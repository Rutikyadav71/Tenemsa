package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlug(String slug);

    // TEMPORARY (Phase 1 only): the app still behaves as single-tenant —
    // registration/login assume "the" company rather than a chosen one.
    // Phase 4 replaces every caller of this method with a real company
    // registration + selection flow. Do not add new callers of this method;
    // it exists purely so Phase 1 can add the schema without breaking the
    // current single-company bootstrap flow.
    Optional<Company> findFirstByOrderByIdAsc();
}
