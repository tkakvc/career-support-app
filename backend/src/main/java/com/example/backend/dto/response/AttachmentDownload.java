package com.example.backend.dto.response;

public record AttachmentDownload(byte[] bytes, String contentType, String fileName, long fileSize) {
}
