package com.osgateway.ussd.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Component
public class OperatorLogoStorage {

    private final Path root;

    public OperatorLogoStorage(@Value("${osgateway.ussd.logo-storage:./data/operator-logos}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create operator logo storage: " + this.root, e);
        }
    }

    public StoredFile store(Long operatorId, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "logo.bin";
        String ext = extension(original);
        String key = operatorId + "/" + UUID.randomUUID() + ext;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid storage path");
        }
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        return new StoredFile(key, original, contentType, file.getSize());
    }

    public Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    public void deleteQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0) return "";
        String ext = name.substring(i).toLowerCase(Locale.ROOT);
        return ext.matches("\\.(png|jpe?g|gif|webp|svg)") ? ext : "";
    }

    public record StoredFile(String storageKey, String fileName, String contentType, long sizeBytes) {}
}
