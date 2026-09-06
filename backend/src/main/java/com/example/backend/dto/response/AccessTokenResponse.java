package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// /api/auth/refresh（アクセストークン再発行）のレスポンス。
// 再発行時はアクセストークンだけを返す（リフレッシュトークンはそのまま使い続けるため返さない）。
// 【AI任せでOK】@Getter / @AllArgsConstructor の Lombok 構文
// ============================================================
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccessTokenResponse {
    private String accessToken;
    private long expiresIn;
}
