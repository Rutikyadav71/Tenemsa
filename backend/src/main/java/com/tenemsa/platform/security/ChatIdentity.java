package com.tenemsa.platform.security;

import com.tenemsa.platform.model.Role;

import java.util.Set;

/**
 * The chat subsystem routes by a fixed two-value tag ("ADMIN" / "USER"), which is a
 * transport convention and NOT the business Role hierarchy (see ChatUserController).
 * This is the one place that maps a Role to its tag so login (AuthService), the chat
 * directory (ChatUserController), and the WebSocket layer cannot drift apart.
 */
public final class ChatIdentity {

    private static final Set<Role> ADMIN_TAGGED = Set.of(Role.OWNER, Role.ADMIN, Role.SUPER_ADMIN);

    private ChatIdentity() {}

    public static String tagFor(Role role) {
        return ADMIN_TAGGED.contains(role) ? "ADMIN" : "USER";
    }

    /** Name of the per-user queue. Includes companyId: "USER_101" exists in EVERY company,
     *  so without it two tenants' employee 101 would share (and read) one queue. */
    public static String queueKey(Long companyId, String tag, Integer id) {
        return "c" + companyId + "_" + tag + "_" + id;
    }
}
