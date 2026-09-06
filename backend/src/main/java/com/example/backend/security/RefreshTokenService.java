package com.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

// リフレッシュトークンを Redis で管理する。ログアウト時に削除することで失効を実現する。
@Service
public class RefreshTokenService {

    // Redis のキーの接頭辞（他用途のキーと区別する）
    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final long refreshExpirationSeconds;
    // 予測されにくいトークンを作るため、暗号的に安全な乱数を使う
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${app.jwt.refresh-expiration}") long refreshExpirationSeconds) {
        this.redisTemplate = redisTemplate;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    // リフレッシュトークンを発行し、Redis に「トークン → userId」をTTL付きで保存する
    public String issue(UUID userId) {
        String token = generateRandomToken();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + token,
                userId.toString(),
                Duration.ofSeconds(refreshExpirationSeconds));
        return token;
    }

    // トークンが有効なら userId を返す。存在しなければ空（失効済み・期限切れ・不正）
    public Optional<UUID> findUserId(String token) {
        String userId = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(userId));
    }

    // リフレッシュトークンを Redis から削除して失効させる
    public void revoke(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    // 32バイトの乱数を URL セーフな Base64 文字列にして返す
    private String generateRandomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
