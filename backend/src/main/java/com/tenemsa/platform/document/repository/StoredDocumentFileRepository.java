package com.tenemsa.platform.document.repository;

import com.tenemsa.platform.document.model.StoredDocumentFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredDocumentFileRepository extends JpaRepository<StoredDocumentFile, Long> {}
