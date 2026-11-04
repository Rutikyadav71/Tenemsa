package com.tenemsa.platform.upload.service;

import com.tenemsa.platform.upload.model.ProfileImage;
import com.tenemsa.platform.upload.repository.ProfileImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores a profile photo and returns the address to show it from.
 * Uses Cloudinary when it is configured and working; otherwise keeps the photo in the database,
 * so uploads never depend on an outside service being set up.
 */
@Service
public class ProfileImageService {
    private static final Logger log = LoggerFactory.getLogger(ProfileImageService.class);
    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final CloudinaryService cloudinary;
    private final ProfileImageRepository images;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    public ProfileImageService(CloudinaryService cloudinary, ProfileImageRepository images,
                               @Value("${cloudinary.cloud-name:}") String cloudName,
                               @Value("${cloudinary.api-key:}") String apiKey,
                               @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.cloudinary = cloudinary; this.images = images;
        this.cloudName = cloudName; this.apiKey = apiKey; this.apiSecret = apiSecret;
    }

    private boolean cloudinaryConfigured() { return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank(); }

    /** @throws IllegalArgumentException with a message that is safe to show when the file is not acceptable. */
    @Transactional
    public String store(MultipartFile file, Long companyId, String ownerKey, String cloudinaryRef) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) throw new IllegalArgumentException("No file provided.");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("The photo is larger than 2 MB. Choose a smaller one.");
        String type = detectType(bytes);
        if (type == null) throw new IllegalArgumentException("Only JPG, PNG or WEBP photos are allowed.");

        if (cloudinaryConfigured()) {
            try {
                return cloudinary.uploadProfileImage(file, cloudinaryRef);
            } catch (Exception e) {
                // Wrong keys, no network, or Cloudinary down: keep the photo ourselves instead of failing.
                log.warn("Cloudinary upload failed ({}); storing the profile photo in the database instead.", e.getMessage());
            }
        }
        images.deleteForOwner(companyId, ownerKey);
        String token = UUID.randomUUID().toString().replace("-", "");
        images.save(new ProfileImage(token, companyId, ownerKey, type, bytes));
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/public/avatars/").path(token).toUriString();
    }

    public Optional<ProfileImage> find(String token) { return images.findById(token); }

    /** Decides the type from the file's own first bytes, not from the name or the type the browser claims. */
    static String detectType(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
