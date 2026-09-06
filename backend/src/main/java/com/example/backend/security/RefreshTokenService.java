package com.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final long refreshExpirationSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${app.jwt.refresh-expiration}") long refreshExpirationSeconds) {
        this.redisTemplate = redisTemplate;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public String issue(UUID userId) {
        String token = generateRandomToken();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + token,
                userId.toString(),
                Duration.ofSeconds(refreshExpirationSeconds));
        return token;
    }

    public Optional<UUID> findUserId(String token) {
        String userId = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(userId));
    }

    public void revoke(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
