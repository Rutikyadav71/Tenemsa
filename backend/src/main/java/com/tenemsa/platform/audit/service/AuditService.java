package com.tenemsa.platform.service;

import com.tenemsa.platform.model.AuditAction;
import com.tenemsa.platform.model.AuditLog;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.AuditLogRepository;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the per-company audit trail.
 *
 * Auditing must never break the action being audited, so every write runs in
 * its own transaction (REQUIRES_NEW) and swallows its own failures: a broken
 * audit insert cannot roll back a login, a leave approval or a registration.
 * TransactionTemplate is used instead of @Transactional so the calls between
 * methods of this class are not defeated by Spring proxy self-invocation.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;
    private final AuthContext authContext;
    private final TransactionTemplate requiresNew;

    public AuditService(AuditLogRepository repository, UserRepository userRepository,
                        AuthContext authContext, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.authContext = authContext;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Explicit actor: used where there is no signed-in caller yet (login, registration, invite acceptance). */
    public void record(Long companyId, Long actorId, String actorName, AuditAction action,
                       String entityType, String entityId, String details) {
        if (companyId == null) return;
        try {
            AuditLog log = new AuditLog();
            log.setCompanyId(companyId);
            log.setActorId(actorId);
            log.setActorName(truncate(actorName, 150));
            log.setAction(action.name());
            log.setEntityType(truncate(entityType, 40));
            log.setEntityId(truncate(entityId, 64));
            log.setDetails(truncate(details, 500));
            log.setIpAddress(truncate(clientIp(), 64));
            requiresNew.executeWithoutResult(status -> repository.save(log));
        } catch (Exception e) {
            System.err.println("Audit write failed for " + action + ": " + e.getMessage());
        }
    }

    /** The actor is whoever the current request's JWT says they are. */
    public void recordCurrent(AuditAction action, String entityType, String entityId, String details) {
        Long companyId = authContext.currentCompanyId();
        String email = authContext.currentEmail();
        Long actorId = null;
        String actorName = email;
        if (companyId != null && email != null) {
            User u = userRepository.findByEmailAndCompanyId(email, companyId).orElse(null);
            if (u != null) { actorId = u.getId(); actorName = u.getName(); }
        }
        record(companyId, actorId, actorName, action, entityType, entityId, details);
    }

    public Page<AuditLog> search(Long companyId, String action, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (action == null || action.isBlank()) {
            return repository.findByCompanyIdOrderByCreatedAtDescIdDesc(companyId, pageable);
        }
        return repository.findByCompanyIdAndActionOrderByCreatedAtDescIdDesc(companyId, action.trim(), pageable);
    }

    private static String clientIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes sra)) return null;
            HttpServletRequest req = sra.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
