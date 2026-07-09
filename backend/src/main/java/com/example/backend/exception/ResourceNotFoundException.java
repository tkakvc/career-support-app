package com.example.backend.exception;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ独自の例外クラスを作るか
//   → 「リソースが見つからない」という状況を専用の例外クラスで表現することで、
//     GlobalExceptionHandler で @ExceptionHandler(ResourceNotFoundException.class) と書いて
//     その例外だけを 404 にマッピングできる。
//     RuntimeException を throw した場合は 500 になってしまう。
// 【AI任せでOK】RuntimeException を継承する書き方・super(message) の呼び方
// ============================================================
// RuntimeException を継承することで、throws 宣言なしに throw できる（非検査例外）
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
