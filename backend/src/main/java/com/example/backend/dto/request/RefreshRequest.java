package com.example.backend.dto.request;

// ============================================================
// 【このファイル全体の方針】
// /api/auth/refresh（アクセストークン再発行）と /api/auth/logout（失効）で受け取るリクエスト。
// どちらもリフレッシュトークンだけを受け取るので、共通の DTO にしている。
// 【AI任せでOK】@NotBlank / @Getter の書き方
// ============================================================
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RefreshRequest {

    @NotBlank(message = "リフレッシュトークンは必須です")
    private String refreshToken;
}
