package com.example.backend.repository;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Specification パターンを使うか
//   → 「タグで絞る」「日付範囲で絞る」「キーワードで絞る」という条件が独立している。
//     Specification を使うと各条件を1メソッドとして定義して、呼び出し側で and() で組み合わせられる。
//     Service に if-else でクエリを組み立てるより、条件の追加・削除がしやすい（開放閉鎖原則）。
// 【面接で説明できるようにする】なぜ JPA / Specification を使うか
//   → JPA は SQL を自分で書かず、cb.equal() などの Java コードから Hibernate が SQL を自動生成する
//     （MyBatis は逆に SQL を自分で直接書く）。この「誰がSQLを書くか」が本質の違い。
//     コンパイル時に構文ミスを検出できるのは、その副次的なメリットにすぎない。
// 【AI任せでOK】(root, query, cb) -> ... のラムダ式の書き方・cb.equal / cb.like / cb.greaterThanOrEqualTo の使い方
// ============================================================
import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

// Specification パターン：検索条件をオブジェクトとして表現し、and() で自由に組み合わせられるようにする
// 条件が増えても if-else を増やさず、ここに1メソッド追加するだけで済む
public class LearningRecordSpecification {

    public static Specification<LearningRecord> hasUserId(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<LearningRecord> hasTagName(String tagName) {
        return (root, query, cb) -> {
            // 各行がSQLのどの部分になるかの対応（1行 = 1箇所とは限らない）:
            //
            //   root                                        → FROM learning_records lr
            //   （root はこの引数として最初から渡されてくる「検索対象」。コード上に書かなくてもFROMに対応する）
            //
            //   root.join("tags", INNER)                    → INNER JOIN learning_record_tags lrt ON ...
            //                                                   INNER JOIN tags t ON ...
            //   （tagsは@ManyToManyで中間テーブル経由のため、joinを1回呼ぶだけで中間テーブルとtagsテーブル、
            //     2つのJOINがまとめて作られる。呼び出し1回とJOIN行数は一致しない）
            //
            //   cb.equal(tagJoin.get("name"), tagName)      → WHERE t.name = ?
            //   （? の部分に、引数 tagName の値が入る）
            //
            //   query.distinct(true)                        → SELECT の直後に DISTINCT が付く
            //
            // 組み立てられるSQL全体:
            //   SELECT DISTINCT lr.* FROM learning_records lr
            //   INNER JOIN learning_record_tags lrt ON lr.id = lrt.learning_record_id
            //   INNER JOIN tags t ON lrt.tag_id = t.id
            //   WHERE t.name = ?
            //
            // distinct が必要な理由: 一覧取得側(findAll)が @EntityGraph(attributePaths="tags") で
            // 該当レコードの全タグを一緒にJOINしてくるため、タグを複数持つレコードは行が重複しうる。
            query.distinct(true);
            Join<LearningRecord, Tag> tagJoin = root.join("tags", JoinType.INNER);
            return cb.equal(tagJoin.get("name"), tagName);
        };
    }

    public static Specification<LearningRecord> fromDate(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<LearningRecord> toDate(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to);
    }

    // root.get("content") で content カラムを指定し、cb.like(カラム, パターン) で LIKE 条件を作る。
    // "%" は「任意の文字列」を表すワイルドカード。keyword="Java" なら
    // WHERE content LIKE '%Java%' となり、content のどこかに"Java"を含む行にヒットする。
    public static Specification<LearningRecord> contentContains(String keyword) {
        return (root, query, cb) -> cb.like(root.get("content"), "%" + keyword + "%");
    }
}
