package com.tenemsa.platform.chat.controller;

import com.tenemsa.platform.chat.model.ChatMessage;
import com.tenemsa.platform.chat.service.ChatMessageService;
import com.tenemsa.platform.chat.service.ChatIdentity;
import com.tenemsa.platform.chat.service.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageService chatMessages;

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

        Integer callerChatId = "ADMIN".equals(callerTag) ? Integer.valueOf(userId.intValue()) : empId;
        try {
            ChatMessage saved = chatMessages.save(companyId, callerTag, callerChatId, msg);
            String receiverKey = ChatIdentity.queueKey(companyId, saved.getReceiverRole(), saved.getReceiverId());
            String senderKey   = ChatIdentity.queueKey(companyId, saved.getSenderRole(), saved.getSenderId());
            messagingTemplate.convertAndSendToUser(receiverKey, "/queue/messages", saved);
            messagingTemplate.convertAndSendToUser(senderKey, "/queue/messages", saved);
        } catch (IllegalArgumentException rejected) {
            // A rejected frame is dropped; the REST history never shows it.
        }
    }
}
