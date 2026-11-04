package com.tenemsa.platform.upload.controller;

import com.tenemsa.platform.upload.service.ProfileImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** Serves stored profile photos to normal image tags. The address is a long random token, like a Cloudinary link. */
@RestController
@RequestMapping("/api/public/avatars")
public class PublicAvatarController {
    private final ProfileImageService service;
    public PublicAvatarController(ProfileImageService service) { this.service = service; }

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> get(@PathVariable String token) {
        return service.find(token)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable())
                        .body(p.getContent()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
