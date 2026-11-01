package com.hms.api.dto;

import com.hms.api.domain.FileRecord;
import java.time.LocalDateTime;

public record FileResponseDto(
    String id,
    String originalName,
    String storedName,
    String contentType,
    long size,
    boolean isPublic,
    String downloadUrl,
    LocalDateTime createdAt
) {
    public static FileResponseDto from(FileRecord file) {
        return new FileResponseDto(
            file.getId(),
            file.getOriginalName(),
            file.getStoredName(),
            file.getContentType(),
            file.getSize(),
            file.isPublic(),
            "/api/files/" + file.getStoredName(),
            file.getCreatedAt()
        );
    }
}

