package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

// /api/auth/refresh・/api/auth/logout で受け取るリクエスト
@Getter
public class RefreshRequest {

    @NotBlank(message = "リフレッシュトークンは必須です")
    private String refreshToken;
}
