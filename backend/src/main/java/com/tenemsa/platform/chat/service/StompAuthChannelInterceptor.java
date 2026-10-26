package com.tenemsa.platform.chat.service;

import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Authenticates the WebSocket at the STOMP layer.
 *
 * HTTP security (JwtFilter) never runs for frames inside an open WebSocket, and "/ws/**"
 * is permitAll for the handshake — so before this, ANYONE could connect, subscribe to any
 * user's queue and publish as any sender. Now:
 *  - CONNECT must carry "Authorization: Bearer <access token>" (STOMP native header);
 *    unknown/frozen/invalid callers are rejected.
 *  - The verified identity (companyId, userId, empId, chat tag) is stored in the session.
 *  - SUBSCRIBE is only allowed to the caller's own queue, in the caller's own company.
 *  - ChatController reads the identity from the session, never from the payload.
 *
 * Deliberately does NOT call accessor.setUser(): the existing queue routing relies on the
 * literal "/user/{key}/queue/messages" destination, and a Principal would re-map it.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String ATTR_COMPANY_ID = "companyId";
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_EMP_ID = "empId";
    public static final String ATTR_TAG = "chatTag";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public StompAuthChannelInterceptor(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return authenticate(accessor) ? message : null;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return mayAccess(accessor) ? message : null;
        }

        return message;
    }

    private boolean authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        String token = header.substring(7);
        try {
            if (!jwtUtil.isTokenValid(token)) return false;
            User user = userRepository.findByEmailAndCompanyId(jwtUtil.extractEmail(token), jwtUtil.extractCompanyId(token)).orElse(null);
            if (user == null || user.isFrozen() || user.getCompany() == null) return false;

            Map<String, Object> session = accessor.getSessionAttributes();
            if (session == null) return false;
            session.put(ATTR_COMPANY_ID, user.getCompany().getId());
            session.put(ATTR_USER_ID, user.getId());
            session.put(ATTR_EMP_ID, user.getEmpId());
            session.put(ATTR_TAG, ChatIdentity.tagFor(user.getRole()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Only "/user/{ownQueueKey}/queue/messages" may be subscribed to. */
    private boolean mayAccess(StompHeaderAccessor accessor) {
        Map<String, Object> session = accessor.getSessionAttributes();
        if (session == null || session.get(ATTR_COMPANY_ID) == null) return false;

        String tag = (String) session.get(ATTR_TAG);
        Integer id = "ADMIN".equals(tag)
                ? Integer.valueOf(((Long) session.get(ATTR_USER_ID)).intValue())
                : (Integer) session.get(ATTR_EMP_ID);
        String own = "/user/" + ChatIdentity.queueKey((Long) session.get(ATTR_COMPANY_ID), tag, id)
                + "/queue/messages";
        return own.equals(accessor.getDestination());
    }
}
