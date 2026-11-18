package com.tenemsa.platform.connections.repository;

import com.tenemsa.platform.connections.model.Connection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** Every finder takes companyId: connections are tenant data. */
public interface ConnectionRepository extends JpaRepository<Connection, Long> {
    List<Connection> findByCompanyIdAndUserId(Long companyId, Long userId);
    Optional<Connection> findByCompanyIdAndUserIdAndProvider(Long companyId, Long userId, String provider);
    long countByCompanyId(Long companyId);
    long countByCompanyIdAndProvider(Long companyId, String provider);
    void deleteByCompanyIdAndProvider(Long companyId, String provider);
    void deleteByCompanyId(Long companyId);
    List<Connection> findByCompanyId(Long companyId);
}
