package com.example.backend.entity;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】@Entity / @Table / @Column などの JPA アノテーションの書き方
//   → @Entity は「このクラスが DB のテーブルと対応する」という宣言。
//   → @Column の columnDefinition / updatable / nullable などの属性は覚えなくていい。
//   → Lombok の @Builder / @Getter / @Setter / @NoArgsConstructor / @AllArgsConstructor は覚えなくていい。
// 【面接で説明できるようにする】なぜ Entity クラスを作るか（JPA を使う理由）
//   → JPA を使うと「テーブルとクラスを対応させる」ことで、SQL を書かずに
//     Java のオブジェクト操作（save / findById / delete）だけで DB にアクセスできる。
//     ただし、複雑なクエリ（JOIN・集計）は JPQL や @Query で書く必要がある。
// ============================================================
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "learning_record_attachments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "learning_record_id", columnDefinition = "uuid", nullable = false)
    private UUID learningRecordId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
