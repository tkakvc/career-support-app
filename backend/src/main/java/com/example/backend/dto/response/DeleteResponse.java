package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】レスポンス DTO のフィールド定義・Lombok の @Getter の書き方
//   → DELETE 成功時に { "result": "deleted" } を返すだけのシンプルなクラス
// ============================================================
import lombok.Getter;

@Getter
public class DeleteResponse {

    private final String result = "deleted";
}
