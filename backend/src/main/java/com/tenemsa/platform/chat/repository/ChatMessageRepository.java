package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.ChatMessage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Every query is company-scoped (tenant isolation, Phase 4). */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByIdAndCompanyId(Long id, Long companyId);

    @Query("""
       SELECT m FROM ChatMessage m
       WHERE m.companyId = :companyId AND (
          (m.senderId = :id1 AND m.senderRole = :role1
          AND m.receiverId = :id2 AND m.receiverRole = :role2)
          OR
          (m.senderId = :id2 AND m.senderRole = :role2
          AND m.receiverId = :id1 AND m.receiverRole = :role1))
       ORDER BY m.timestamp ASC
    """)
    List<ChatMessage> getChatBetween(
            @Param("companyId") Long companyId,
            @Param("id1") Integer id1,
            @Param("role1") String role1,
            @Param("id2") Integer id2,
            @Param("role2") String role2
    );

    @Query("""
       SELECT COUNT(m) FROM ChatMessage m
       WHERE m.companyId = :companyId
         AND m.senderId = :senderId
         AND m.senderRole = :senderRole
         AND m.receiverId = :receiverId
         AND m.receiverRole = :receiverRole
         AND m.status <> 'READ'
    """)
    Long countUnread(
            @Param("companyId") Long companyId,
            @Param("senderId") Integer senderId,
            @Param("senderRole") String senderRole,
            @Param("receiverId") Integer receiverId,
            @Param("receiverRole") String receiverRole
    );

    @Modifying
    @Transactional
    @Query("""
       UPDATE ChatMessage m
       SET m.status = 'READ'
       WHERE m.companyId = :companyId
         AND m.senderId = :senderId
         AND m.senderRole = :senderRole
         AND m.receiverId = :receiverId
         AND m.receiverRole = :receiverRole
         AND m.status <> 'READ'
    """)
    int markAsRead(
            @Param("companyId") Long companyId,
            @Param("senderId") Integer senderId,
            @Param("senderRole") String senderRole,
            @Param("receiverId") Integer receiverId,
            @Param("receiverRole") String receiverRole
    );

    @Modifying
    @Transactional
    @Query("""
       DELETE FROM ChatMessage m
       WHERE m.companyId = :companyId AND (
          (m.senderId = :id1 AND m.senderRole = :role1
          AND m.receiverId = :id2 AND m.receiverRole = :role2)
          OR
          (m.senderId = :id2 AND m.senderRole = :role2
          AND m.receiverId = :id1 AND m.receiverRole = :role1))
    """)
    int deleteChatBetween(
            @Param("companyId") Long companyId,
            @Param("id1") Integer id1,
            @Param("role1") String role1,
            @Param("id2") Integer id2,
            @Param("role2") String role2
    );
}
