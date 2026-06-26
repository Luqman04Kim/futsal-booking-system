package com.footballsystem.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Service for uploading images.
 *
 * If Cloudinary credentials are configured (CLOUDINARY_CLOUD_NAME,
 * CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET), images are uploaded to
 * Cloudinary and get a permanent public HTTPS URL that survives Railway
 * redeploys.
 *
 * If credentials are NOT set, falls back to local disk storage (useful for
 * local development).
 */
@Service
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${upload.dir:uploads}")
    private String uploadDirProperty;

    /**
     * Upload a file. Returns a permanent URL (Cloudinary) or a local /uploads/
     * path, depending on whether Cloudinary is configured.
     */
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // --- Cloudinary path (production) ---
        if (cloudName != null && !cloudName.isBlank()) {
            try {
                Map<?, ?> result = cloudinary.uploader().upload(
                        file.getBytes(),
                        ObjectUtils.asMap(
                                "folder",        "footballhub/" + folder,
                                "resource_type", "image",
                                "overwrite",     true
                        )
                );
                String url = (String) result.get("secure_url");
                if (url != null) return url;
            } catch (Exception e) {
                System.err.println("[CloudinaryService] Cloudinary upload failed, falling back to local: " + e.getMessage());
            }
        }

        // --- Local disk fallback (development / no credentials) ---
        try {
            Path dir = Paths.get(uploadDirProperty).toAbsolutePath();
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String fileName = folder + "_" + UUID.randomUUID() + ".png";
            Path dest = dir.resolve(fileName);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + fileName;
        } catch (Exception e) {
            System.err.println("[CloudinaryService] Local fallback also failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete an image from Cloudinary by its public ID.
     * Safe to call even if Cloudinary is not configured.
     */
    public void deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank() || cloudName == null || cloudName.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            System.err.println("[CloudinaryService] Delete failed: " + e.getMessage());
        }
    }
}
