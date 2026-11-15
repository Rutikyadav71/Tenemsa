package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.tenemsa.platform.connections.service.ConnectionException;
import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Shared Google sign-in. Calendar and Drive are separate apps so a person can allow one without the other. */
public abstract class GoogleProvider implements ConnectorProvider {

    @Value("${connections.google.client-id:}") private String clientId;
    @Value("${connections.google.client-secret:}") private String clientSecret;
    protected final ConnectorHttp http;

    protected GoogleProvider(ConnectorHttp http) { this.http = http; }

    /** The one Google permission this app asks for, on top of the person's email. */
    protected abstract String scope();

    @Override public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank(); }

    @Override
    public String authorizeUrl(String redirectUri, String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri) + "&scope=" + enc("openid email " + scope())
                + "&access_type=offline&prompt=consent&state=" + enc(state);
    }

    @Override
    public Tokens exchange(String code, String redirectUri) {
        JsonNode n = http.postForm("https://oauth2.googleapis.com/token", Map.of("code", code, "client_id", clientId,
                "client_secret", clientSecret, "redirect_uri", redirectUri, "grant_type", "authorization_code"));
        return parse(n, null);
    }

    @Override
    public Tokens refresh(String refreshToken) {
        JsonNode n = http.postForm("https://oauth2.googleapis.com/token", Map.of("refresh_token", refreshToken, "client_id", clientId,
                "client_secret", clientSecret, "grant_type", "refresh_token"));
        return parse(n, refreshToken);
    }

    private Tokens parse(JsonNode n, String keepRefresh) {
        String access = n.path("access_token").asText("");
        if (access.isBlank()) throw new ConnectionException(400, "Google did not accept the sign-in. Please try again.");
        String refresh = n.path("refresh_token").asText(keepRefresh == null ? "" : keepRefresh);
        return new Tokens(access, refresh.isBlank() ? null : refresh, n.path("expires_in").asLong(3600), n.path("scope").asText(""));
    }

    @Override
    public String accountLabel(String token) { return http.get("https://www.googleapis.com/oauth2/v3/userinfo", token).path("email").asText("Google account"); }

    @Override
    public void revoke(String token) {
        try { http.postForm("https://oauth2.googleapis.com/revoke", Map.of("token", token)); } catch (RuntimeException ignored) { /* best effort */ }
    }

    protected static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
