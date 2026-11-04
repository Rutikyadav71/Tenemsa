package com.tenemsa.platform.ai.conversation.repository;

import com.tenemsa.platform.ai.conversation.model.AiConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiConversationMessageRepository extends JpaRepository<AiConversationMessage, Long> {

    List<AiConversationMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    long countByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
