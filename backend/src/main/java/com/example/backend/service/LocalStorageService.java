package com.example.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${storage.local.base-path:uploads}")
    private String basePath;

    @Override
    public void upload(String storageKey, MultipartFile file) throws IOException {
        Path path = Path.of(basePath, storageKey);
        Files.createDirectories(path.getParent());
        Files.write(path, file.getBytes());
    }

    @Override
    public byte[] download(String storageKey) throws IOException {
        Path path = Path.of(basePath, storageKey);
        if (!Files.exists(path)) {
            throw new IOException("ファイルが見つかりません: " + storageKey);
        }
        return Files.readAllBytes(path);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(Path.of(basePath, storageKey));
    }
}
