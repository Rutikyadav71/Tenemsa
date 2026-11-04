package com.tenemsa.platform.ai.conversation.repository;

import com.tenemsa.platform.ai.conversation.model.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every finder takes company and user: a chat belongs to one person. */
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    Optional<AiConversation> findByIdAndCompanyIdAndUserId(Long id, Long companyId, Long userId);

    List<AiConversation> findByCompanyIdAndUserIdAndKindOrderByUpdatedAtDescIdDesc(Long companyId, Long userId, String kind);
}
