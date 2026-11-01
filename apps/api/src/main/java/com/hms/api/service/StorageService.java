package com.hms.api.service;

import com.hms.api.domain.FileRecord;
import com.hms.api.domain.User;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    FileRecord store(MultipartFile file, boolean isPublic, User uploadedBy);
    Resource loadAsResource(String storedName);
    FileRecord getFileRecord(String storedName);
    void delete(String storedName);
}
