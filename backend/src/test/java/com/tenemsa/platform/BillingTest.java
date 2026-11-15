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
        when(razorpay.createSubscription(anyString(), any())).thenReturn(Map.of("id", "sub_" + UUID.randomUUID().toString().substring(0, 8), "status", "created"));
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
        assertThat(o.get("plans")).hasSize(3);
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
}
