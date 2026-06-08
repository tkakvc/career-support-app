package com.example.backend.dto.response;

import com.example.backend.entity.Attachment;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class AttachmentResponse {

    private final UUID id;
    private final UUID learningRecordId;
    private final String fileName;
    private final String contentType;
    private final Long fileSize;
    private final LocalDateTime createdAt;

    public AttachmentResponse(Attachment attachment) {
        this.id = attachment.getId();
        this.learningRecordId = attachment.getLearningRecordId();
        this.fileName = attachment.getFileName();
        this.contentType = attachment.getContentType();
        this.fileSize = attachment.getFileSize();
        this.createdAt = attachment.getCreatedAt();
    }
}
