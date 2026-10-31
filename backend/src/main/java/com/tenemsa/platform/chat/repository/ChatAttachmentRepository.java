package com.tenemsa.platform.chat.repository;

import com.tenemsa.platform.chat.model.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    Optional<ChatAttachment> findByIdAndCompanyId(Long id, Long companyId);
}
