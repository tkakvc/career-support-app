package com.example.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

// @Getter: 各フィールドの getter をLombokが自動生成
// @AllArgsConstructor: 全フィールドを引数に取るコンストラクタをLombokが自動生成
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private long expiresIn;

    // refreshTokenはJSON本文では返さず、AuthControllerがHttpOnly CookieのSet-Cookieヘッダーとして
    // 返す。このクラス自体には残してあるのは、AuthServiceからAuthControllerへCookieを組み立てるための
    // 値を渡す手段として使っているため。@JsonIgnoreでJSONへのシリアライズだけ止めている
    @JsonIgnore
    private String refreshToken;
    @JsonIgnore
    private long refreshExpiresIn;
}
