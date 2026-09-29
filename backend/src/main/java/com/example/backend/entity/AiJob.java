package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiJobStatus status;

    // 参考資料生成に必要な材料（学習記録+interestを整形したJSON。AiService.ReferenceJobInput参照）
    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    // 成功時の結果（AiReferenceResponse相当のJSON文字列）。未完了時はnull
    @Column(columnDefinition = "TEXT")
    private String result;

    // 失敗時のメッセージ。成功時はnull
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AiJob pending(UUID userId, AiJobType type, String input) {
        AiJob job = new AiJob();
        job.setUserId(userId);
        job.setType(type);
        job.setStatus(AiJobStatus.PENDING);
        job.setInput(input);
        return job;
    }

    public void markProcessing() {
        this.status = AiJobStatus.PROCESSING;
    }

    public void markDone(String result) {
        this.status = AiJobStatus.DONE;
        this.result = result;
    }

    public void markFailed(String errorMessage) {
        this.status = AiJobStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
