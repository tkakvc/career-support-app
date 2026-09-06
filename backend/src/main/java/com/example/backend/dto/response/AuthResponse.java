package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 認証成功時にクライアントへ返すレスポンス DTO。
// 方式変更に伴い、返すものが「JWT1本」から「アクセストークン＋リフレッシュトークン」に増えた。
//
// 【面接で説明できるようにする】なぜ2種類のトークンを返すか
//   → accessToken：API認証に使う短命(15分)のJWT。毎リクエストに付ける。
//     refreshToken：accessToken が切れたとき再発行に使う長命(14日)の鍵。Redisで失効管理される。
//     短命アクセス＋失効可能なリフレッシュ、の組み合わせで「速さ」と「失効できる安全性」を両立する。
// 【AI任せでOK】@Getter / @AllArgsConstructor の Lombok 構文
// ============================================================
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;      // APIauthに使うJWT（短命）
    private long expiresIn;          // accessToken の有効期限（秒）
    private String refreshToken;     // accessToken 再発行用の鍵（長命・Redis管理）
    private long refreshExpiresIn;   // refreshToken の有効期限（秒）
}
