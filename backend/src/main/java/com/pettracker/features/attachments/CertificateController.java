package com.pettracker.features.attachments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.pettracker.features.auth.AuthTokenFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/vaccinations/{vaccinationId}/certificate")
public class CertificateController {

    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final Path storageRoot;

    public CertificateController(
            JdbcTemplate jdbc,
            @Value("${app.storage.certificates-dir}") String directory
    ) throws IOException {
        this.jdbc = jdbc;
        this.storageRoot = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    public record CertificateMetadata(
            UUID vaccinationId,
            String originalFilename,
            String contentType,
            long sizeBytes
    ) {}

    private record StoredCertificate(
            String storageKey,
            CertificateMetadata metadata
    ) {}

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CertificateMetadata upload(
            @PathVariable UUID vaccinationId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) throws IOException {
        requireOwnership(vaccinationId, request);

        if (file.isEmpty() || file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a non-empty file no larger than 5 MB."
            );
        }

        byte[] bytes = file.getBytes();
        String contentType = detectType(bytes);
        String filename = safeFilename(file.getOriginalFilename());

        List<String> previousKeys = jdbc.queryForList(
                """
                SELECT storage_key
                FROM vaccination_certificates
                WHERE vaccination_id = ?
                """,
                String.class,
                vaccinationId
        );

        // The uploaded filename never determines the filesystem path.
        String storageKey = UUID.randomUUID() + ".bin";
        Path destination = storageRoot.resolve(storageKey);

        try {
            Files.write(destination, bytes);

            jdbc.update(
                    """
                    INSERT INTO vaccination_certificates (
                        vaccination_id, storage_key, original_filename,
                        content_type, size_bytes
                    )
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (vaccination_id) DO UPDATE SET
                        storage_key = EXCLUDED.storage_key,
                        original_filename = EXCLUDED.original_filename,
                        content_type = EXCLUDED.content_type,
                        size_bytes = EXCLUDED.size_bytes,
                        uploaded_at = CURRENT_TIMESTAMP
                    """,
                    vaccinationId,
                    storageKey,
                    filename,
                    contentType,
                    bytes.length
            );
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }

        // Metadata now points to the new file. Old-file cleanup is best effort.
        for (String previousKey : previousKeys) {
            try {
                Files.deleteIfExists(storageRoot.resolve(previousKey));
            } catch (IOException ignored) {
                // A maintenance job can remove unreferenced files later.
            }
        }

        return new CertificateMetadata(
                vaccinationId, filename, contentType, bytes.length
        );
    }

    @GetMapping("/metadata")
    public CertificateMetadata metadata(
            @PathVariable UUID vaccinationId,
            HttpServletRequest request
    ) {
        requireOwnership(vaccinationId, request);
        return findCertificate(vaccinationId).metadata();
    }

    @GetMapping
    public ResponseEntity<Resource> download(
            @PathVariable UUID vaccinationId,
            HttpServletRequest request
    ) {
        requireOwnership(vaccinationId, request);

        StoredCertificate certificate = findCertificate(vaccinationId);
        Path path = storageRoot.resolve(certificate.storageKey()).normalize();

        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        CertificateMetadata metadata = certificate.metadata();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        metadata.originalFilename(),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .body(new FileSystemResource(path));
    }

    private void requireOwnership(
            UUID vaccinationId,
            HttpServletRequest request
    ) {
        Object authenticatedOwner =
                request.getAttribute(AuthTokenFilter.OWNER_ID);

        if (!(authenticatedOwner instanceof UUID ownerId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Boolean owned = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM vaccination_records v
                    JOIN pets p ON p.id = v.pet_id
                    WHERE v.id = ?
                      AND p.owner_id = ?
                      AND p.archived_at IS NULL
                )
                """,
                Boolean.class,
                vaccinationId,
                ownerId
        );

        if (!Boolean.TRUE.equals(owned)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private StoredCertificate findCertificate(UUID vaccinationId) {
        return jdbc.query(
                """
                SELECT storage_key, original_filename, content_type, size_bytes
                FROM vaccination_certificates
                WHERE vaccination_id = ?
                """,
                (rs, row) -> new StoredCertificate(
                        rs.getString("storage_key"),
                        new CertificateMetadata(
                                vaccinationId,
                                rs.getString("original_filename"),
                                rs.getString("content_type"),
                                rs.getLong("size_bytes")
                        )
                ),
                vaccinationId
        ).stream().findFirst().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND)
        );
    }

    private static String safeFilename(String original) {
        String name = original == null
                ? "certificate"
                : original.replace('\\', '/');

        name = name.substring(name.lastIndexOf('/') + 1)
                .replaceAll("\\p{Cntrl}", "")
                .strip();

        if (name.isBlank()) name = "certificate";
        return name.substring(0, Math.min(name.length(), 255));
    }

    private static String detectType(byte[] bytes) {
        return CertificateFileType.detect(bytes);
    }
}

