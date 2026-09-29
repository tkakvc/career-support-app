package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// AI情報収集・提案（F09）で生成した参考資料。学習記録とは独立したテーブルで、
// 学習記録への変換は「値をコピーして学習記録作成フォームへ引き継ぐ」だけであり、
// 外部キーでの関連は持たせない（docs/design.md の ai_references 参照）。
@Entity
@Table(name = "ai_references")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    // 生成時に入力された興味。未入力（学習記録のみから生成）の場合は null
    @Column(length = 200)
    private String interest;

    // 要約本文（HTML形式。OpenAIが生成したMarkdownをMarkdownHtmlConverterでHTMLに変換したもの）
    @Column(name = "summary_html", nullable = false, columnDefinition = "TEXT")
    private String summaryHtml;

    // 分類先のタグ。既存タグ（大文字小文字を無視した名前一致）が見つかった場合だけセットする。
    // タグの新規作成は必ずユーザーの明示的な操作（学習記録をつける等）を起点に行うべきで、
    // 非同期ジョブ（AiJobWorker→AiService.generateResult）がAIの判断だけで勝手にタグを
    // 作ってしまうのは、既存のタグ作成（TagService.createTag）が常にユーザー起点である
    // という他の実装との一貫性が無い。そのため一致しなかった場合はここではタグを作らず、
    // 提案名だけをsuggestedTagNameに保存する
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    // tagがnull（既存タグに一致しなかった）の場合のみ値が入る、AIが提案したタグ名
    @Column(name = "suggested_tag_name", length = 50)
    private String suggestedTagName;

    // 参考リンク（Web検索結果）。参考資料の削除に追従して削除してよい所有関係なのでcascade+orphanRemoval
    @OneToMany(mappedBy = "reference", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AiReferenceLink> links = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
