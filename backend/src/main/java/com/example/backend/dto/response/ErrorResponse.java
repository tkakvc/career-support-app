package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】レスポンス DTO のフィールド定義・@JsonInclude の書き方
// 【面接で説明できるようにする】なぜ統一されたエラーレスポンス形式を作るか
//   → エラーが起きたときにフロントエンドに返す JSON の形を統一するためのクラス。
//     このクラスを使うと、例えば 401 エラーのとき以下の JSON が返る：
//       { "status": 401, "message": "認証が必要です" }
//     バリデーションエラー（400）のときは errors も返る：
//       { "status": 400, "message": "入力値が不正です", "errors": ["名前は必須です", "メールの形式が不正です"] }
//
//     Spring のデフォルトエラーは { "timestamp", "status", "error", "path" } という形で、
//     エラーの詳細メッセージが入っていないのでフロントエンドが扱いにくい。
//     GlobalExceptionHandler でこのクラスを返すことで、全エラーを上記の形に統一している。
// ============================================================
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;

@Getter
public class ErrorResponse {

    private final int status;
    private final String message;

    // バリデーションエラー（400）のときだけ使うフィールド。
    // @JsonInclude(NON_NULL): errors が null のとき、JSON に "errors": null と出力されるのを防ぐ。
    // → 401 や 409 のエラーでは errors を JSON に含めたくないので、null のときはキーごと省略する。
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<String> errors;

    // errors が不要なエラー（401, 409, 500 など）向けのコンストラクタ。
    // errors に null を渡して3引数版に処理を委譲している。
    public ErrorResponse(int status, String message) {
        this(status, message, null);
    }

    public ErrorResponse(int status, String message, List<String> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors;
    }
}
