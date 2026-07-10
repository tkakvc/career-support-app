package com.example.backend.service;

import com.example.backend.dto.response.AttachmentDownload;
import com.example.backend.dto.response.AttachmentResponse;
import com.example.backend.entity.Attachment;
import com.example.backend.entity.LearningRecord;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.AttachmentRepository;
import com.example.backend.repository.LearningRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    // サーバーのメモリ圧迫を防ぐため 10MB を上限とする
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    // ストレージの無制限消費を防ぐため学習記録1件あたり 10 ファイルを上限とする
    private static final int MAX_ATTACHMENT_COUNT = 10;

    private final AttachmentRepository attachmentRepository;
    private final LearningRecordRepository learningRecordRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getList(UUID userId, UUID learningRecordId) {
        verifyOwnership(userId, learningRecordId);
        return attachmentRepository.findByLearningRecordId(learningRecordId)
                .stream().map(AttachmentResponse::new).toList();
    }

    @Transactional
    public AttachmentResponse upload(UUID userId, UUID learningRecordId, MultipartFile file) {
        verifyOwnership(userId, learningRecordId);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ファイルが添付されていません");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ファイルサイズは10MB以下にしてください");
        }
        if (attachmentRepository.countByLearningRecordId(learningRecordId) >= MAX_ATTACHMENT_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "添付ファイルは1件の学習記録につき10ファイルまでです");
        }

        // DB 保存より前に attachmentId を確定させて storageKey を組み立てる（DB 書き込みを1回で済ませるため）
        UUID attachmentId = UUID.randomUUID();
        String storageKey = buildStorageKey(userId, attachmentId, file.getOriginalFilename());

        Attachment attachment = Attachment.builder()
                .id(attachmentId)
                .learningRecordId(learningRecordId)
                .fileName(file.getOriginalFilename())
                .storageKey(storageKey)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .build();

        attachmentRepository.save(attachment);

        try {
            storageService.upload(storageKey, file);
        } catch (IOException e) {
            // RuntimeException を throw すると @Transactional がロールバックするため save() も取り消される
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ファイルの保存に失敗しました");
        }

        return new AttachmentResponse(attachment);
    }

    public AttachmentDownload download(UUID userId, UUID learningRecordId, UUID attachmentId) {
        verifyOwnership(userId, learningRecordId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("添付ファイルが見つかりません"));

        // attachmentId が URL の learningRecordId に紐づいているか確認（他記録への横断アクセスを防ぐ）
        if (!attachment.getLearningRecordId().equals(learningRecordId)) {
            throw new ResourceNotFoundException("添付ファイルが見つかりません");
        }

        try {
            byte[] bytes = storageService.download(attachment.getStorageKey());
            return new AttachmentDownload(bytes, attachment.getContentType(), attachment.getFileName(), attachment.getFileSize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ファイルのダウンロードに失敗しました");
        }
    }

    @Transactional
    public void delete(UUID userId, UUID learningRecordId, UUID attachmentId) {
        verifyOwnership(userId, learningRecordId);

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("添付ファイルが見つかりません"));

        if (!attachment.getLearningRecordId().equals(learningRecordId)) {
            throw new ResourceNotFoundException("添付ファイルが見つかりません");
        }

        // ストレージ → DB の順で削除する。DB を先に消すとストレージに孤立ファイルが残るリスクがある
        try {
            storageService.delete(attachment.getStorageKey());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ファイルの削除に失敗しました");
        }

        attachmentRepository.delete(attachment);
    }

    // 学習記録削除時に呼ぶ。ストレージ削除はベストエフォート（失敗してもDB削除は続行する）
    @Transactional
    public void deleteAllByLearningRecordId(UUID learningRecordId) {
        List<Attachment> attachments = attachmentRepository.findByLearningRecordId(learningRecordId);
        for (Attachment attachment : attachments) {
            try {
                storageService.delete(attachment.getStorageKey());
            } catch (IOException ignored) {
            }
        }
        attachmentRepository.deleteAll(attachments);
    }

    private void verifyOwnership(UUID userId, UUID learningRecordId) {
        LearningRecord record = learningRecordRepository.findById(learningRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("学習記録が見つかりません"));
        if (!record.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの学習記録にはアクセスできません");
        }
    }

    // パスに attachmentId（UUID）を含めることで同名ファイルの上書きを防ぐ
    private String buildStorageKey(UUID userId, UUID attachmentId, String fileName) {
        return String.format("attachments/%s/%s/%s", userId, attachmentId, fileName);
    }
}
