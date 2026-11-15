package com.tenemsa.platform.connections.service;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.company.service.CompanyLimits;
import com.tenemsa.platform.connections.model.CompanyApp;
import com.tenemsa.platform.connections.model.Connection;
import com.tenemsa.platform.connections.provider.ConnectorProvider;
import com.tenemsa.platform.connections.repository.CompanyAppRepository;
import com.tenemsa.platform.connections.repository.ConnectionRepository;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Connected apps. Each person signs in to an app themselves (OAuth), so Tenemsa can only ever see what that
 * person can see. A company must switch an app on first, and the plan caps how many connections it may hold.
 */
@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);
    private static final long STATE_MINUTES = 10;

    @Value("${app.frontend-url:http://localhost:5173}") private String frontendUrl;
    @Value("${app.backend-url:http://localhost:8080}") private String backendUrl;

    private final Map<String, ConnectorProvider> providers;
    private final ConnectionRepository connections;
    private final CompanyAppRepository apps;
    private final ConnectionCrypto crypto;
    private final CompanyLimits limits;
    private final UserRepository users;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public ConnectionService(List<ConnectorProvider> providers, ConnectionRepository connections, CompanyAppRepository apps,
                             ConnectionCrypto crypto, CompanyLimits limits, UserRepository users, AuditService audit) {
        this.providers = providers.stream().collect(Collectors.toMap(ConnectorProvider::code, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.connections = connections; this.apps = apps; this.crypto = crypto; this.limits = limits; this.users = users; this.audit = audit;
    }

    private ConnectorProvider provider(String code) {
        ConnectorProvider p = providers.get(code == null ? "" : code.toLowerCase());
        if (p == null) throw new ConnectionException(404, "That app is not available.");
        return p;
    }

    private boolean enabled(Long companyId, String code) {
        return apps.findByCompanyIdAndProvider(companyId, code).map(CompanyApp::isEnabled).orElse(false);
    }

    // ------------------------------------------------------------------ read

    public Map<String, Object> list(Long companyId, Long userId) {
        Map<String, Connection> mine = connections.findByCompanyIdAndUserId(companyId, userId).stream()
                .collect(Collectors.toMap(Connection::getProvider, Function.identity(), (a, b) -> a));
        Map<String, Boolean> on = new HashMap<>();
        for (CompanyApp a : apps.findByCompanyId(companyId)) on.put(a.getProvider(), a.isEnabled());
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConnectorProvider p : providers.values()) {
            Connection c = mine.get(p.code());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.code()); m.put("label", p.label()); m.put("description", p.description()); m.put("access", p.access());
            m.put("configured", p.configured()); m.put("enabled", on.getOrDefault(p.code(), false));
            m.put("connected", c != null);
            m.put("status", c == null ? null : c.getStatus());
            m.put("account", c == null ? null : c.getAccountLabel());
            m.put("connectedAt", c == null ? null : c.getCreatedAt().toString());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ready", crypto.isReady());
        out.put("apps", list);
        out.put("companyConnections", connections.countByCompanyId(companyId));
        out.put("maxConnections", limits.of(companyId).maxConnections());
        return out;
    }

    // ------------------------------------------------------------------ switch on/off

    @Transactional
    public void setEnabled(Long companyId, String code, boolean enabled) {
        ConnectorProvider p = provider(code);
        CompanyApp a = apps.findByCompanyIdAndProvider(companyId, p.code()).orElseGet(() -> {
            CompanyApp n = new CompanyApp(); n.setCompanyId(companyId); n.setProvider(p.code()); return n;
        });
        a.setEnabled(enabled);
        apps.save(a);
        if (!enabled) connections.deleteByCompanyIdAndProvider(companyId, p.code());   // switching an app off removes everyone's sign-in to it
    }

    // ------------------------------------------------------------------ sign in

    /** The address to send the browser to so the person can sign in to the app. */
    public String authorizeUrl(Long companyId, Long userId, String code) {
        if (!crypto.isReady()) throw new ConnectionException(503, "Connected apps are not set up on this server yet.");
        ConnectorProvider p = provider(code);
        if (!p.configured()) throw new ConnectionException(503, p.label() + " is not set up on this server yet.");
        if (!enabled(companyId, p.code())) throw new ConnectionException(403, p.label() + " is not switched on for your company. Ask an owner or admin.");
        assertRoom(companyId, userId, p.code());
        String nonce = Long.toHexString(random.nextLong());
        long expires = System.currentTimeMillis() + STATE_MINUTES * 60_000L;
        String state = crypto.sign(companyId + "|" + userId + "|" + p.code() + "|" + expires + "|" + nonce);
        return p.authorizeUrl(redirectUri(p.code()), state);
    }

    private String redirectUri(String code) { return backendUrl.replaceAll("/+$", "") + "/api/connections/callback/" + code; }

    private void assertRoom(Long companyId, Long userId, String code) {
        if (connections.findByCompanyIdAndUserIdAndProvider(companyId, userId, code).isPresent()) return;   // reconnecting is always fine
        Integer max = limits.of(companyId).maxConnections();
        if (max != null && connections.countByCompanyId(companyId) >= max) {
            throw new ConnectionException(409, "Your plan allows " + max + " connected app" + (max == 1 ? "" : "s") + " and you have reached that. Upgrade in Plan & billing.");
        }
    }

    /** The app sends the browser back here. Always returns the page to show next; never throws to the browser. */
    public String finishSignIn(String code, String authCode, String state, String error) {
        String back = frontendUrl.replaceAll("/+$", "") + "/connections/done?provider=" + enc(code == null ? "" : code);
        try {
            if (error != null && !error.isBlank()) return back + "&status=denied";
            String payload = crypto.verify(state);
            if (payload == null || authCode == null || authCode.isBlank()) return back + "&status=error";
            String[] parts = payload.split("\\|");
            if (parts.length != 5 || !parts[2].equals(code) || Long.parseLong(parts[3]) < System.currentTimeMillis()) return back + "&status=error";
            Long companyId = Long.valueOf(parts[0]), userId = Long.valueOf(parts[1]);
            ConnectorProvider p = provider(code);
            if (!enabled(companyId, p.code())) return back + "&status=off";
            assertRoom(companyId, userId, p.code());

            ConnectorProvider.Tokens t = p.exchange(authCode, redirectUri(p.code()));
            String label = null;
            try { label = p.accountLabel(t.accessToken()); } catch (RuntimeException e) { log.warn("Could not read the account name from {}: {}", p.code(), e.getMessage()); }

            Connection c = connections.findByCompanyIdAndUserIdAndProvider(companyId, userId, p.code()).orElseGet(() -> {
                Connection n = new Connection(); n.setCompanyId(companyId); n.setUserId(userId); n.setProvider(p.code()); return n;
            });
            c.setAccountLabel(label);
            c.setAccessTokenEnc(crypto.encrypt(t.accessToken()));
            if (t.refreshToken() != null) c.setRefreshTokenEnc(crypto.encrypt(t.refreshToken()));
            c.setExpiresAt(t.expiresInSeconds() == null ? null : LocalDateTime.now().plusSeconds(t.expiresInSeconds()));
            c.setScopes(t.scopes());
            c.setStatus("ACTIVE");
            connections.save(c);
            String who = users.findById(userId).map(User::getName).orElse("A user");
            audit.record(companyId, userId, who, AuditAction.CONNECTION_ADDED, "CONNECTION", p.code(), "Connected " + p.label());
            return back + "&status=ok";
        } catch (ConnectionException e) {
            log.info("Connecting {} failed: {}", code, e.getMessage());
            return back + "&status=error";
        } catch (Exception e) {
            log.warn("Connecting {} failed: {}", code, e.toString());
            return back + "&status=error";
        }
    }

    // ------------------------------------------------------------------ disconnect, preview, use

    @Transactional
    public void disconnect(Long companyId, Long userId, String code) {
        ConnectorProvider p = provider(code);
        Connection c = connections.findByCompanyIdAndUserIdAndProvider(companyId, userId, p.code())
                .orElseThrow(() -> new ConnectionException(404, "You are not connected to " + p.label() + "."));
        try { p.revoke(crypto.decrypt(c.getAccessTokenEnc())); } catch (RuntimeException ignored) { /* best effort */ }
        connections.delete(c);
        audit.recordCurrent(AuditAction.CONNECTION_REMOVED, "CONNECTION", p.code(), "Disconnected " + p.label());
    }

    /** A few recent items, so the person can see the connection works. */
    public List<Map<String, Object>> preview(Long companyId, Long userId, String code) {
        ConnectorProvider p = provider(code);
        if (!enabled(companyId, p.code())) throw new ConnectionException(403, p.label() + " is switched off for your company.");
        Connection c = connections.findByCompanyIdAndUserIdAndProvider(companyId, userId, p.code())
                .orElseThrow(() -> new ConnectionException(404, "You are not connected to " + p.label() + "."));
        return ConnectorProvider.asMaps(fetch(c, p));
    }

    /** Fetches recent items for a connection to show the person. */
    public List<ConnectorProvider.Item> fetch(Connection c, ConnectorProvider p) { return fetch(c, p, false); }

    /** Fetches the richer data Tenemsa AI answers from. */
    public List<ConnectorProvider.Item> fetchForAi(Connection c, ConnectorProvider p) { return fetch(c, p, true); }

    private List<ConnectorProvider.Item> fetch(Connection c, ConnectorProvider p, boolean forAi) {
        try {
            String token = accessToken(c, p);
            List<ConnectorProvider.Item> items = forAi ? p.context(token) : p.preview(token);
            c.setLastUsedAt(LocalDateTime.now());
            connections.save(c);
            return items;
        } catch (ConnectionException e) {
            if (e.getStatus() == 401) { markReconnect(c); throw new ConnectionException(409, "Your " + p.label() + " sign-in has expired. Please reconnect."); }
            throw e;
        }
    }

    private String accessToken(Connection c, ConnectorProvider p) {
        if ("NEEDS_RECONNECT".equals(c.getStatus())) throw new ConnectionException(409, "Your " + p.label() + " sign-in has expired. Please reconnect.");
        if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(LocalDateTime.now().plusSeconds(60))) {
            if (c.getRefreshTokenEnc() == null) { markReconnect(c); throw new ConnectionException(409, "Your " + p.label() + " sign-in has expired. Please reconnect."); }
            ConnectorProvider.Tokens t;
            try { t = p.refresh(crypto.decrypt(c.getRefreshTokenEnc())); }
            catch (ConnectionException e) { markReconnect(c); throw new ConnectionException(409, "Your " + p.label() + " sign-in has expired. Please reconnect."); }
            if (t == null) { markReconnect(c); throw new ConnectionException(409, "Your " + p.label() + " sign-in has expired. Please reconnect."); }
            c.setAccessTokenEnc(crypto.encrypt(t.accessToken()));
            if (t.refreshToken() != null) c.setRefreshTokenEnc(crypto.encrypt(t.refreshToken()));
            c.setExpiresAt(t.expiresInSeconds() == null ? null : LocalDateTime.now().plusSeconds(t.expiresInSeconds()));
            connections.save(c);
        }
        return crypto.decrypt(c.getAccessTokenEnc());
    }

    private void markReconnect(Connection c) { c.setStatus("NEEDS_RECONNECT"); connections.save(c); }

    /** The person's usable connections: the app is on for the company and the sign-in still works. */
    public List<Connection> usableFor(Long companyId, Long userId) {
        return connections.findByCompanyIdAndUserId(companyId, userId).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()) && enabled(companyId, c.getProvider()) && providers.containsKey(c.getProvider())).toList();
    }

    public Optional<ConnectorProvider> providerOf(String code) { return Optional.ofNullable(providers.get(code)); }
    public Optional<Connection> connectionOf(Long companyId, Long userId, String code) { return connections.findByCompanyIdAndUserIdAndProvider(companyId, userId, code); }
    public boolean isEnabled(Long companyId, String code) { return enabled(companyId, code); }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
