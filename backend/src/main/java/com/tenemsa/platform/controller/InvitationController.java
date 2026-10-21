package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.*;
import com.tenemsa.platform.model.Invitation;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.service.AuthService;
import com.tenemsa.platform.service.InvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Invitations. Managing them needs user:create (and the role check inside
 * InvitationService); preview and accept are public because the invitee has
 * no account yet, the emailed token is their credential.
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    @Autowired private InvitationService invitationService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthContext authContext;

    @PreAuthorize("hasAuthority('user:create')")
    @PostMapping
    public ResponseEntity<?> invite(@Valid @RequestBody InvitationRequest req) {
        try {
            Invitation inv = invitationService.invite(authContext.currentCompanyId(), caller(), req);
            return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.from(inv));
        } catch (InvitationService.InvitationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('user:create')")
    @GetMapping
    public List<InvitationResponse> pending() {
        return invitationService.pending(authContext.currentCompanyId()).stream().map(InvitationResponse::from).toList();
    }

    @PreAuthorize("hasAuthority('user:create')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id) {
        try {
            invitationService.revoke(authContext.currentCompanyId(), id, caller());
            return ResponseEntity.ok(Map.of("message", "Invitation revoked"));
        } catch (InvitationService.InvitationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam String token) {
        try {
            return ResponseEntity.ok(invitationService.preview(token));
        } catch (InvitationService.InvitationException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/accept")
    public ResponseEntity<?> accept(@Valid @RequestBody AcceptInvitationRequest req, HttpServletRequest httpRequest) {
        try {
            User user = invitationService.accept(req.token(), req.name(), req.password());
            String ua = httpRequest.getHeader("User-Agent");
            AuthResponse resp = authService.issueAuthResponse(user, false,
                    ua != null && ua.length() > 255 ? ua.substring(0, 255) : ua);
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (InvitationService.InvitationException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private User caller() {
        return userRepository.findByEmailAndCompanyId(authContext.currentEmail(), authContext.currentCompanyId())
                .orElseThrow(() -> new RuntimeException("Not authenticated"));
    }
}
