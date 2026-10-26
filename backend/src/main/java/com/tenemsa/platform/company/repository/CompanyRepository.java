package com.tenemsa.platform.company.repository;

import com.tenemsa.platform.company.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
