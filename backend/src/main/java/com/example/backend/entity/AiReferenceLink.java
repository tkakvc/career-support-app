package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// AiReference（参考資料）1件に対する参考リンク（1対多）。Web検索結果の title/url をそのまま保存する
@Entity
@Table(name = "ai_reference_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiReferenceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_reference_id", nullable = false)
    private AiReference reference;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(length = 255)
    private String title;
}
