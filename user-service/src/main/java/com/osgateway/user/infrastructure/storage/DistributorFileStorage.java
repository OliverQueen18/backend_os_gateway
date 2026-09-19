package com.osgateway.user.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class DistributorFileStorage {

    private final Path root;

    public DistributorFileStorage(
            @Value("${osgateway.uploads.dir:./uploads}") String uploadsDir) throws IOException {
        Path preferred = Path.of(uploadsDir, "distributors").toAbsolutePath().normalize();
        this.root = ensureWritable(preferred);
    }

    private static Path ensureWritable(Path preferred) throws IOException {
        try {
            Files.createDirectories(preferred);
            Path probe = preferred.resolve(".write-test");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return preferred;
        } catch (Exception ex) {
            Path fallback = Path.of(System.getProperty("java.io.tmpdir"), "osgateway-uploads", "distributors")
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(fallback);
            return fallback;
        }
    }

    public StoredFile store(Long distributorId, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = distributorId + "/" + UUID.randomUUID() + "_" + safeName;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid storage path");
        }
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(key, safeName, file.getContentType(), file.getSize());
    }

    public Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (Exception ignored) {
        }
    }

    public record StoredFile(String storageKey, String fileName, String contentType, long sizeBytes) {}
}
