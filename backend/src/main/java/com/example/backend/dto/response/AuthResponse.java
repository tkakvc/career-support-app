package com.example.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// @Getter: token フィールドの getter をLombokが自動生成
// @AllArgsConstructor: 全フィールドを引数に取るコンストラクタをLombokが自動生成（new AuthResponse(token) で生成できる）
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
}
