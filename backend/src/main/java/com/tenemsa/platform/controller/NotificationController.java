package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.Notification;
import com.tenemsa.platform.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository repo;

    @GetMapping
    public List<Notification> getAll() {
        return repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @PutMapping("/mark-all-read")
    @Transactional
    public void markAllRead() {
        repo.markAllRead();
    }
}
