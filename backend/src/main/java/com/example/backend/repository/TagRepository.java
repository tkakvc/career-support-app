package com.example.backend.repository;

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
