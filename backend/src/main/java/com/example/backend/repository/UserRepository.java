package com.example.backend.repository;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ JpaRepository を extends するか
//   → JpaRepository を extends するだけで save() / findById() / delete() などの基本 CRUD メソッドが使える。
//     findByEmail は「email フィールドで完全一致検索する SQL を自動生成する」命名規則メソッド。
//     実装を一切書かなくていいのが Spring Data JPA の強み。
// 【AI任せでOK】JpaRepository の extends 構文・命名規則によるメソッドの書き方
// ============================================================
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // email でユーザーを検索する（ログイン時の認証用）
    // 命名規則を使う理由: email の完全一致検索のみで JOIN や OR が不要なため。
    Optional<User> findByEmail(String email);
}
