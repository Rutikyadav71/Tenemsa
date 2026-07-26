package com.tenemsa.platform.controller;

import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.security.SecurityRoles;
import com.tenemsa.platform.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    @Autowired private CloudinaryService cloudinaryService;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthContext authContext;

    // Previously had no @PreAuthorize at all — any authenticated user could
    // overwrite any OTHER employee's or admin's profile photo just by passing
    // a different empId/adminId in the URL. Same class of gap fixed
    // elsewhere; closed here too.
    @PreAuthorize(SecurityRoles.IS_ADMIN + " or @employeeSecurity.isSelf(#empId)")
    @PostMapping(value = "/profile/{empId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadEmployeeProfile(@PathVariable int empId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("No file provided");
        try {
            String url = cloudinaryService.uploadProfileImage(file, "emp_" + empId);
            userRepository.findByCompanyIdAndEmpId(authContext.currentCompanyId(), empId).ifPresent(user -> {
                user.setProfileImageUrl(url);
                userRepository.save(user);
            });
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        catch (IOException e) { return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage()); }
    }

    // adminId here is the User's surrogate primary key (id), same as before
    // when it referred to Admin's own primary key — unchanged meaning, just
    // now resolved against the unified users table.
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PostMapping(value = "/admin-profile/{adminId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAdminProfile(@PathVariable Long adminId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("No file provided");
        try {
            String url = cloudinaryService.uploadProfileImage(file, "admin_" + adminId);
            userRepository.findById(adminId).ifPresent(admin -> {
                admin.setProfileImageUrl(url);
                userRepository.save(admin);
            });
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        catch (IOException e) { return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage()); }
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadChatFile(@RequestParam("file") MultipartFile file,
            @RequestParam("sender") String sender) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("No file provided");
        try { return ResponseEntity.ok(cloudinaryService.uploadChatFile(file, sender)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        catch (IOException e) { return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage()); }
    }
}
