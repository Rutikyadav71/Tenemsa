package com.tenemsa.platform.platformadmin.repository;

import com.tenemsa.platform.platformadmin.model.PlatformAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {
    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);
}
