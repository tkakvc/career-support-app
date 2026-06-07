package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    // メールアドレス。ログイン時の識別子。UNIQUE 制約でアカウント重複を防ぐ。
    @Column(nullable = false, unique = true)
    private String email;

    // BCrypt でハッシュ化済みのパスワード。平文は保存しない。
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // 画面上に表示するユーザー名。
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // GitHub ユーザー名。未設定の場合は null。
    @Column(name = "github_username", length = 100)
    private String githubUsername;

    // レコード INSERT 時に自動で現在日時をセットする
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // レコード UPDATE 時に自動で現在日時をセットする
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
