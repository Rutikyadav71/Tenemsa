package com.tenemsa.platform.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Plain HTTPS calls to Razorpay's REST API with the key id and secret. No SDK, no card data ever touches us. */
@Service
public class HttpRazorpayApi implements RazorpayApi {

    private static final String BASE = "https://api.razorpay.com/v1";

    @Value("${billing.razorpay.key-id:}") private String keyId;
    @Value("${billing.razorpay.key-secret:}") private String keySecret;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override public boolean isConfigured() { return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank(); }
    @Override public String keyId() { return keyId; }

    @Override
    public Map<String, Object> createSubscription(String razorpayPlanId, Map<String, String> notes, Long startAtEpochSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan_id", razorpayPlanId);
        body.put("total_count", 120);          // up to 10 years of monthly charges; the customer can cancel any time
        body.put("quantity", 1);
        body.put("customer_notify", 1);
        body.put("notes", notes);
        if (startAtEpochSeconds != null) body.put("start_at", startAtEpochSeconds);
        JsonNode n = post("/subscriptions", body);
        return Map.of("id", n.path("id").asText(), "status", n.path("status").asText("created"));
    }

    @Override
    public Map<String, Object> fetchSubscription(String id) {
        JsonNode n = get("/subscriptions/" + id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", n.path("status").asText(""));
        m.put("plan_id", n.path("plan_id").asText(""));
        m.put("current_start", n.path("current_start").isNumber() ? n.path("current_start").asLong() : null);
        m.put("current_end", n.path("current_end").isNumber() ? n.path("current_end").asLong() : null);
        m.put("paid_count", n.path("paid_count").asInt(0));
        return m;
    }

    @Override
    public void updateSubscription(String id, String newPlanId, boolean atCycleEnd) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan_id", newPlanId);
        body.put("schedule_change_at", atCycleEnd ? "cycle_end" : "now");
        call("PATCH", "/subscriptions/" + id, body);
    }

    @Override
    public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", notes);
        return post("/orders", body).path("id").asText();
    }

    @Override
    public void cancelSubscription(String id, boolean atCycleEnd) {
        post("/subscriptions/" + id + "/cancel", Map.of("cancel_at_cycle_end", atCycleEnd ? 1 : 0));
    }

    @Override
    public String createPlan(String name, String description, long amountPaise) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("amount", amountPaise);
        item.put("currency", "INR");
        if (description != null && !description.isBlank()) item.put("description", description);
        JsonNode n = post("/plans", Map.of("period", "monthly", "interval", 1, "item", item));
        return n.path("id").asText();
    }

    private JsonNode get(String path) {
        if (!isConfigured()) throw new BillingException(503, "Online payments are not set up yet.");
        try {
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode n = json.readTree(res.body());
            if (res.statusCode() >= 300) throw new BillingException(502, "Razorpay: " + n.path("error").path("description").asText("Razorpay refused the request."));
            return n;
        } catch (BillingException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BillingException(502, "The payment service could not be reached. Please try again.");
        } catch (Exception e) {
            throw new BillingException(502, "The payment service could not be reached. Please try again.");
        }
    }

    private JsonNode post(String path, Object body) { return call("POST", path, body); }

    private JsonNode call(String method, String path, Object body) {
        if (!isConfigured()) throw new BillingException(503, "Online payments are not set up yet.");
        try {
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode n = json.readTree(res.body());
            if (res.statusCode() >= 300) {
                String why = n.path("error").path("description").asText("Razorpay refused the request.");
                throw new BillingException(502, "Razorpay: " + why);
            }
            return n;
        } catch (BillingException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BillingException(502, "The payment service could not be reached. Please try again.");
        } catch (Exception e) {
            throw new BillingException(502, "The payment service could not be reached. Please try again.");
        }
    }
}
