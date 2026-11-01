package com.hms.api.web;

import com.hms.api.annotation.Audited;
import com.hms.api.domain.FileRecord;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.dto.FileResponseDto;
import com.hms.api.dto.MessageResponse;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.SecurityUser;
import com.hms.api.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final StorageService storageService;
    private final UserRepository userRepository;

    public FileController(StorageService storageService, UserRepository userRepository) {
        this.storageService = storageService;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    @Audited(action = "UPLOAD_FILE", entityType = "FILE")
    public ResponseEntity<FileResponseDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        User user = null;
        if (currentUser != null) {
            user = userRepository.findById(currentUser.getId()).orElse(null);
        }

        FileRecord record = storageService.store(file, isPublic, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(FileResponseDto.from(record));
    }

    @GetMapping("/{storedName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String storedName) {
        FileRecord record = storageService.getFileRecord(storedName);

        if (!record.isPublic()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                throw new BadCredentialsException("Full authentication is required to access this file");
            }

            SecurityUser user = (SecurityUser) auth.getPrincipal();
            boolean isOwner = record.getUploadedBy() != null && record.getUploadedBy().getId().equals(user.getId());
            boolean isAdmin = user.getRole() == Role.ADMIN;

            if (!isAdmin && !isOwner) {
                throw new AccessDeniedException("Access denied: you do not have permission to access this file");
            }
        }

        Resource resource = storageService.loadAsResource(storedName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + record.getOriginalName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{storedName}")
    @PreAuthorize("isAuthenticated()")
    @Audited(action = "DELETE_FILE", entityType = "FILE", entityIdParam = "storedName")
    public ResponseEntity<MessageResponse> deleteFile(
            @PathVariable String storedName,
            @AuthenticationPrincipal SecurityUser currentUser
    ) {
        FileRecord record = storageService.getFileRecord(storedName);
        boolean isOwner = record.getUploadedBy() != null && record.getUploadedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("Access denied: you do not have permission to delete this file");
        }

        storageService.delete(storedName);
        return ResponseEntity.ok(new MessageResponse("File deleted successfully"));
    }
}
