package com.tenemsa.platform.knowledge.repository;

import com.tenemsa.platform.knowledge.model.KbChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbChunkRepository extends JpaRepository<KbChunk, Long> {

    List<KbChunk> findByCompanyIdAndDocumentIdOrderByChunkIndexAsc(Long companyId, Long documentId);

    @Modifying
    @Query("delete from KbChunk c where c.companyId = :companyId and c.documentId = :documentId")
    void deleteAllForDocument(@Param("companyId") Long companyId, @Param("documentId") Long documentId);
}
