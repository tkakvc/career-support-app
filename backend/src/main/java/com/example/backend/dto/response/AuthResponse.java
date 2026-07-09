package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】レスポンス DTO のフィールド定義・@Getter / @AllArgsConstructor の Lombok 構文
// 【面接で説明できるようにする】なぜレスポンスに token だけを返すか（ステートレス JWT の設計）
//   → サーバーはトークンを保存しない。クライアントがトークンを受け取って保管し、
//     以降のリクエストに Authorization ヘッダーで付けて送る。
//     サーバーが状態を持たないためスケールアウトしやすい（ステートレス設計）。
// ============================================================
import lombok.AllArgsConstructor;
import lombok.Getter;

// @Getter: token フィールドの getter をLombokが自動生成
// @AllArgsConstructor: 全フィールドを引数に取るコンストラクタをLombokが自動生成（new AuthResponse(token) で生成できる）
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
}
