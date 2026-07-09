package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "learning_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    // 外部キー: users テーブルの id（UUID）を参照する。NOT NULL。
    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    // 学習日。日付のみ（時刻なし）を保存する。NOT NULL。
    @Column(nullable = false)
    private LocalDate date;

    // 学習内容。最大 2000 文字。NOT NULL。TEXT 型で保存。
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 学習時間（分）。NOT NULL。
    @Column(nullable = false)
    private Integer duration;

    // 多対多リレーション: learning_record_tags を中間テーブルとして Tag と関連付ける
    // Cascade は指定しない。学習記録を削除してもタグ自体は残す（中間テーブルの紐付け行だけ消える）。
    @ManyToMany
    @JoinTable(
        name = "learning_record_tags",
        // joinColumns:        このクラス（LearningRecord）を指す FK カラム名
        joinColumns = @JoinColumn(name = "learning_record_id"),
        // inverseJoinColumns: 相手側（Tag）を指す FK カラム名
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    // @ManyToMany はデフォルト LAZY。
    // getTags() を呼ぶまで SQL は実行されず、tags フィールドには Hibernate のプロキシ（中身のない代替オブジェクト）が入っている。
    // getTags() を呼んだ瞬間にプロキシが SQL を発行して本物のリストに差し替わる。
    // → id / content など他のフィールドは最初のSQLで取得済み。tags だけが遅延取得。
    // 一覧取得で tags も返す場合は Repository で JOIN FETCH を書かないと N+1 が発生する。
    @Builder.Default
    private List<Tag> tags = new ArrayList<>();

    // レコード INSERT 時に自動で現在日時をセットする
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
