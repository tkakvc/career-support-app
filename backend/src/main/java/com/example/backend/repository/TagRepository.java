package com.example.backend.repository;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ命名規則（findByNameAndCreatedBy）と @Query を使い分けるか
//   → findByNameAndCreatedBy は「name と createdBy の完全一致」だけで、JOIN や OR が不要。
//     命名規則だけで表現できるシンプルなクエリは命名規則で書く方がコードが短く意図が伝わりやすい。
//     findVisibleTags は「type='default' OR (type='user' AND userId一致)」という OR 条件で
//     命名規則では表現できないため @Query を使う。適材適所で使い分けることが大切。
// 【AI任せでOK】@Query のテキストブロック（"""..."""）の書き方・@Param の使い方
// ============================================================
import com.example.backend.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    // ユーザーに表示するタグ一覧: default タグ全件 + 自分が作成した user タグ
    // @Query を使う理由: OR 条件（type='default' または 自分のuserタグ）は
    // 命名規則では表現できないため。
    @Query("""
            SELECT t FROM Tag t
            WHERE t.type = 'default'
            OR (t.type = 'user' AND t.createdBy = :userId)
            ORDER BY t.type ASC, t.name ASC
            """)
    List<Tag> findVisibleTags(@Param("userId") UUID userId);

    // タグ名の重複チェック（同ユーザー内でユニーク）
    // 命名規則を使う理由: name と createdBy の完全一致チェックのみで
    // JOIN や OR が不要なため。命名規則で十分に表現できる。
    Optional<Tag> findByNameAndCreatedBy(String name, UUID createdBy);
}
