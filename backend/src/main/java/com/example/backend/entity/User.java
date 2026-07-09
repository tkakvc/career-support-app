package com.example.backend.entity;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】@Entity / @Table / @Column などの JPA アノテーションの書き方
//   → @CreationTimestamp / @UpdateTimestamp の Hibernate アノテーションは覚えなくていい
//   → Lombok の @Builder / @Getter / @Setter / @NoArgsConstructor / @AllArgsConstructor は覚えなくていい
// 【面接で説明できるようにする】なぜ passwordHash フィールドを持ち、password フィールドを持たないか
//   → 平文パスワードをDBに保存すると、DBが漏洩した瞬間に全ユーザーのパスワードが流出する。
//     BCrypt でハッシュ化した値（passwordHash）だけを保存することで、
//     漏洩してもハッシュから元のパスワードを逆算できないため被害を最小化できる。
// ============================================================
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
