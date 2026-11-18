package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GitHub: the person's repositories and the issues assigned to them. Read only. */
@Component
public class GithubProvider implements ConnectorProvider {

    @Value("${connections.github.client-id:}") private String clientId;
    @Value("${connections.github.client-secret:}") private String clientSecret;
    private final ConnectorHttp http;

    public GithubProvider(ConnectorHttp http) { this.http = http; }

    @Override public String code() { return "github"; }
    @Override public String label() { return "GitHub"; }
    @Override public String description() { return "Your repositories and the issues assigned to you."; }
    @Override public String access() { return "Read your profile, repositories and issues. Tenemsa never writes to GitHub."; }
    @Override public boolean configured() { return !clientId.isBlank() && !clientSecret.isBlank(); }

    @Override
    public String authorizeUrl(String redirectUri, String state, String pkceChallenge) {
        return "https://github.com/login/oauth/authorize?client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc("read:user repo") + "&state=" + enc(state);
    }

    @Override
    public Tokens exchange(String code, String redirectUri, String pkceVerifier) {
        JsonNode n = http.postForm("https://github.com/login/oauth/access_token",
                Map.of("client_id", clientId, "client_secret", clientSecret, "code", code, "redirect_uri", redirectUri));
        String token = n.path("access_token").asText("");
        if (token.isBlank()) throw new com.tenemsa.platform.connections.service.ConnectionException(400, "GitHub did not accept the sign-in. Please try again.");
        return new Tokens(token, null, null, n.path("scope").asText(""));
    }

    @Override
    public String accountLabel(String token) { return http.get("https://api.github.com/user", token).path("login").asText("GitHub account"); }

    @Override
    public List<Item> context(String token) { return context(token, QueryHints.NONE); }

    @Override
    public List<Item> context(String token, QueryHints hints) {
        List<Item> out = new ArrayList<>();
        for (JsonNode i : http.get("https://api.github.com/issues?filter=assigned&state=open&per_page=15", token)) {
            out.add(new Item("Open issue or pull request assigned to me: " + i.path("title").asText(),
                    i.path("repository").path("full_name").asText() + ", updated " + i.path("updated_at").asText(""), i.path("html_url").asText("")));
        }
        for (JsonNode i : http.get("https://api.github.com/search/issues?q=" + enc("is:open is:pr review-requested:@me") + "&per_page=10", token).path("items")) {
            out.add(new Item("Pull request waiting for my review: " + i.path("title").asText(), "updated " + i.path("updated_at").asText(""), i.path("html_url").asText("")));
        }
        if (!hints.isEmpty()) {
            // Anything the person is involved in that matches the words and/or the window ("what did I merge last week").
            StringBuilder q = new StringBuilder("involves:@me");
            if (hints.firstTerm() != null) q.append(' ').append(hints.firstTerm().replaceAll("[\\r\\n:]", " ")).append(" in:title,body");
            if (hints.from() != null) q.append(" updated:>=").append(hints.from().toString(), 0, 10);
            for (JsonNode i : http.get("https://api.github.com/search/issues?sort=updated&per_page=15&q=" + enc(q.toString()), token).path("items")) {
                if (hints.to() != null && i.path("updated_at").asText("").compareTo(hints.to().toString()) >= 0) continue;
                out.add(new Item((i.has("pull_request") ? "Pull request I am involved in: " : "Issue I am involved in: ") + i.path("title").asText(),
                        i.path("state").asText("") + ", updated " + i.path("updated_at").asText(""), i.path("html_url").asText("")));
            }
        }
        for (JsonNode r : http.get("https://api.github.com/user/repos?sort=updated&per_page=10", token)) {
            out.add(new Item("Repository: " + r.path("full_name").asText(), r.path("description").asText("") + " (pushed " + r.path("pushed_at").asText("") + ")", r.path("html_url").asText("")));
        }
        return out;
    }

    @Override
    public List<Item> preview(String token) {
        List<Item> out = new ArrayList<>();
        for (JsonNode i : http.get("https://api.github.com/issues?filter=assigned&state=open&per_page=5", token)) {
            out.add(new Item("Issue: " + i.path("title").asText(), i.path("repository").path("full_name").asText(), i.path("html_url").asText()));
        }
        for (JsonNode r : http.get("https://api.github.com/user/repos?sort=updated&per_page=5", token)) {
            out.add(new Item(r.path("full_name").asText(), r.path("description").asText(""), r.path("html_url").asText()));
        }
        return out;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
