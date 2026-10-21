package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.Notification;
import com.tenemsa.platform.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository repo;

    @Autowired
    private AuthContext authContext;

    @GetMapping
    public List<Notification> getAll() {
        return repo.findByCompanyId(authContext.currentCompanyId(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @PutMapping("/mark-all-read")
    @Transactional
    public void markAllRead() {
        repo.markAllReadForCompany(authContext.currentCompanyId());
    }
}
