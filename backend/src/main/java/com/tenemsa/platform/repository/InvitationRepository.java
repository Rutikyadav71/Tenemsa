package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Tenant finders take companyId first; findByTokenHash is the one public lookup (the token IS the credential). */
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByIdAndCompanyId(Long id, Long companyId);

    List<Invitation> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);

    List<Invitation> findByCompanyIdAndEmailIgnoreCaseAndStatus(Long companyId, String email, String status);
}
