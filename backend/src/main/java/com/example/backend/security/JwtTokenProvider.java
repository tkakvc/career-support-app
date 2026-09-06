package com.example.backend.security;

// ============================================================
// 【このファイル全体の方針】
// このクラスが担当するのは「アクセストークン（JWT）」の生成・検証だけ。
// リフレッシュトークンはJWTではなく不透明なランダム文字列で、RefreshTokenService が Redis で管理する（別ファイル）。
//
// 【面接で説明できるようにする】なぜアクセストークンはJWT（ステートレス）のままにするか
//   → アクセストークンは毎リクエストで検証される。JWTなら署名検証だけで完結し、
//     サーバー側の保存やDB/Redisアクセスが不要で速い。短命(15分)にすることで、
//     「失効できない」という弱点の影響時間を最小化している。
// 【面接で説明できるようにする】なぜ HS256（HMAC + SHA-256）で署名するか
//   → 秘密鍵(app.jwt.secret)を知らないと有効な署名を作れないため、トークンの偽造・改ざんを防げる。
// 【AI任せでOK】Jwts.builder() / Jwts.parser() の JJWT ライブラリの API の書き方
// ============================================================
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpirationSeconds;

    // application.yaml の app.jwt.secret / app.jwt.access-expiration を注入
    // yamlから秘密鍵とアクセストークンの有効期限を受け取り、署名用の鍵オブジェクトを作る
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration}") long accessExpirationSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationSeconds = accessExpirationSeconds;
    }

    // userId を埋め込んだアクセストークン(JWT)を生成して返す
    public String generateAccessToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationSeconds * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    // アクセストークンの有効期限（秒）。レスポンスの expiresIn としてクライアントに返すために公開する。
    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    // JWT から userId を取り出す
    public UUID extractUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }

    // JWT の署名・有効期限を検証する。不正なら false を返す
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
