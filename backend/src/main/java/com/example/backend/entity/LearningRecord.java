package com.example.backend.entity;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】@Entity / @Table / @Column / @ManyToMany / @JoinTable などの JPA アノテーションの書き方
//   → Lombok の @Builder / @Getter / @Setter などは覚えなくていい。
// 【面接で説明できるようにする】なぜ @ManyToMany と中間テーブル（learning_record_tags）を使うか
//   → 1件の学習記録は複数のタグを持てる、1つのタグは複数の学習記録に付けられる。
//     この「多対多」の関係を DB で表現するには中間テーブルが必要。
//     @ManyToMany + @JoinTable で JPA がその中間テーブルへの操作（挿入・削除）を自動でやってくれる。
// 【面接で説明できるようにする】なぜ tags が LAZY ローディングになるか（N+1 問題との関係）
//   → @ManyToMany はデフォルトで LAZY（アクセスするまで SQL を発行しない）。
//     一覧取得（実体は LearningRecordRepository.findAll(spec, sort)。
//     呼び出し元: LearningRecordController.getList() → LearningRecordService.getLearningRecords()）
//     で tags にアクセスするとレコードの数だけ追加の SQL が走る（N+1 問題）。
//
//     具体例: 学習記録が10件あるとき、素朴に書くと
//       1. 学習記録10件を取得する SQL … 1回
//       2. 10件それぞれについて record.getTags() を呼ぶたびに
//          「このrecordのtagsをちょうだい」という SQL … 10回
//       合計 1 + 10 = 11回 SQL が走る。これが N+1 問題（Nは件数、+1は最初の1回）。
//
//     「これを防ぐ」とは、上の11回を1回のJOINにまとめること。やり方は2つある。
//
//     @EntityGraph とは何か:
//     Entity（実体）+ Graph（つながり図）で「このEntityとどのフィールドをつなげて取得するか」を
//     表す設計図、という意味の単語。実体は JPA が用意しているアノテーションで、
//     「本来は LAZY で後から取ってくるはずのフィールドを、最初から JOIN して一緒に取ってきて」
//     とHibernateに指示するもの。
//
//     (1) @EntityGraph で書く例（LearningRecordRepository.java:34-36）:
//       @Override
//       @EntityGraph(attributePaths = "tags")
//       List<LearningRecord> findAll(@Nullable Specification<LearningRecord> spec, Sort sort);
//     attributePaths = "tags" は「tags フィールドも一緒にJOINして取ってきて」という指定。
//     これにより10件のSQLが1回で済む（tagsもまとめて取れる）。
//
//     JOIN FETCH とは何か:
//     JOIN は SQL の単語で「複数のテーブルを条件でつなげて1つの結果にする」処理
//     （例: learning_records テーブルと tags テーブルを learning_record_tags 経由でつなげる）。
//     FETCH は英語で「取ってくる」という意味。JPQL（JPAが使うSQLに似た言語）の中で
//     「JOINしたテーブルの中身も、プロキシ（空の代替オブジェクト）のままにせず
//     実際のデータとして一緒に取得する」ことを指示するキーワード。
//     JOIN だけだと「絞り込みには使うが tags の中身は LAZY のまま」になり、
//     JOIN FETCH と書くことで初めて tags の中身も同じ1回のSQLで取得される。
//
//     (2) JOIN FETCH で書く例（LearningRecordRepository.java:43）:
//       @Query("SELECT DISTINCT r FROM LearningRecord r LEFT JOIN FETCH r.tags WHERE r.userId = :userId ...")
//     "LEFT JOIN FETCH r.tags" が「r（LearningRecord）とtagsを1回のSQLでJOINして取得する」という指定。
//
//     違い: @EntityGraph は既存メソッド（findAllなど）にJOINを後付けするやり方。
//           JOIN FETCH は @Query で自分でSQLに近いクエリ（JPQL）を書くやり方。
// ============================================================
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
        // inverse とは英語で「逆・反対」という意味の単語。
        // @JoinTable は中間テーブル（learning_record_tags）の設定を、
        // このクラス（LearningRecord）側から見た視点で書く。
        // joinColumns        = 自分（LearningRecord）を指すFK列名 → learning_record_id
        // inverseJoinColumns = 自分の"逆"、つまり相手側（Tag）を指すFK列名 → tag_id
        //
        // 具体的なテーブルの中身の例（learning_record_tags）:
        //   | learning_record_id | tag_id     |
        //   |--------------------|------------|
        //   | (学習記録1のid)     | (タグAのid) |
        //   | (学習記録1のid)     | (タグBのid) |
        // → 1行目・2行目とも learning_record_id 列（joinColumns側）は同じ値、
        //   tag_id 列（inverseJoinColumns側）だけが違う値になっている。
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
    // @Builder.Default: Lombokの@Builderは、フィールドの初期値（= new ArrayList<>()）を
    // 無視する仕様のため、これを付けないと builder().build() で tags を null にしてしまう。
    // 例: LearningRecord.builder().content("Java勉強").build().getTags()
    //   → 付けない場合: null / 付ける場合: []（空リスト）
    @Builder.Default
    private List<Tag> tags = new ArrayList<>();

    // レコード INSERT 時に自動で現在日時をセットする
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
