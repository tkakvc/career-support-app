package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RefreshRequest {

    @NotBlank(message = "リフレッシュトークンは必須です")
    private String refreshToken;
}
