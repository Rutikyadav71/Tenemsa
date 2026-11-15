package com.tenemsa.platform.connections.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** The two kinds of web call the providers make. An interface so tests can replace the network. */
public interface ConnectorHttp {
    /** POST a form (token exchange, refresh, revoke). Throws ConnectionException on a non-2xx reply. */
    JsonNode postForm(String url, Map<String, String> form);
    /** GET with a bearer token. Throws ConnectionException(401) if the app rejects the token. */
    JsonNode get(String url, String bearerToken);
}
