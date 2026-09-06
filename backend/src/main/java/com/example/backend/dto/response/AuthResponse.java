package com.example.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// @Getter: 各フィールドの getter をLombokが自動生成
// @AllArgsConstructor: 全フィールドを引数に取るコンストラクタをLombokが自動生成
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private long expiresIn;
    private String refreshToken;
    private long refreshExpiresIn;
}
