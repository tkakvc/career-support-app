package com.example.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {

    void upload(String storageKey, MultipartFile file) throws IOException;

    byte[] download(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
