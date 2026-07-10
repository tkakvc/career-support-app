package com.example.backend.controller;

import com.example.backend.dto.response.AttachmentDownload;
import com.example.backend.dto.response.AttachmentResponse;
import com.example.backend.dto.response.DeleteResponse;
import com.example.backend.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/learning-records/{learningRecordId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public List<AttachmentResponse> getList(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId) {
        return attachmentService.getList(userId, learningRecordId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId,
            @RequestParam MultipartFile file) {
        return attachmentService.upload(userId, learningRecordId, file);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId,
            @PathVariable UUID attachmentId) {
        AttachmentDownload dl = attachmentService.download(userId, learningRecordId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.fileName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(dl.fileSize()))
                .body(dl.bytes());
    }

    @DeleteMapping("/{attachmentId}")
    public DeleteResponse delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId,
            @PathVariable UUID attachmentId) {
        attachmentService.delete(userId, learningRecordId, attachmentId);
        return new DeleteResponse();
    }
}
