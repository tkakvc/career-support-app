package com.example.backend.repository;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ JpaSpecificationExecutor を extends するか（Specification パターン）
//   → 検索条件（タグ・日付範囲・キーワード）が複数あり、任意の組み合わせで絞り込む必要がある。
//     条件をメソッド名だけで表現すると組み合わせの数だけメソッドが増えてしまう。
//     Specification パターンを使うと「条件を and() で動的に積み上げる」書き方ができる。
// 【面接で説明できるようにする】なぜ @EntityGraph を使うか（N+1 問題の解決）
//   → tags は @ManyToMany でデフォルト LAZY。findAll() するだけだと tags が取れず、
//     後で getTags() を呼ぶたびに1件ずつ追加 SQL が走る（N+1 問題）。
//     @EntityGraph(attributePaths = "tags") で JOIN して一括取得することで N+1 を解消する。
// 【AI任せでOK】@Override / @EntityGraph / @Query / @Param などのアノテーションの書き方
// ============================================================
import com.example.backend.entity.LearningRecord;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningRecordRepository extends JpaRepository<LearningRecord, UUID>, JpaSpecificationExecutor<LearningRecord> {

    // @EntityGraph: Specification で絞り込んだ結果に対して tags を JOIN FETCH で一緒に取得する
    // これがないと tags にアクセスするたびに SQL が発行されて N+1 が発生する
    @Override
    @EntityGraph(attributePaths = "tags")
    List<LearningRecord> findAll(@Nullable Specification<LearningRecord> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = "tags")
    Optional<LearningRecord> findById(UUID id);

    // AI学習提案用：直近30件をタグ込みで取得する
    //
    // DISTINCT の注意点: 普通のSQLのDISTINCTは列の値を比較するが、
    // ここでの DISTINCT r はエンティティ(id)単位で重複除去する特殊な意味になる。
    // 1件のrがタグを2つ持つ場合、LEFT JOIN FETCH直後は
    //   (id=R1, tag="Java") (id=R1, tag="Spring")  の2行になるが、
    // DISTINCT r により最終的なJavaのListにはR1が1件だけ入り、
    // その tags フィールドに ["Java","Spring"] の両方が入る。
    @Query("""
            SELECT DISTINCT r
            FROM LearningRecord r
                LEFT JOIN FETCH r.tags
            WHERE r.userId = :userId
            ORDER BY r.date DESC
            LIMIT 30
            """)
    List<LearningRecord> findTop30WithTagsByUserId(@Param("userId") UUID userId);
}
