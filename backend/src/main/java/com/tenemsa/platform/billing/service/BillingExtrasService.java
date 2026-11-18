package com.tenemsa.platform.billing.service;

import com.tenemsa.platform.billing.model.BillingDetails;
import com.tenemsa.platform.billing.model.BillingEvent;
import com.tenemsa.platform.billing.model.TokenTopup;
import com.tenemsa.platform.billing.model.TopupPack;
import com.tenemsa.platform.billing.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** Token top-ups, billing details for receipts, and the AI usage breakdown. */
@Service
public class BillingExtrasService {

    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    @Value("${billing.razorpay.key-secret:}") private String checkoutSecret;

    private final TopupPackRepository packs;
    private final TokenTopupRepository topups;
    private final BillingDetailsRepository details;
    private final BillingEventRepository events;
    private final RazorpayApi razorpay;
    private final AiBudget budget;
    private final JdbcTemplate jdbc;

    public BillingExtrasService(TopupPackRepository packs, TokenTopupRepository topups, BillingDetailsRepository details,
                                BillingEventRepository events, RazorpayApi razorpay, AiBudget budget, JdbcTemplate jdbc) {
        this.packs = packs; this.topups = topups; this.details = details; this.events = events;
        this.razorpay = razorpay; this.budget = budget; this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ top-ups

    public List<Map<String, Object>> packs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TopupPack p : packs.findAllByOrderBySortOrderAsc()) {
            if (!p.isActive()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getCode()); m.put("name", p.getName()); m.put("tokens", p.getTokens()); m.put("pricePaise", p.getPricePaise());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> recentTopups(Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TokenTopup t : topups.findTop10ByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, "PAID")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", t.getCreatedAt().toLocalDate().toString()); m.put("tokens", t.getTokens());
            m.put("amountPaise", t.getAmountPaise()); m.put("period", t.getPeriod()); m.put("paymentId", t.getRazorpayPaymentId());
            out.add(m);
        }
        return out;
    }

    /** Creates a Razorpay order for the pack. Top-ups only make sense when the plan has a token limit. */
    @Transactional
    public Map<String, Object> startTopup(Long companyId, String packCode) {
        if (!razorpay.isConfigured()) throw new BillingException(503, "Online payments are not set up yet.");
        if (budget.budget(companyId) == null) throw new BillingException(400, "Your plan has no AI limit, so there is nothing to top up.");
        TopupPack pack = packs.findById(packCode == null ? "" : packCode.trim().toUpperCase())
                .filter(TopupPack::isActive).orElseThrow(() -> new BillingException(404, "That top-up does not exist."));
        String receipt = "topup-" + companyId + "-" + System.currentTimeMillis();
        String orderId = razorpay.createOrder(pack.getPricePaise(), receipt,
                Map.of("company_id", String.valueOf(companyId), "pack_code", pack.getCode()));
        TokenTopup t = new TokenTopup();
        t.setCompanyId(companyId); t.setPackCode(pack.getCode()); t.setTokens(pack.getTokens());
        t.setAmountPaise(pack.getPricePaise()); t.setPeriod(AiBudget.period());
        t.setRazorpayOrderId(orderId);
        topups.save(t);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keyId", razorpay.keyId()); out.put("orderId", t.getRazorpayOrderId());
        out.put("amountPaise", pack.getPricePaise()); out.put("packName", pack.getName());
        return out;
    }

    /** Razorpay signs "order_id|payment_id". A valid signature proves the payment, so the tokens count immediately. */
    @Transactional
    public void verifyTopup(Long companyId, String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) throw new BillingException(400, "The payment details are incomplete.");
        TokenTopup t = topups.findByRazorpayOrderId(orderId).filter(x -> x.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BillingException(404, "That top-up was not found."));
        if (checkoutSecret == null || checkoutSecret.isBlank()) throw new BillingException(503, "Online payments are not set up yet.");
        if (!BillingService.constantTimeEquals(BillingService.hmacHex(checkoutSecret, orderId + "|" + paymentId), signature)) {
            throw new BillingException(400, "The payment could not be verified.");
        }
        markPaid(t, paymentId);
    }

    /** Backup path: Razorpay's order.paid webhook, in case the browser closed before verifying. */
    @Transactional
    public void markPaidFromWebhook(String orderId, String paymentId) {
        if (orderId == null || orderId.isBlank()) return;
        topups.findByRazorpayOrderId(orderId).ifPresent(t -> markPaid(t, paymentId));
    }

    private void markPaid(TokenTopup t, String paymentId) {
        if ("PAID".equals(t.getStatus())) return;           // verify and webhook may both arrive
        t.setStatus("PAID");
        t.setRazorpayPaymentId(paymentId);
        topups.save(t);
        BillingEvent e = new BillingEvent();
        e.setCompanyId(t.getCompanyId()); e.setRazorpayEventId("topup:" + t.getRazorpayOrderId()); e.setEventType("topup.paid");
        e.setRazorpayPaymentId(paymentId); e.setAmountPaise((long) t.getAmountPaise()); e.setStatus("captured");
        if (!events.existsByRazorpayEventId(e.getRazorpayEventId())) events.save(e);
    }

    // ------------------------------------------------------------------ billing details

    public Map<String, Object> getDetails(Long companyId) {
        BillingDetails d = details.findById(companyId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("legalName", d == null ? "" : nz(d.getLegalName())); m.put("gstin", d == null ? "" : nz(d.getGstin()));
        m.put("address", d == null ? "" : nz(d.getAddress())); m.put("city", d == null ? "" : nz(d.getCity()));
        m.put("state", d == null ? "" : nz(d.getState())); m.put("pincode", d == null ? "" : nz(d.getPincode()));
        m.put("billingEmail", d == null ? "" : nz(d.getBillingEmail()));
        return m;
    }

    @Transactional
    public void saveDetails(Long companyId, Map<String, String> in) {
        String gstin = clean(in.get("gstin"), 15).toUpperCase();
        if (!gstin.isEmpty() && !GSTIN.matcher(gstin).matches()) throw new BillingException(400, "That GSTIN does not look right. It has 15 letters and digits, for example 27ABCDE1234F1Z5.");
        String email = clean(in.get("billingEmail"), 200);
        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new BillingException(400, "That billing email does not look right.");
        String pin = clean(in.get("pincode"), 12);
        if (!pin.isEmpty() && !pin.matches("^[0-9A-Za-z \\-]{3,12}$")) throw new BillingException(400, "That pincode does not look right.");
        BillingDetails d = details.findById(companyId).orElseGet(() -> { BillingDetails n = new BillingDetails(); n.setCompanyId(companyId); return n; });
        d.setLegalName(clean(in.get("legalName"), 200)); d.setGstin(gstin); d.setAddress(clean(in.get("address"), 300));
        d.setCity(clean(in.get("city"), 100)); d.setState(clean(in.get("state"), 100)); d.setPincode(pin); d.setBillingEmail(email);
        details.save(d);
    }

    private static String clean(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() > max) throw new BillingException(400, "One of the billing details is too long.");
        return t;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // ------------------------------------------------------------------ usage breakdown

    /** This month's AI use by feature, by person and by day. Counts input and output tokens, as the budget does. */
    public Map<String, Object> usage(Long companyId) {
        LocalDateTime since = AiBudget.monthStart();
        String tokens = "SUM(COALESCE(l.input_tokens,0) + COALESCE(l.output_tokens,0))";
        String where = "WHERE l.company_id = ? AND l.created_at >= ? AND l.status <> 'RATE_LIMITED'";
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", AiBudget.period());
        out.put("tokensUsed", budget.tokensUsedThisMonth(companyId));
        out.put("budget", budget.budget(companyId));
        out.put("topupTokens", budget.topupTokens(companyId));
        out.put("resetsOn", AiBudget.nextReset().toString());
        out.put("byFeature", lower(jdbc.queryForList("SELECT l.feature AS name, COUNT(*) AS requests, " + tokens + " AS tokens FROM ai_request_logs l "
                + where + " GROUP BY l.feature ORDER BY tokens DESC", companyId, since)));
        out.put("byPerson", lower(jdbc.queryForList("SELECT COALESCE(u.name, 'Unknown') AS name, COUNT(*) AS requests, " + tokens + " AS tokens FROM ai_request_logs l "
                + "LEFT JOIN users u ON u.company_id = l.company_id AND u.emp_id = l.emp_id " + where
                + " GROUP BY COALESCE(u.name, 'Unknown') ORDER BY tokens DESC LIMIT 15", companyId, since)));
        out.put("byDay", lower(jdbc.queryForList("SELECT CAST(l.created_at AS DATE) AS usage_day, COUNT(*) AS requests, " + tokens + " AS tokens FROM ai_request_logs l "
                + where + " GROUP BY CAST(l.created_at AS DATE) ORDER BY usage_day", companyId, since)));
        return out;
    }

    /** Column labels come back in different cases on different databases; the screens expect lower case. */
    static List<Map<String, Object>> lower(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            r.forEach((k, v) -> m.put(k.toLowerCase(), v));
            out.add(m);
        }
        return out;
    }
}
