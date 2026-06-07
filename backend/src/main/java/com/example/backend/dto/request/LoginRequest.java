package com.example.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

// @Getter: 各フィールドの getXxx() メソッドをLombokが自動生成
@Getter
public class LoginRequest {

    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;

    // ログイン時はパスワード長のチェック不要（認証失敗として処理するため）
    @NotBlank(message = "パスワードは必須です")
    private String password;
}
