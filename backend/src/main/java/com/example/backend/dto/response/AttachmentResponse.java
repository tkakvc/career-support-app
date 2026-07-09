package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】レスポンス DTO のフィールド定義・Lombok の @Getter の書き方
// 【面接で説明できるようにする】なぜ Entity をそのまま返さず Response クラスに変換するか
//   → Entity には storageKey（ファイルの保存場所のパス）など、クライアントに見せたくない内部情報がある。
//     Response クラスに変換することで、返す情報を明示的に制御できる。
//     Entity を直接返すと内部構造の変更がAPIの仕様変更に直結してしまうため、層を分けている。
// ============================================================
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
