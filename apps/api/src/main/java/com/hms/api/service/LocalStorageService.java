package com.hms.api.service;

import com.hms.api.domain.FileRecord;
import com.hms.api.domain.User;
import com.hms.api.exception.BadRequestException;
import com.hms.api.exception.ResourceNotFoundException;
import com.hms.api.repository.FileRecordRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    private final Path rootLocation;
    private final FileRecordRepository fileRecordRepository;

    public LocalStorageService(
            @Value("${app.storage.local-dir:./uploads}") String uploadDir,
            FileRecordRepository fileRecordRepository
    ) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.fileRecordRepository = fileRecordRepository;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            log.error("Could not initialize storage directory", e);
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    @Override
    @Transactional
    public FileRecord store(MultipartFile file, boolean isPublic, User uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum permitted limit of 5 MB");
        }

        // Verify file content by inspecting magic bytes
        String detectedContentType = detectAllowedContentType(file);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "unnamed_file";
        } else {
            // Strip any directory path components to prevent path traversal
            originalFilename = Paths.get(originalFilename).getFileName().toString();
        }

        String extension = getExtensionForContentType(detectedContentType);
        String randomStoredName = UUID.randomUUID().toString() + extension;

        try {
            Path destinationFile = this.rootLocation.resolve(randomStoredName).normalize();
            // Path traversal guard
            if (!destinationFile.getParent().equals(this.rootLocation)) {
                throw new BadRequestException("Cannot store file outside target directory");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            FileRecord fileRecord = new FileRecord(
                UUID.randomUUID().toString(),
                originalFilename,
                randomStoredName,
                detectedContentType,
                file.getSize(),
                isPublic,
                uploadedBy
            );

            return fileRecordRepository.save(fileRecord);
        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public Resource loadAsResource(String storedName) {
        try {
            // Prevent path traversal in filename parameter
            if (storedName == null || storedName.contains("..") || storedName.contains("/") || storedName.contains("\\")) {
                throw new BadRequestException("Invalid stored file name");
            }

            Path file = this.rootLocation.resolve(storedName).normalize();
            if (!file.startsWith(this.rootLocation)) {
                throw new BadRequestException("Cannot access file outside current directory");
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("File not found with name: " + storedName);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found with name: " + storedName);
        }
    }

    @Override
    public FileRecord getFileRecord(String storedName) {
        return fileRecordRepository.findByStoredName(storedName)
                .orElseThrow(() -> new ResourceNotFoundException("File record not found for: " + storedName));
    }

    @Override
    @Transactional
    public void delete(String storedName) {
        FileRecord record = getFileRecord(storedName);
        try {
            Path file = this.rootLocation.resolve(storedName).normalize();
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete physical file from disk: {}", storedName);
        }
        fileRecordRepository.delete(record);
    }

    /**
     * Inspects magic bytes from the first 12 bytes of the file stream.
     * Allowed formats: JPEG, PNG, WebP, PDF.
     */
    private String detectAllowedContentType(MultipartFile file) {
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            throw new BadRequestException("Could not read file stream for type verification");
        }

        if (bytesRead < 4) {
            throw new BadRequestException("File too small to determine content type");
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytesRead >= 8 &&
            (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 &&
            (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47 &&
            (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A &&
            (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return "image/png";
        }

        // PDF: %PDF (25 50 44 46)
        if ((header[0] & 0xFF) == 0x25 && (header[1] & 0xFF) == 0x50 &&
            (header[2] & 0xFF) == 0x44 && (header[3] & 0xFF) == 0x46) {
            return "application/pdf";
        }

        // WebP: RIFF (bytes 0..3) ... WEBP (bytes 8..11)
        if (bytesRead >= 12 &&
            (header[0] & 0xFF) == 0x52 && (header[1] & 0xFF) == 0x49 &&
            (header[2] & 0xFF) == 0x46 && (header[3] & 0xFF) == 0x46 &&
            (header[8] & 0xFF) == 0x57 && (header[9] & 0xFF) == 0x45 &&
            (header[10] & 0xFF) == 0x42 && (header[11] & 0xFF) == 0x50) {
            return "image/webp";
        }

        throw new BadRequestException("Invalid file type: only JPEG, PNG, WebP, and PDF files are allowed");
    }

    private String getExtensionForContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }
}

