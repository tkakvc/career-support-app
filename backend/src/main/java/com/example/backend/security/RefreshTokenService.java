package com.example.backend.security;

// ============================================================
// 【このファイル全体の方針】
// リフレッシュトークン（アクセストークンを再発行するための鍵）を Redis で管理するクラス。
//
// 【面接で説明できるようにする】なぜリフレッシュトークンは JWT ではなく「ランダム文字列＋Redis保存」か
//   → JWTは自己完結（サーバーに保存しない）ゆえに、発行後に失効できないのが弱点。
//     リフレッシュトークンは「失効できること」が重要なので、あえてサーバー側(Redis)に保存し、
//     ログアウト時はそのキーを削除するだけで失効できるようにしている。
//     トークン自体はただのランダム文字列にして、意味を持たせない（漏れても中身から情報が取れない）。
// 【面接で説明できるようにする】なぜ Redis を使うか（DBではなく）
//   → キーごとにTTL(有効期限)を設定でき、期限切れを自動削除してくれる。
//     リフレッシュトークンの「14日で失効」をアプリ側の掃除処理なしに実現できる。
//     読み書きも高速で、認証のたびのアクセスに向く。
// 【面接で説明できるようにする】なぜトークンを SecureRandom で作るか
//   → 通常の Random は値が予測可能で、次のトークンを推測される恐れがある。
//     SecureRandom は暗号的に安全な乱数で、推測を困難にする。
// 【AI任せでOK】StringRedisTemplate の opsForValue().set/get/delete の書き方
// ============================================================
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

    // Redisのキーの接頭辞。"refresh:" で始めることで、Redis内の他の用途のキーと区別する。
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

    // 新しいリフレッシュトークンを発行し、Redisに「トークン→userId」で保存して返す。
    // 保存時にTTL（有効期限）を設定するので、14日後にRedisから自動で消える。
    public String issue(UUID userId) {
        String token = generateRandomToken();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + token,
                userId.toString(),
                Duration.ofSeconds(refreshExpirationSeconds));
        return token;
    }

    // リフレッシュトークンが有効なら、その持ち主の userId を返す。
    // Redisに存在しなければ（＝失効済み or 期限切れ or 偽物）空を返す。
    public Optional<UUID> findUserId(String token) {
        String userId = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(userId));
    }

    // リフレッシュトークンを失効させる（ログアウト）。Redisからキーを消すだけ。
    public void revoke(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    // 有効期限（秒）。レスポンスの refreshExpiresIn としてクライアントに返すために公開する。
    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    // 暗号的に安全な乱数から、URLで扱える文字列のトークンを作る。
    private String generateRandomToken() {
        byte[] bytes = new byte[32]; // 32バイト=256ビットの乱数。推測を事実上不可能にする長さ
        secureRandom.nextBytes(bytes);
        // URLセーフなBase64（+/ の代わりに -_ を使う）でエンコードし、末尾の詰め文字(=)は付けない
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
