package com.example.backend.security;

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

    // アクセストークンの有効期限（秒）。レスポンスの expiresIn として返すために公開する
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
