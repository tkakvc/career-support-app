package com.example.backend.repository;

import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // email でユーザーを検索する（ログイン時の認証用）
    // 命名規則を使う理由: email の完全一致検索のみで JOIN や OR が不要なため。
    Optional<User> findByEmail(String email);
}
