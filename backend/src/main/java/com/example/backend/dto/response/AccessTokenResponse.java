package com.example.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// /api/auth/refresh のレスポンス（再発行はアクセストークンのみ返す）
@Getter
@AllArgsConstructor
public class AccessTokenResponse {
    private String accessToken;
    private long expiresIn;
}
