package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.billing.model.Plan;
import com.tenemsa.platform.billing.repository.PlanRepository;
import com.tenemsa.platform.billing.service.AiBudget;
import com.tenemsa.platform.billing.service.BillingService;
import com.tenemsa.platform.billing.service.RazorpayApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Plans, the monthly AI budget, checkout verification and the Razorpay webhook. Razorpay itself is replaced by a stub. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "billing.razorpay.key-secret=test_key_secret",
        "billing.razorpay.webhook-secret=test_webhook_secret"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanRepository plans;
    @Autowired AiBudget aiBudget;
    @Autowired BillingService billing;
    @Autowired com.tenemsa.platform.company.service.CompanyLimits limits;
    @MockitoBean RazorpayApi razorpay;

    @BeforeEach
    void seedPlans() {
        if (plans.count() == 0) {
            plans.save(plan("FREE", "Free", 0, 10, 1000L, false, 1));
            plans.save(plan("STARTER", "Starter", 49900, 50, 5000L, false, 2));
            plans.save(plan("ENTERPRISE", "Enterprise", 0, null, null, true, 3));
        }
        when(razorpay.isConfigured()).thenReturn(true);
        when(razorpay.keyId()).thenReturn("rzp_test_key");
        when(razorpay.createPlan(anyString(), any(), anyLong())).thenReturn("plan_test123");
        when(razorpay.createSubscription(anyString(), any(), any())).thenReturn(Map.of("id", "sub_" + UUID.randomUUID().toString().substring(0, 8), "status", "created"));
    }

    private static Plan plan(String code, String name, int price, Integer users, Long tokens, boolean contact, int order) {
        Plan p = new Plan();
        p.setCode(code); p.setName(name); p.setPricePaise(price); p.setMaxUsers(users);
        p.setMonthlyAiTokens(tokens); p.setContactOnly(contact); p.setSortOrder(order); p.setActive(true);
        return p;
    }

    private MockHttpServletResponse postJson(String url, String token, Object body) throws Exception {
        var rb = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) rb.header("Authorization", "Bearer " + token);
        return mvc.perform(rb).andReturn().getResponse();
    }

    private record Co(String token, long id) {}

    private Co company() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse reg = postJson("/api/companies/register", null, Map.of("companyName", "Bill " + id,
                "ownerName", "Owner", "email", "owner" + id + "@example.com", "password", PASSWORD));
        assertThat(reg.getStatus()).isEqualTo(201);
        JsonNode r = json.readTree(reg.getContentAsString());
        Long cid = jdbc.queryForObject("SELECT id FROM companies WHERE slug = ?", Long.class, r.get("companySlug").asText());
        return new Co(r.get("token").asText(), cid);
    }

    private JsonNode overview(Co c) throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/api/billing/overview").header("Authorization", "Bearer " + c.token())).andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString());
    }

    private void logTokens(long companyId, int in, int out, String status) {
        jdbc.update("INSERT INTO ai_request_logs (company_id, feature, provider, model, status, latency_ms, input_tokens, output_tokens, created_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                companyId, "CHAT", "test", "test", status, 1, in, out);
    }

    @Test
    void overviewShowsPlanUsageAndThePlanList() throws Exception {
        Co c = company();
        JsonNode o = overview(c);
        assertThat(o.get("currentPlan").asText()).isEqualTo("FREE");
        assertThat(o.get("usage").get("aiTokenBudget").asLong()).isEqualTo(1000);
        assertThat(o.get("plans").size()).isGreaterThanOrEqualTo(3);
        assertThat(o.get("paymentsEnabled").asBoolean()).isTrue();
    }

    @Test
    void monthlyBudgetCountsInputAndOutputTokensAndIgnoresRefusedCalls() throws Exception {
        Co c = company();
        assertThat(aiBudget.exceededMessage(c.id())).isNull();
        logTokens(c.id(), 300, 200, "SUCCESS");
        logTokens(c.id(), 9999, 9999, "RATE_LIMITED");
        assertThat(aiBudget.tokensUsedThisMonth(c.id())).isEqualTo(500);
        assertThat(aiBudget.exceededMessage(c.id())).isNull();
        logTokens(c.id(), 400, 200, "SUCCESS");
        assertThat(aiBudget.exceededMessage(c.id())).contains("monthly AI allowance");
    }

    @Test
    void aCompanyOverrideBeatsThePlanAndNullMeansUnlimited() throws Exception {
        Co c = company();
        assertThat(limits.of(c.id()).monthlyTokens()).isEqualTo(1000L);
        jdbc.update("UPDATE companies SET ai_monthly_tokens = 50 WHERE id = ?", c.id());
        assertThat(limits.of(c.id()).monthlyTokens()).isEqualTo(50L);
        jdbc.update("UPDATE companies SET ai_monthly_tokens = NULL, plan = 'ENTERPRISE' WHERE id = ?", c.id());
        assertThat(limits.of(c.id()).monthlyTokens()).isNull();
        assertThat(limits.of(c.id()).maxUsers()).isNull();
    }

    @Test
    void checkoutVerifyActivatesThePlanOnlyWithAValidSignature() throws Exception {
        Co c = company();
        MockHttpServletResponse res = postJson("/api/billing/checkout", c.token(), Map.of("plan", "STARTER"));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        JsonNode co = json.readTree(res.getContentAsString());
        String subId = co.get("subscriptionId").asText();
        assertThat(co.get("keyId").asText()).isEqualTo("rzp_test_key");

        MockHttpServletResponse bad = postJson("/api/billing/verify", c.token(), Map.of("paymentId", "pay_1", "subscriptionId", subId, "signature", "nope"));
        assertThat(bad.getStatus()).isEqualTo(400);
        assertThat(overview(c).get("currentPlan").asText()).isEqualTo("FREE");

        String sig = BillingService.hmacHex("test_key_secret", "pay_1|" + subId);
        MockHttpServletResponse ok = postJson("/api/billing/verify", c.token(), Map.of("paymentId", "pay_1", "subscriptionId", subId, "signature", sig));
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        JsonNode o = overview(c);
        assertThat(o.get("currentPlan").asText()).isEqualTo("STARTER");
        assertThat(o.get("subscription").get("status").asText()).isEqualTo("ACTIVE");

        // a second checkout is refused while a subscription is live
        assertThat(postJson("/api/billing/checkout", c.token(), Map.of("plan", "STARTER")).getStatus()).isEqualTo(409);
    }

    @Test
    void aCompanyCannotVerifyAnotherCompaniesSubscription() throws Exception {
        Co a = company();
        Co b = company();
        String subId = json.readTree(postJson("/api/billing/checkout", a.token(), Map.of("plan", "STARTER")).getContentAsString()).get("subscriptionId").asText();
        String sig = BillingService.hmacHex("test_key_secret", "pay_2|" + subId);
        assertThat(postJson("/api/billing/verify", b.token(), Map.of("paymentId", "pay_2", "subscriptionId", subId, "signature", sig)).getStatus()).isEqualTo(404);
    }

    @Test
    void contactOnlyAndFreePlansCannotBeBought() throws Exception {
        Co c = company();
        assertThat(postJson("/api/billing/checkout", c.token(), Map.of("plan", "ENTERPRISE")).getStatus()).isEqualTo(400);
        assertThat(postJson("/api/billing/checkout", c.token(), Map.of("plan", "FREE")).getStatus()).isEqualTo(400);
        assertThat(postJson("/api/billing/checkout", c.token(), Map.of("plan", "NOPE")).getStatus()).isEqualTo(404);
    }

    @Test
    void checkoutSaysSoWhenPaymentsAreNotSetUp() throws Exception {
        when(razorpay.isConfigured()).thenReturn(false);
        Co c = company();
        MockHttpServletResponse res = postJson("/api/billing/checkout", c.token(), Map.of("plan", "STARTER"));
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(overview(c).get("paymentsEnabled").asBoolean()).isFalse();
    }

    @Test
    void cancelWithoutASubscriptionIsAClearError() throws Exception {
        Co c = company();
        assertThat(postJson("/api/billing/cancel", c.token(), Map.of()).getStatus()).isEqualTo(404);
    }

    private MockHttpServletResponse webhook(String body, String signature, String eventId) throws Exception {
        var rb = post("/api/billing/webhook/razorpay").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) rb.header("X-Razorpay-Signature", signature);
        if (eventId != null) rb.header("X-Razorpay-Event-Id", eventId);
        return mvc.perform(rb).andReturn().getResponse();
    }

    private String chargedBody(String subId) {
        return "{\"event\":\"subscription.charged\",\"payload\":{\"subscription\":{\"entity\":{\"id\":\"" + subId
                + "\",\"status\":\"active\",\"paid_count\":2,\"current_start\":1790000000,\"current_end\":1792600000}},"
                + "\"payment\":{\"entity\":{\"id\":\"pay_w1\",\"amount\":49900,\"status\":\"captured\"}}}}";
    }

    @Test
    void webhookRejectsABadSignatureAndAcceptsAGoodOneOnlyOnce() throws Exception {
        Co c = company();
        String subId = json.readTree(postJson("/api/billing/checkout", c.token(), Map.of("plan", "STARTER")).getContentAsString()).get("subscriptionId").asText();
        String body = chargedBody(subId);

        assertThat(webhook(body, "wrong", "evt_1").getStatus()).isEqualTo(400);
        assertThat(webhook(body, null, "evt_1").getStatus()).isEqualTo(400);
        assertThat(overview(c).get("currentPlan").asText()).isEqualTo("FREE");

        String sig = BillingService.hmacHex("test_webhook_secret", body);
        assertThat(webhook(body, sig, "evt_" + subId).getStatus()).isEqualTo(200);
        assertThat(overview(c).get("currentPlan").asText()).isEqualTo("STARTER");
        assertThat(overview(c).get("payments")).hasSize(1);

        // Razorpay may deliver the same event again: no second payment row
        assertThat(webhook(body, sig, "evt_" + subId).getStatus()).isEqualTo(200);
        assertThat(overview(c).get("payments")).hasSize(1);
    }

    @Test
    void reconcileCorrectsASubscriptionWhoseWebhookWasMissed() throws Exception {
        Co c = company();
        String subId = json.readTree(postJson("/api/billing/checkout", c.token(), Map.of("plan", "STARTER")).getContentAsString()).get("subscriptionId").asText();
        String sig = BillingService.hmacHex("test_key_secret", "pay_r|" + subId);
        assertThat(postJson("/api/billing/verify", c.token(), Map.of("paymentId", "pay_r", "subscriptionId", subId, "signature", sig)).getStatus()).isEqualTo(200);

        // Razorpay says the payments have been failing, and we never got the webhook
        when(razorpay.fetchSubscription(subId)).thenReturn(Map.of("status", "halted", "current_end", 1700000000L, "paid_count", 1));
        assertThat(billing.reconcile()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE razorpay_subscription_id = ?", String.class, subId)).isEqualTo("HALTED");

        // a lookup that fails changes nothing
        when(razorpay.fetchSubscription(subId)).thenThrow(new com.tenemsa.platform.billing.service.BillingException(502, "down"));
        billing.reconcile();
        assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE razorpay_subscription_id = ?", String.class, subId)).isEqualTo("HALTED");
    }

    @Test
    void aHaltedSubscriptionDropsToFreeAfterTheGraceDays() throws Exception {
        Co c = company();
        String subId = json.readTree(postJson("/api/billing/checkout", c.token(), Map.of("plan", "STARTER")).getContentAsString()).get("subscriptionId").asText();
        String sig = BillingService.hmacHex("test_key_secret", "pay_3|" + subId);
        assertThat(postJson("/api/billing/verify", c.token(), Map.of("paymentId", "pay_3", "subscriptionId", subId, "signature", sig)).getStatus()).isEqualTo(200);

        String body = "{\"event\":\"subscription.halted\",\"payload\":{\"subscription\":{\"entity\":{\"id\":\"" + subId + "\",\"status\":\"halted\"}}}}";
        assertThat(webhook(body, BillingService.hmacHex("test_webhook_secret", body), "evt_h_" + subId).getStatus()).isEqualTo(200);
        jdbc.update("UPDATE subscriptions SET current_end = DATEADD('DAY', -10, CURRENT_TIMESTAMP) WHERE razorpay_subscription_id = ?", subId);

        assertThat(billing.downgradeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(overview(c).get("currentPlan").asText()).isEqualTo("FREE");
    }

    // ------------------------------------------------------------------ stage 2

    private void ensurePro() {
        if (!plans.existsById("PRO")) {
            Plan p = plan("PRO", "Pro", 99900, 200, 20000L, false, 4);
            p.setTrialDays(14);
            plans.save(p);
        }
        jdbc.update("UPDATE plans SET razorpay_plan_id = 'plan_starter_rz' WHERE code = 'STARTER'");
        jdbc.update("UPDATE plans SET razorpay_plan_id = 'plan_pro_rz' WHERE code = 'PRO'");
    }

    private String subscribe(Co c, String plan) throws Exception {
        MockHttpServletResponse res = postJson("/api/billing/checkout", c.token(), Map.of("plan", plan));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        JsonNode co = json.readTree(res.getContentAsString());
        String subId = co.get("subscriptionId").asText();
        String sig = BillingService.hmacHex("test_key_secret", "pay_" + subId + "|" + subId);
        assertThat(postJson("/api/billing/verify", c.token(), Map.of("paymentId", "pay_" + subId, "subscriptionId", subId, "signature", sig)).getStatus()).isEqualTo(200);
        return subId;
    }

    @Test
    void theFirstSubscriptionGetsAFreeTrialAndIsNotChargedYet() throws Exception {
        ensurePro();
        Co c = company();
        assertThat(overview(c).get("trialEligible").asBoolean()).isTrue();
        MockHttpServletResponse res = postJson("/api/billing/checkout", c.token(), Map.of("plan", "PRO"));
        JsonNode co = json.readTree(res.getContentAsString());
        assertThat(co.get("trialDays").asInt()).isEqualTo(14);
        ArgumentCaptor<Long> startAt = ArgumentCaptor.forClass(Long.class);
        verify(razorpay).createSubscription(eq("plan_pro_rz"), any(), startAt.capture());
        assertThat(startAt.getValue()).isNotNull().isGreaterThan(System.currentTimeMillis() / 1000 + 13 * 86400);

        String subId = co.get("subscriptionId").asText();
        String sig = BillingService.hmacHex("test_key_secret", "pay_t|" + subId);
        assertThat(postJson("/api/billing/verify", c.token(), Map.of("paymentId", "pay_t", "subscriptionId", subId, "signature", sig)).getStatus()).isEqualTo(200);
        JsonNode o = overview(c);
        assertThat(o.get("currentPlan").asText()).isEqualTo("PRO");           // plan works during the trial
        assertThat(o.get("subscription").get("trial").asBoolean()).isTrue();
        assertThat(o.get("subscription").get("status").asText()).isEqualTo("AUTHENTICATED");
        assertThat(o.get("trialEligible").asBoolean()).isFalse();

        // the first real charge ends the trial
        String body = chargedBody(subId);
        assertThat(webhook(body, BillingService.hmacHex("test_webhook_secret", body), "evt_t_" + subId).getStatus()).isEqualTo(200);
        assertThat(overview(c).get("subscription").get("trial").asBoolean()).isFalse();
    }

    @Test
    void aSecondSubscriptionAfterAnEarlierOneGetsNoTrial() throws Exception {
        ensurePro();
        Co c = company();
        String first = subscribe(c, "PRO");
        postJson("/api/billing/cancel", c.token(), Map.of());
        jdbc.update("UPDATE subscriptions SET status = 'EXPIRED' WHERE razorpay_subscription_id = ?", first);
        postJson("/api/billing/checkout", c.token(), Map.of("plan", "PRO"));
        ArgumentCaptor<Long> startAt = ArgumentCaptor.forClass(Long.class);
        verify(razorpay, org.mockito.Mockito.times(2)).createSubscription(eq("plan_pro_rz"), any(), startAt.capture());
        assertThat(startAt.getAllValues().get(1)).isNull();
    }

    @Test
    void aPlanChangeWaitsForTheRenewalAndThenSwitchesThePlan() throws Exception {
        ensurePro();
        Co c = company();
        String subId = subscribe(c, "STARTER");
        MockHttpServletResponse res = postJson("/api/billing/plan-change", c.token(), Map.of("plan", "PRO"));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        verify(razorpay).updateSubscription(subId, "plan_pro_rz", true);
        JsonNode o = json.readTree(res.getContentAsString());
        assertThat(o.get("currentPlan").asText()).isEqualTo("STARTER");        // nothing changes yet
        assertThat(o.get("subscription").get("pendingPlanCode").asText()).isEqualTo("PRO");

        // same plan, free plan and unknown plan are refused
        assertThat(postJson("/api/billing/plan-change", c.token(), Map.of("plan", "STARTER")).getStatus()).isEqualTo(400);
        assertThat(postJson("/api/billing/plan-change", c.token(), Map.of("plan", "FREE")).getStatus()).isEqualTo(400);
        assertThat(postJson("/api/billing/plan-change", c.token(), Map.of("plan", "NOPE")).getStatus()).isEqualTo(404);

        // the renewal arrives and Razorpay now bills the PRO plan
        String body = "{\"event\":\"subscription.charged\",\"payload\":{\"subscription\":{\"entity\":{\"id\":\"" + subId
                + "\",\"status\":\"active\",\"plan_id\":\"plan_pro_rz\",\"paid_count\":2}},\"payment\":{\"entity\":{\"id\":\"pay_up\",\"amount\":99900,\"status\":\"captured\"}}}}";
        assertThat(webhook(body, BillingService.hmacHex("test_webhook_secret", body), "evt_up_" + subId).getStatus()).isEqualTo(200);
        o = overview(c);
        assertThat(o.get("currentPlan").asText()).isEqualTo("PRO");
        assertThat(o.get("subscription").get("pendingPlanCode").isNull()).isTrue();
    }

    @Test
    void aRenewalOnTheOldPlanDoesNotApplyTheScheduledChange() throws Exception {
        ensurePro();
        Co c = company();
        String subId = subscribe(c, "STARTER");
        postJson("/api/billing/plan-change", c.token(), Map.of("plan", "PRO"));
        String body = "{\"event\":\"subscription.charged\",\"payload\":{\"subscription\":{\"entity\":{\"id\":\"" + subId
                + "\",\"status\":\"active\",\"plan_id\":\"plan_starter_rz\"}},\"payment\":{\"entity\":{\"id\":\"pay_old\",\"amount\":49900}}}}";
        webhook(body, BillingService.hmacHex("test_webhook_secret", body), "evt_old_" + subId);
        assertThat(overview(c).get("currentPlan").asText()).isEqualTo("STARTER");
        assertThat(overview(c).get("subscription").get("pendingPlanCode").asText()).isEqualTo("PRO");
    }

    @Test
    void planChangeNeedsAnActiveSubscription() throws Exception {
        ensurePro();
        Co c = company();
        assertThat(postJson("/api/billing/plan-change", c.token(), Map.of("plan", "PRO")).getStatus()).isEqualTo(404);
    }

    @Test
    void aVerifiedTopUpRaisesTheBudgetOnceAndOnlyWithAValidSignature() throws Exception {
        Co c = company();
        jdbc.update("INSERT INTO topup_packs (code, name, tokens, price_paise, sort_order, active) SELECT 'TEST_PACK','Test pack',2000,9900,9,TRUE WHERE NOT EXISTS (SELECT 1 FROM topup_packs WHERE code='TEST_PACK')");
        when(razorpay.createOrder(anyLong(), anyString(), any())).thenReturn("order_" + UUID.randomUUID().toString().substring(0, 8));
        MockHttpServletResponse res = postJson("/api/billing/topup/checkout", c.token(), Map.of("pack", "TEST_PACK"));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        String orderId = json.readTree(res.getContentAsString()).get("orderId").asText();
        assertThat(aiBudget.budget(c.id())).isEqualTo(1000L);

        assertThat(postJson("/api/billing/topup/verify", c.token(), Map.of("orderId", orderId, "paymentId", "pay_x", "signature", "bad")).getStatus()).isEqualTo(400);
        assertThat(aiBudget.budget(c.id())).isEqualTo(1000L);

        String sig = BillingService.hmacHex("test_key_secret", orderId + "|pay_x");
        assertThat(postJson("/api/billing/topup/verify", c.token(), Map.of("orderId", orderId, "paymentId", "pay_x", "signature", sig)).getStatus()).isEqualTo(200);
        assertThat(postJson("/api/billing/topup/verify", c.token(), Map.of("orderId", orderId, "paymentId", "pay_x", "signature", sig)).getStatus()).isEqualTo(200);
        assertThat(aiBudget.budget(c.id())).isEqualTo(3000L);                    // counted once
        assertThat(overview(c).get("usage").get("aiTokenBudget").asLong()).isEqualTo(3000L);

        // the top-up lets a company that had used up its allowance carry on
        logTokens(c.id(), 1500, 0, "SUCCESS");
        assertThat(aiBudget.exceededMessage(c.id())).isNull();
        logTokens(c.id(), 1600, 0, "SUCCESS");
        assertThat(aiBudget.exceededMessage(c.id())).isNotNull();

        // another company cannot use this order
        Co other = company();
        assertThat(postJson("/api/billing/topup/verify", other.token(), Map.of("orderId", orderId, "paymentId", "pay_x", "signature", sig)).getStatus()).isEqualTo(404);
    }

    @Test
    void aTopUpIsRefusedWhenThePlanHasNoLimit() throws Exception {
        Co c = company();
        jdbc.update("UPDATE companies SET plan = 'ENTERPRISE' WHERE id = ?", c.id());
        assertThat(postJson("/api/billing/topup/checkout", c.token(), Map.of("pack", "SMALL")).getStatus()).isEqualTo(400);
    }

    @Test
    void billingDetailsAreValidatedAndSaved() throws Exception {
        Co c = company();
        assertThat(putJson("/api/billing/details", c.token(), Map.of("legalName", "Acme Pvt Ltd", "gstin", "BADGST")).getStatus()).isEqualTo(400);
        MockHttpServletResponse ok = putJson("/api/billing/details", c.token(), Map.of("legalName", "Acme Pvt Ltd", "gstin", "27abcde1234f1z5",
                "city", "Pune", "billingEmail", "accounts@acme.test"));
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        MockHttpServletResponse got = mvc.perform(get("/api/billing/details").header("Authorization", "Bearer " + c.token())).andReturn().getResponse();
        JsonNode d = json.readTree(got.getContentAsString());
        assertThat(d.get("gstin").asText()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(d.get("legalName").asText()).isEqualTo("Acme Pvt Ltd");
        assertThat(putJson("/api/billing/details", c.token(), Map.of("billingEmail", "not-an-email")).getStatus()).isEqualTo(400);
    }

    @Test
    void usageBreaksDownByFeatureAndDay() throws Exception {
        Co c = company();
        logTokens(c.id(), 100, 50, "SUCCESS");
        logTokens(c.id(), 10, 10, "SUCCESS");
        MockHttpServletResponse res = mvc.perform(get("/api/billing/usage").header("Authorization", "Bearer " + c.token())).andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        JsonNode u = json.readTree(res.getContentAsString());
        assertThat(u.get("tokensUsed").asLong()).isEqualTo(170);
        assertThat(u.get("byFeature").get(0).get("name").asText()).isEqualTo("CHAT");
        assertThat(u.get("byFeature").get(0).get("tokens").asLong()).isEqualTo(170);
        assertThat(u.get("byDay")).hasSize(1);
    }

    @Autowired com.tenemsa.platform.billing.service.BillingNotifier notifier;

    @Test
    void allowanceAlertsAreRecordedOncePerMonthPerThreshold() throws Exception {
        Co c = company();
        logTokens(c.id(), 850, 0, "SUCCESS");                       // 85% of 1000
        notifier.checkAlerts();
        notifier.checkAlerts();
        String period = AiBudget.period();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_events WHERE razorpay_event_id = ?", Integer.class, "alert:80:" + c.id() + ":" + period)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_events WHERE razorpay_event_id = ?", Integer.class, "alert:100:" + c.id() + ":" + period)).isEqualTo(0);
        logTokens(c.id(), 200, 0, "SUCCESS");
        notifier.checkAlerts();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_events WHERE razorpay_event_id = ?", Integer.class, "alert:100:" + c.id() + ":" + period)).isEqualTo(1);
    }

    private MockHttpServletResponse putJson(String url, String token, Object body) throws Exception {
        var rb = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        rb.header("Authorization", "Bearer " + token);
        return mvc.perform(rb).andReturn().getResponse();
    }
}
