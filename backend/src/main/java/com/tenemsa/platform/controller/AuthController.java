package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.AuthResponse;
import com.tenemsa.platform.dto.LoginRequest;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.security.JwtUtil;
import com.tenemsa.platform.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User user = authService.login(request);
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getCompany().getId());

            AuthResponse resp = new AuthResponse(token, user.getRole().name(), user.getName(), user.getEmail());
            resp.setId(user.getId());
            resp.setEmpId(user.getEmpId());
            resp.setCompanyId(user.getCompany().getId());
            resp.setDepartment(user.getDepartment());
            resp.setSalary(user.getSalary());
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("ACCOUNT_FROZEN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(msg);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(msg);
        }
    }
}
