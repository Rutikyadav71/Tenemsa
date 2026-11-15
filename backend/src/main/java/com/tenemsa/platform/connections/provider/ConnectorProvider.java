package com.tenemsa.platform.connections.provider;

import java.util.List;
import java.util.Map;

/** One outside app Tenemsa can connect to. Adding an app means adding one class that implements this. */
public interface ConnectorProvider {

    /** What comes back from an app after sign-in. */
    record Tokens(String accessToken, String refreshToken, Long expiresInSeconds, String scopes) {}

    /** One line shown to the person in the preview (and later handed to the AI). */
    record Item(String title, String subtitle, String url) {}

    String code();
    String label();
    String description();
    /** What Tenemsa will be allowed to do, in plain words. */
    String access();
    /** True when the server has the app's client id and secret. */
    boolean configured();
    String authorizeUrl(String redirectUri, String state);
    Tokens exchange(String code, String redirectUri);
    /** Returns new tokens, or null when this app does not use refresh tokens. */
    default Tokens refresh(String refreshToken) { return null; }
    String accountLabel(String accessToken);
    /** A few recent items for the person to look at. */
    List<Item> preview(String accessToken);
    /** More detail for Tenemsa AI to answer from. Defaults to the preview. */
    default List<Item> context(String accessToken) { return preview(accessToken); }
    /** Best effort: tell the app the token is no longer wanted. */
    default void revoke(String token) {}

    static List<Map<String, Object>> asMaps(List<Item> items) {
        return items.stream().map(i -> Map.<String, Object>of("title", i.title(), "subtitle", i.subtitle() == null ? "" : i.subtitle(), "url", i.url() == null ? "" : i.url())).toList();
    }
}
