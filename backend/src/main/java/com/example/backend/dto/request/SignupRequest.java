package com.example.backend.dto.request;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】DTO クラスのフィールド定義と @NotBlank / @Email / @Size などのバリデーションアノテーションの書き方
//   → @Getter の Lombok 構文は覚えなくていい。
// ============================================================
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

// @Getter: 各フィールドの getXxx() メソッドをLombokが自動生成
@Getter
public class SignupRequest {

    // @NotBlank: null・空文字・空白のみを弾く
    // @Email: メールアドレスの形式チェック
    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;

    // @Size(min = 8): 8文字未満を弾く
    @NotBlank(message = "パスワードは必須です")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String password;

    // @Size(max = 100): DBのカラム長(100)に合わせて上限を設定
    @NotBlank(message = "表示名は必須です")
    @Size(max = 100, message = "表示名は100文字以内で入力してください")
    private String displayName;
}
