package com.tenemsa.platform.knowledge.repository;

import com.tenemsa.platform.knowledge.model.KbDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every finder takes companyId first: documents are tenant data. */
public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    List<KbDocument> findByCompanyIdOrderByCreatedAtDescIdDesc(Long companyId);

    Optional<KbDocument> findByIdAndCompanyId(Long id, Long companyId);

    Optional<KbDocument> findFirstByCompanyIdAndContentHash(Long companyId, String contentHash);
}
