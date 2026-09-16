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
// トークンの形式・2種類のキーの仕様は docs/auth/api/security.md の「Redisのキー設計」参照
@Service
public class RefreshTokenService {

    // Redisに保存するキーの種類。用途によって前置き文字列（prefix）が違う2種類を、
    // どちらの意図で組み立てたキーかが名前で分かるように enum にまとめている。
    //
    // 具体例：ユーザー 3fa85f64-... がスマホとPCの2台でログイン中の場合、Redisの中身はこうなる
    //   refresh:tokenA            → 3fa85f64-...          （スマホ分。TOKEN_TO_USER）
    //   refresh:tokenB            → 3fa85f64-...          （PC分。TOKEN_TO_USER）
    //   refresh:user:3fa85f64-... → {"tokenA", "tokenB"}   （逆引き用の集合。USER_TO_TOKENS）
    private enum RefreshTokenIndex {
        // TOKEN_TO_USER("refresh:") のように書くと、Javaが内部で
        // new RefreshTokenIndex("refresh:") を呼んでコンストラクタを実行してくれる
        // （enumの定数宣言だけに許された特別な省略記法。newは書かない）
        // トークン文字列 → userId 1個。本人確認に使う（issue/findUserId/revoke が使う）
        TOKEN_TO_USER("refresh:"),
        // userId → その人が発行したトークン文字列の集合（Set）。全端末ログアウト用の逆引き
        // （issue/revokeAllForUser が使う）
        USER_TO_TOKENS("refresh:user:");

        private final String prefix;

        RefreshTokenIndex(String prefix) {
            this.prefix = prefix;
        }

        // このキーの種類として、指定したidの実際のRedisキー文字列を組み立てる
        String key(Object id) {
            return prefix + id;
        }
    }

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
                RefreshTokenIndex.TOKEN_TO_USER.key(token),
                userId.toString(),
                Duration.ofSeconds(refreshExpirationSeconds));

        // 逆引きSetにもこのトークンを追加する。あわせて毎回 expire() でSetのTTLを
        // 「今から14日後」に更新し直しているのが以下の1行。これをやらないと起きる不具合の具体例：
        //   1日目：スマホでログイン → tokenA発行 → Set(userId)のTTLが「15日目」に設定される
        //   10日目：PCでログイン    → tokenB発行 → Setに追加するだけでTTLを更新し直さないと、
        //           Setは15日目のままなので、tokenBはまだ有効（10+14=24日目まで）なのに
        //           15日目にSetごと消えてしまい、revokeAllForUser()がtokenBを見つけられなくなる
        // これはRedisのSetが「要素1つ1つに別々の有効期限」を持てず、TTLをSet全体に対して
        // 1つしか設定できない仕様のため。発行のたびにTTLを更新し直すことで、
        // 直近発行したトークンの有効期限までSet自体を延命させている。
        String userTokensKey = RefreshTokenIndex.USER_TO_TOKENS.key(userId);
        redisTemplate.opsForSet().add(userTokensKey, token);
        // expire()は値には触れず、TTL（あと何秒で自動削除されるか）だけを設定し直す
        // （Redisの EXPIRE コマンドに対応）。add()はTTLを更新しないのでここで呼び直している
        redisTemplate.expire(userTokensKey, Duration.ofSeconds(refreshExpirationSeconds));

        return token;
    }

    // トークンが有効なら userId を返す。存在しなければ空（失効済み・期限切れ・不正）
    public Optional<UUID> findUserId(String token) {
        String userId = redisTemplate.opsForValue().get(RefreshTokenIndex.TOKEN_TO_USER.key(token));
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(userId));
    }

    // リフレッシュトークンを Redis から削除して失効させる
    public void revoke(String token) {
        redisTemplate.delete(RefreshTokenIndex.TOKEN_TO_USER.key(token));
    }

    // このユーザーが発行した全リフレッシュトークンを失効させる（パスワード変更時に使う）。
    // 対象が無くても例外にはならず、複数回呼んでも安全（冪等）。
    // 実行中に issue() が同時に呼ばれた場合、その新トークンは失効対象から漏れうる
    public void revokeAllForUser(UUID userId) {
        String userTokensKey = RefreshTokenIndex.USER_TO_TOKENS.key(userId);
        var tokens = redisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null) {
            tokens.forEach(token -> redisTemplate.delete(RefreshTokenIndex.TOKEN_TO_USER.key(token)));
        }
        redisTemplate.delete(userTokensKey);
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
