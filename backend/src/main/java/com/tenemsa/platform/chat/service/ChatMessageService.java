package com.tenemsa.platform.chat.service;

import com.tenemsa.platform.chat.model.ChatAttachment;
import com.tenemsa.platform.chat.model.ChatMessage;
import com.tenemsa.platform.chat.repository.ChatAttachmentRepository;
import com.tenemsa.platform.chat.repository.ChatMessageRepository;
import com.tenemsa.platform.employee.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Validates and saves one chat message. Everything about who is sending comes from the authenticated
 * session (ChatController) and everything about an attachment comes from the stored attachment,
 * never from what the client put in the payload.
 */
@Service
public class ChatMessageService {

    private final ChatMessageRepository chatRepo;
    private final ChatAttachmentRepository attachments;
    private final UserRepository userRepository;

    public ChatMessageService(ChatMessageRepository chatRepo, ChatAttachmentRepository attachments, UserRepository userRepository) {
        this.chatRepo = chatRepo;
        this.attachments = attachments;
        this.userRepository = userRepository;
    }

    /** @throws IllegalArgumentException when the message is not acceptable; nothing is saved. */
    @Transactional
    public ChatMessage save(Long companyId, String callerTag, Integer callerChatId, ChatMessage msg) {
        if (msg == null || msg.getSenderId() == null || msg.getSenderRole() == null
                || msg.getReceiverId() == null || msg.getReceiverRole() == null) {
            throw new IllegalArgumentException("Incomplete message.");
        }
        boolean hasText = msg.getMessage() != null && !msg.getMessage().trim().isEmpty();
        if (!hasText && msg.getAttachmentId() == null) throw new IllegalArgumentException("Empty message.");

        String senderRole = msg.getSenderRole().toUpperCase();
        String receiverRole = msg.getReceiverRole().toUpperCase();
        if (callerChatId == null || !callerTag.equals(senderRole) || !callerChatId.equals(msg.getSenderId())) {
            throw new IllegalArgumentException("Sender mismatch.");
        }
        if (!receiverExists(companyId, receiverRole, msg.getReceiverId())) throw new IllegalArgumentException("Unknown receiver.");

        ChatAttachment att = null;
        if (msg.getAttachmentId() != null) {
            att = attachments.findByIdAndCompanyId(msg.getAttachmentId(), companyId).orElse(null);
            if (att == null || att.getMessageId() != null || !att.getUploaderTag().equals(callerTag)
                    || !att.getUploaderId().equals(callerChatId)) {
                throw new IllegalArgumentException("Attachment not available.");
            }
        }

        msg.setId(null);
        msg.setCompanyId(companyId);
        msg.setSenderRole(senderRole);
        msg.setReceiverRole(receiverRole);
        msg.setTimestamp(LocalDateTime.now());
        msg.setStatus("SENT");
        msg.setReaction(null);
        msg.setStarred(false);
        if (!hasText) msg.setMessage("");
        if (att != null) {
            msg.setAttachmentName(att.getFileName());
            msg.setAttachmentType(att.getContentType());
            msg.setAttachmentSize(att.getSizeBytes());
        } else {
            msg.setAttachmentId(null); msg.setAttachmentName(null); msg.setAttachmentType(null); msg.setAttachmentSize(null);
        }
        ChatMessage saved = chatRepo.save(msg);
        if (att != null) { att.setMessageId(saved.getId()); attachments.save(att); }
        return saved;
    }

    public boolean receiverExists(Long companyId, String tag, Integer id) {
        if ("ADMIN".equals(tag)) {
            return userRepository.findByIdAndCompanyId(id.longValue(), companyId)
                    .filter(u -> "ADMIN".equals(ChatIdentity.tagFor(u.getRole()))).isPresent();
        }
        if ("USER".equals(tag)) {
            return userRepository.findByCompanyIdAndEmpId(companyId, id)
                    .filter(u -> "USER".equals(ChatIdentity.tagFor(u.getRole()))).isPresent();
        }
        return false;
    }
}
