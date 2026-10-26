package com.tenemsa.platform.chat.controller;

import com.tenemsa.platform.chat.model.ChatMessage;
import com.tenemsa.platform.chat.repository.ChatMessageRepository;
import com.tenemsa.platform.chat.service.ChatIdentity;
import com.tenemsa.platform.chat.service.StompAuthChannelInterceptor;
import com.tenemsa.platform.employee.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageRepository chatRepo;

    @Autowired
    private UserRepository userRepository;

    /**
     * Identity comes from the authenticated STOMP session (see
     * StompAuthChannelInterceptor), never from the payload: a client can no longer
     * send "as" someone else, or to a user in another company.
     */
    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessage msg, SimpMessageHeaderAccessor headers) {
        Map<String, Object> session = headers.getSessionAttributes();
        if (session == null || session.get(StompAuthChannelInterceptor.ATTR_COMPANY_ID) == null) return;

        Long companyId = (Long) session.get(StompAuthChannelInterceptor.ATTR_COMPANY_ID);
        Long userId = (Long) session.get(StompAuthChannelInterceptor.ATTR_USER_ID);
        Integer empId = (Integer) session.get(StompAuthChannelInterceptor.ATTR_EMP_ID);
        String callerTag = (String) session.get(StompAuthChannelInterceptor.ATTR_TAG);

        if (msg.getSenderId() == null ||
                msg.getSenderRole() == null ||
                msg.getReceiverId() == null ||
                msg.getReceiverRole() == null ||
                msg.getMessage() == null ||
                msg.getMessage().trim().isEmpty()) {
            return;
        }

        String senderRole = msg.getSenderRole().toUpperCase();
        String receiverRole = msg.getReceiverRole().toUpperCase();

        // Sender must be the authenticated caller.
        Integer callerChatId = "ADMIN".equals(callerTag) ? Integer.valueOf(userId.intValue()) : empId;
        if (callerChatId == null || !callerTag.equals(senderRole) || !callerChatId.equals(msg.getSenderId())) return;

        // Receiver must exist in the SAME company and carry the tag it claims.
        if (!receiverExists(companyId, receiverRole, msg.getReceiverId())) return;

        msg.setId(null);
        msg.setCompanyId(companyId);
        msg.setSenderRole(senderRole);
        msg.setReceiverRole(receiverRole);
        msg.setTimestamp(LocalDateTime.now());
        msg.setStatus("SENT");

        ChatMessage saved = chatRepo.save(msg);

        String receiverKey = ChatIdentity.queueKey(companyId, receiverRole, msg.getReceiverId());
        String senderKey   = ChatIdentity.queueKey(companyId, senderRole, msg.getSenderId());

        messagingTemplate.convertAndSendToUser(receiverKey, "/queue/messages", saved);
        messagingTemplate.convertAndSendToUser(senderKey, "/queue/messages", saved);
    }

    private boolean receiverExists(Long companyId, String tag, Integer id) {
        if ("ADMIN".equals(tag)) {
            return userRepository.findByIdAndCompanyId(id.longValue(), companyId)
                    .filter(u -> "ADMIN".equals(ChatIdentity.tagFor(u.getRole())))
                    .isPresent();
        }
        if ("USER".equals(tag)) {
            return userRepository.findByCompanyIdAndEmpId(companyId, id)
                    .filter(u -> "USER".equals(ChatIdentity.tagFor(u.getRole())))
                    .isPresent();
        }
        return false;
    }
}
