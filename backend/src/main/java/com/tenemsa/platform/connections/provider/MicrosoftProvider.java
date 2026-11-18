package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.tenemsa.platform.connections.service.ConnectionException;
import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared Microsoft 365 sign-in (Microsoft identity platform v2, authorization code with PKCE) and Microsoft Graph.
 * Outlook calendar and OneDrive are separate apps so a person can allow one without the other. Works with work,
 * school and personal accounts when the tenant is "common" (the default); set MICROSOFT_TENANT to limit it.
 */
public abstract class MicrosoftProvider implements ConnectorProvider {

    @Value("${connections.microsoft.client-id:}") private String clientId;
    @Value("${connections.microsoft.client-secret:}") private String clientSecret;
    @Value("${connections.microsoft.tenant:common}") private String tenant;
    protected final ConnectorHttp http;

    protected MicrosoftProvider(ConnectorHttp http) { this.http = http; }

    /** The one Graph permission this app asks for (plus sign-in, profile and a refresh token). */
    protected abstract String scope();

    @Override public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank(); }

    private String base() { return "https://login.microsoftonline.com/" + enc(tenant.isBlank() ? "common" : tenant.trim()) + "/oauth2/v2.0"; }
    private String scopes() { return "offline_access User.Read " + scope(); }

    @Override
    public String authorizeUrl(String redirectUri, String state, String pkceChallenge) {
        return base() + "/authorize?client_id=" + enc(clientId) + "&response_type=code&response_mode=query&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(scopes()) + "&code_challenge=" + enc(pkceChallenge) + "&code_challenge_method=S256&state=" + enc(state);
    }

    @Override
    public Tokens exchange(String code, String redirectUri, String pkceVerifier) {
        JsonNode n = http.postForm(base() + "/token", Map.of("client_id", clientId, "client_secret", clientSecret, "code", code,
                "redirect_uri", redirectUri, "grant_type", "authorization_code", "code_verifier", pkceVerifier, "scope", scopes()));
        return parse(n, null);
    }

    @Override
    public Tokens refresh(String refreshToken) {
        JsonNode n = http.postForm(base() + "/token", Map.of("client_id", clientId, "client_secret", clientSecret,
                "refresh_token", refreshToken, "grant_type", "refresh_token", "scope", scopes()));
        return parse(n, refreshToken);
    }

    private Tokens parse(JsonNode n, String keepRefresh) {
        String access = n.path("access_token").asText("");
        if (access.isBlank()) throw new ConnectionException(400, "Microsoft did not accept the sign-in. Please try again.");
        // Microsoft rotates refresh tokens: always keep the newest one it sends.
        String refresh = n.path("refresh_token").asText(keepRefresh == null ? "" : keepRefresh);
        return new Tokens(access, refresh.isBlank() ? null : refresh, n.path("expires_in").asLong(3600), n.path("scope").asText(""));
    }

    @Override
    public String accountLabel(String token) {
        JsonNode me = http.get("https://graph.microsoft.com/v1.0/me?$select=userPrincipalName,mail", token);
        String mail = me.path("mail").asText("");
        return mail.isBlank() ? me.path("userPrincipalName").asText("Microsoft account") : mail;
    }

    protected static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
