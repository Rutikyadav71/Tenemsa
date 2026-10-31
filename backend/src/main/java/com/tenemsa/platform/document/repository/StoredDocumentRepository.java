package com.tenemsa.platform.document.repository;

import com.tenemsa.platform.document.model.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every finder starts from the company. */
public interface StoredDocumentRepository extends JpaRepository<StoredDocument, Long> {

    List<StoredDocument> findByCompanyIdOrderByCreatedAtDescIdDesc(Long companyId);

    Optional<StoredDocument> findByIdAndCompanyId(Long id, Long companyId);

    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM StoredDocument d WHERE d.companyId = :companyId")
    long totalBytes(@Param("companyId") Long companyId);
}
