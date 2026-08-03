package ai.riskvision.graveyard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Secure file storage service for RiskVision AI.
 *
 * Security hardening:
 *  - Whitelist-based extension validation (configurable via env var)
 *  - Path traversal prevention (resolved paths checked to be inside storage root)
 *  - File size limit enforced (configurable)
 *  - Category sanitisation (alphanumeric + dash/underscore only)
 *  - Randomised stored filename (UUID prefix) prevents guessing
 *  - Virus scan hook preserved for future integration
 */
@Service
@Slf4j
public class FileService {

    private final Path fileStorageLocation;
    private final Set<String> allowedExtensions;
    private final long maxFileSizeBytes;

    public FileService(
        @Value("${file.upload-dir:./uploads}") String uploadDir,
        @Value("${file.allowed-extensions:pdf,csv,xlsx,xls,json,txt,png,jpg,jpeg}") String allowedExtensionsCsv,
        @Value("${file.max-size-bytes:10485760}") long maxFileSizeBytes // 10 MB default
    ) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedExtensions = Arrays.stream(allowedExtensionsCsv.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());

        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("FileService initialised. Storage root: {}", this.fileStorageLocation);
        } catch (Exception ex) {
            log.error("Could not create storage directory: {}", uploadDir, ex);
        }
    }

    /**
     * Validates and stores an uploaded file.
     *
     * @param file     the uploaded file
     * @param category storage category (sanitised to alphanumeric + dash/underscore)
     * @return relative path within the upload root (category/storedFileName)
     */
    public String storeFile(MultipartFile file, String category) {
        // ── Size validation ────────────────────────────────────────────────
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                "File size (" + file.getSize() + " bytes) exceeds the maximum allowed size (" +
                maxFileSizeBytes + " bytes).");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        // ── Filename sanitisation ──────────────────────────────────────────
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("File name is missing.");
        }
        String cleanName = StringUtils.cleanPath(originalFilename);

        // Path traversal check
        if (cleanName.contains("..") || cleanName.contains("/") || cleanName.contains("\\")) {
            throw new SecurityException("File name contains invalid characters: " + cleanName);
        }

        // ── Extension whitelist ────────────────────────────────────────────
        String extension = "";
        int dotIdx = cleanName.lastIndexOf('.');
        if (dotIdx > 0) {
            extension = cleanName.substring(dotIdx + 1).toLowerCase();
        }
        if (extension.isEmpty() || !allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(
                "File type '." + extension + "' is not allowed. " +
                "Permitted extensions: " + allowedExtensions);
        }

        // ── Category sanitisation ──────────────────────────────────────────
        String safeCategory = sanitiseCategory(category);

        // ── Virus scan hook ────────────────────────────────────────────────
        if (!scanForViruses(file)) {
            throw new SecurityException("Virus scan failed for file: " + cleanName);
        }

        // ── Store with randomised name ─────────────────────────────────────
        String storedFileName = UUID.randomUUID() + "_" + System.currentTimeMillis() + "." + extension;

        try {
            Path targetDir = this.fileStorageLocation.resolve(safeCategory).normalize();

            // Ensure target dir is inside storage root (prevent category path traversal)
            if (!targetDir.startsWith(this.fileStorageLocation)) {
                throw new SecurityException("Category path traversal detected: " + category);
            }

            Files.createDirectories(targetDir);
            Path filePath = targetDir.resolve(storedFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File stored: category={} originalName={} storedAs={} size={}",
                safeCategory, cleanName, storedFileName, file.getSize());
            return safeCategory + "/" + storedFileName;
        } catch (SecurityException e) {
            throw e;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file '" + cleanName + "'. Please try again.", ex);
        }
    }

    /**
     * Loads a stored file as a Spring {@link Resource}.
     * Path traversal in category/fileName is prevented by normalisation check.
     */
    public Resource loadFileAsResource(String category, String fileName) {
        try {
            Path filePath = this.fileStorageLocation
                .resolve(sanitiseCategory(category))
                .resolve(StringUtils.cleanPath(fileName))
                .normalize();

            // Ensure the resolved path is inside the storage root
            if (!filePath.startsWith(this.fileStorageLocation)) {
                throw new SecurityException("Path traversal detected in file download request.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("File not found: " + fileName);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("File not found: " + fileName, ex);
        }
    }

    /**
     * Deletes a stored file.
     * Path traversal in category/fileName is prevented by normalisation check.
     */
    public boolean deleteFile(String category, String fileName) {
        try {
            Path filePath = this.fileStorageLocation
                .resolve(sanitiseCategory(category))
                .resolve(StringUtils.cleanPath(fileName))
                .normalize();

            if (!filePath.startsWith(this.fileStorageLocation)) {
                log.warn("Path traversal attempt blocked in deleteFile: category={} fileName={}",
                    category, fileName);
                return false;
            }

            return Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            log.error("Failed to delete file: category={} fileName={}", category, fileName, ex);
            return false;
        }
    }

    /**
     * Sanitises a category name to alphanumeric, dash, and underscore characters only.
     * Prevents directory traversal via category parameter.
     */
    private String sanitiseCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "misc";
        }
        // Allow only safe characters
        String safe = category.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        return safe.isEmpty() ? "misc" : safe;
    }

    /**
     * Virus scanning hook/stub.
     * Integrate ClamAV or an external scanning service here.
     * Replace the stub body with a real scan when deploying to production.
     */
    private boolean scanForViruses(MultipartFile file) {
        // TODO: Integrate ClamAV or similar scanner
        log.info("Virus scan (stub): file='{}' size={}B — PASS",
            file.getOriginalFilename(), file.getSize());
        return true;
    }
}
