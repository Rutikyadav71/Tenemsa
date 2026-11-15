package com.tenemsa.platform.connections.repository;

import com.tenemsa.platform.connections.model.CompanyApp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyAppRepository extends JpaRepository<CompanyApp, Long> {
    List<CompanyApp> findByCompanyId(Long companyId);
    Optional<CompanyApp> findByCompanyIdAndProvider(Long companyId, String provider);
}
