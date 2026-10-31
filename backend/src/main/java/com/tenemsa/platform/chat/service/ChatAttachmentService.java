package com.tenemsa.platform.chat.service;

import com.tenemsa.platform.chat.model.ChatAttachment;
import com.tenemsa.platform.chat.model.ChatAttachmentFile;
import com.tenemsa.platform.chat.model.ChatMessage;
import com.tenemsa.platform.chat.repository.ChatAttachmentFileRepository;
import com.tenemsa.platform.chat.repository.ChatAttachmentRepository;
import com.tenemsa.platform.chat.repository.ChatMessageRepository;
import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Stores and serves files sent in chat. Only the two people in a conversation can ever read a sent attachment. */
@Service
public class ChatAttachmentService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final ChatAttachmentRepository attachments;
    private final ChatAttachmentFileRepository files;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final AuthContext authContext;

    public ChatAttachmentService(ChatAttachmentRepository attachments, ChatAttachmentFileRepository files,
                                 ChatMessageRepository messages, UserRepository users, AuthContext authContext) {
        this.attachments = attachments;
        this.files = files;
        this.messages = messages;
        this.users = users;
        this.authContext = authContext;
    }

    public record Download(String fileName, String contentType, boolean image, byte[] bytes) {}

    /** The caller's chat identity: admin-tier users are ("ADMIN", database id), everyone else ("USER", empId). */
    private record Me(String tag, Integer chatId) {}

    private Me me() {
        User u = users.findByEmailAndCompanyId(authContext.currentEmail(), authContext.currentCompanyId())
                .orElseThrow(() -> new AccessDeniedException("Not authenticated"));
        String tag = ChatIdentity.tagFor(u.getRole());
        Integer chatId = "ADMIN".equals(tag) ? Integer.valueOf(u.getId().intValue()) : u.getEmpId();
        if (chatId == null) throw new AccessDeniedException("Not authenticated");
        return new Me(tag, chatId);
    }

    @Transactional
    public Map<String, Object> store(MultipartFile file) {
        Me me = me();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to send.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("That file is larger than 10 MB.");
        String name = ChatFileTypes.cleanName(file.getOriginalFilename());
        String ext = ChatFileTypes.extension(name);
        if (!ChatFileTypes.isAllowed(ext)) {
            throw new IllegalArgumentException("This file type can't be sent in chat. Allowed: PDF, Word, Excel, PowerPoint, text, CSV, ZIP, images, audio and MP4 video.");
        }
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new IllegalArgumentException("The file could not be read."); }
        if (!ChatFileTypes.looksLike(ext, bytes)) {
            throw new IllegalArgumentException("The contents of '" + name + "' don't match its ." + ext + " type.");
        }
        ChatAttachment a = new ChatAttachment();
        a.setCompanyId(authContext.currentCompanyId());
        a.setUploaderTag(me.tag());
        a.setUploaderId(me.chatId());
        a.setFileName(name);
        a.setContentType(ChatFileTypes.contentType(ext).split(";")[0]);
        a.setSizeBytes((long) bytes.length);
        a = attachments.save(a);
        files.save(new ChatAttachmentFile(a.getId(), bytes));
        return Map.of("id", a.getId(), "fileName", a.getFileName(), "contentType", a.getContentType(),
                "sizeBytes", a.getSizeBytes(), "image", ChatFileTypes.isImage(ext));
    }

    @Transactional(readOnly = true)
    public Download download(Long id) {
        Me me = me();
        ChatAttachment a = attachments.findByIdAndCompanyId(id, authContext.currentCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("File not found."));
        boolean allowed;
        if (a.getMessageId() == null) {
            allowed = a.getUploaderTag().equals(me.tag()) && a.getUploaderId().equals(me.chatId());   // not sent yet: uploader only
        } else {
            ChatMessage m = messages.findByIdAndCompanyId(a.getMessageId(), authContext.currentCompanyId()).orElse(null);
            allowed = m != null && !"DELETED".equals(m.getStatus())
                    && ((me.tag().equals(m.getSenderRole()) && me.chatId().equals(m.getSenderId()))
                    || (me.tag().equals(m.getReceiverRole()) && me.chatId().equals(m.getReceiverId())));
        }
        if (!allowed) throw new ResourceNotFoundException("File not found.");
        byte[] bytes = files.findById(a.getId()).orElseThrow(() -> new ResourceNotFoundException("File not found.")).getContent();
        return new Download(a.getFileName(), a.getContentType(), ChatFileTypes.isImage(ChatFileTypes.extension(a.getFileName())), bytes);
    }

    /** Removes the stored bytes and the row; used when a message is deleted. */
    @Transactional
    public void purge(Long attachmentId) {
        if (attachmentId == null) return;
        files.deleteById(attachmentId);
        attachments.deleteById(attachmentId);
    }
}
