package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.connections.service.ConnectionException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Plain HTTPS to the few fixed hosts the providers use. Nothing here ever takes a URL from a user. */
@Component
public class HttpConnectorHttp implements ConnectorHttp {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public JsonNode postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    @Override
    public JsonNode get(String url, String bearerToken) {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + bearerToken).header("Accept", "application/json")
                .header("User-Agent", "Tenemsa").GET().build());
    }

    private JsonNode send(HttpRequest req) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401) throw new ConnectionException(401, "The app no longer accepts the saved sign-in.");
            if (res.statusCode() >= 300) throw new ConnectionException(502, "The app refused the request (" + res.statusCode() + ").");
            return res.body() == null || res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
        } catch (ConnectionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException(502, "The app could not be reached. Please try again.");
        } catch (Exception e) {
            throw new ConnectionException(502, "The app could not be reached. Please try again.");
        }
    }
}
