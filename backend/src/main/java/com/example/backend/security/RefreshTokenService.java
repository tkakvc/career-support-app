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
// トークンの形式・2種類のキーの仕様は docs/auth/api/security.md の「Redisのキー設計」参照
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

    // リフレッシュトークンが有効なら、その持ち主の userId を返す。
    // Redisに存在しなければ（＝失効済み or 期限切れ or 偽物）空を返す。
    public Optional<UUID> findUserId(String token) {
        String userId = redisTemplate.opsForValue().get(RefreshTokenIndex.TOKEN_TO_USER.key(token));
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(userId));
    }

    // リフレッシュトークンを失効させる（ログアウト）。Redisからキーを消すだけ。
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
