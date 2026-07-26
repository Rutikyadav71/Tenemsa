package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.ChatMessage;
import com.tenemsa.platform.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ChatMessageRepository chatRepo;

    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessage msg) {

        if (msg.getSenderId() == null ||
                msg.getSenderRole() == null ||
                msg.getReceiverId() == null ||
                msg.getReceiverRole() == null ||
                msg.getMessage() == null ||
                msg.getMessage().trim().isEmpty()) {
            return;
        }

        msg.setSenderRole(msg.getSenderRole().toUpperCase());
        msg.setReceiverRole(msg.getReceiverRole().toUpperCase());

        msg.setTimestamp(LocalDateTime.now());
        msg.setStatus("SENT");

        ChatMessage saved = chatRepo.save(msg);

        String receiverKey = msg.getReceiverRole() + "_" + msg.getReceiverId();
        String senderKey   = msg.getSenderRole()   + "_" + msg.getSenderId();

        messagingTemplate.convertAndSendToUser(
                receiverKey, "/queue/messages", saved
        );

        messagingTemplate.convertAndSendToUser(
                senderKey, "/queue/messages", saved
        );
    }
}