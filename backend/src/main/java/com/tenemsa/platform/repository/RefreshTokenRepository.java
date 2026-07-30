package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // JOIN FETCH both levels (RefreshToken -> User -> Company): AuthService.refresh()
    // immediately calls token.getUser().getCompany() to rebuild the access
    // token's claims. Both relations are LAZY by default, and this endpoint
    // is invoked via a normal REST call, so it happens to work today — but
    // it's the identical fragile pattern that broke OAuth2LoginSuccessHandler
    // (see UserRepository.findByEmail), just not yet triggered from a context
    // where the safety net is absent. Fetching eagerly here removes the
    // fragility instead of waiting for that to surface as a separate bug report.
    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user u JOIN FETCH u.company WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    void revokeAllForUser(Long userId);
}
