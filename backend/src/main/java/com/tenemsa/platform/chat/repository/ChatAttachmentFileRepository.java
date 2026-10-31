package com.tenemsa.platform.chat.repository;

import com.tenemsa.platform.chat.model.ChatAttachmentFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAttachmentFileRepository extends JpaRepository<ChatAttachmentFile, Long> {}
