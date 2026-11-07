package com.tenemsa.platform.platformadmin.controller;

import com.tenemsa.platform.platformadmin.security.PlatformJwtFilter;
import com.tenemsa.platform.platformadmin.service.PlatformAnnouncementService;
import com.tenemsa.platform.platformadmin.service.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Announcements from the system administrators to companies. Exists only when the console is switched on. */
@RestController
@RequestMapping("/api/platform/announcements")
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformAnnouncementController {

    private final PlatformAnnouncementService service;

    public PlatformAnnouncementController(PlatformAnnouncementService service) { this.service = service; }

    private static PlatformJwtFilter.Principal me() {
        return (PlatformJwtFilter.Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.list(page, size);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PlatformAnnouncementService.CreateRequest r, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.create(r, me().id(), me().email(), req.getRemoteAddr()));
    }

    @PostMapping("/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable long id, HttpServletRequest req) {
        service.withdraw(id, me().id(), me().email(), req.getRemoteAddr());
        return Map.of("ok", true);
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<?> handle(PlatformException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
