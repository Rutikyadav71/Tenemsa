package com.tenemsa.platform.platformadmin.service;

import com.tenemsa.platform.platformadmin.model.PlatformAuditLog;
import com.tenemsa.platform.platformadmin.repository.PlatformAuditLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformAuditService {
    private final PlatformAuditLogRepository repo;
    public PlatformAuditService(PlatformAuditLogRepository repo) { this.repo = repo; }

    public void record(Long adminId, String adminEmail, String action, Long companyId, String companyName, String details, String ip) {
        PlatformAuditLog l = new PlatformAuditLog();
        l.setAdminId(adminId); l.setAdminEmail(adminEmail); l.setAction(action);
        l.setCompanyId(companyId); l.setCompanyName(companyName); l.setDetails(details); l.setIpAddress(ip);
        repo.save(l);
    }

    public Page<PlatformAuditLog> page(int page, int size, String action) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return action == null || action.isBlank() ? repo.findAllByOrderByCreatedAtDescIdDesc(pr)
                : repo.findByActionOrderByCreatedAtDescIdDesc(action, pr);
    }
}
