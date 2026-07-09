package com.example.backend.security;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ JWT をステートレスで使うか
//   → JWT（JSON Web Token）はトークン自体に userId と有効期限が入っており、
//     サーバーが「どのトークンが有効か」を DB に保存する必要がない（ステートレス）。
//     サーバーは秘密鍵で署名を検証するだけで済む。
//     セッションと違い、サーバーを複数台に増やしても全台で同じ検証ができる。
// 【面接で説明できるようにする】なぜ HS256（HMAC + SHA-256）で署名するか
//   → 「このトークンは確かにこのサーバーが発行した」ことを証明するため。
//     秘密鍵（app.jwt.secret）を知らなければ有効な署名を作れないので、
//     トークンの偽造や改ざんを防げる。
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
    private final long expirationSeconds;

    // application.yaml の app.jwt.secret / app.jwt.expiration を注入
    // yamlから秘密鍵と有効期限を受け取り、署名用の鍵オブジェクトを作る
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expirationSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    // userId を埋め込んだJWTを生成して返す
    public String generateToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
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
