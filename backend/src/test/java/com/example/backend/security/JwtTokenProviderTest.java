package com.example.backend.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// StringRedisTemplateのようなインフラ依存が無く、コンストラクタに文字列と数値を渡すだけで
// 本物のインスタンスを直接newできるので、Mockitoは使わずシンプルな単体テストにする
class JwtTokenProviderTest {

    // HS256は最低32バイトの鍵長が必要なので、それを満たす適当な文字列を使う
    private static final String SECRET = "test-secret-value-for-unit-tests-do-not-use-in-prod";

    private final UUID userId = UUID.randomUUID();

    // ── generateAccessToken / extractUserId ─────────────────────
    @Nested
    class GenerateAndExtract {

        @Test
        void 生成したトークンからextractUserIdで同じuserIdが取り出せる() {
            // given
            JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);

            // when
            String token = provider.generateAccessToken(userId);

            // then
            assertThat(provider.extractUserId(token)).isEqualTo(userId);
        }
    }

    // ── validateToken ────────────────────────────────────────────
    @Nested
    class ValidateToken {

        @Test
        void 正しいトークンならtrueを返す() {
            // given
            JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);
            String token = provider.generateAccessToken(userId);

            // when / then
            assertThat(provider.validateToken(token)).isTrue();
        }

        @Test
        void JWTの形をしていない文字列ならfalseを返す() {
            // given
            JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);

            // when / then
            assertThat(provider.validateToken("not-a-jwt-token")).isFalse();
        }

        @Test
        void 有効期限が切れたトークンならfalseを返す() {
            // given：有効期限を-1秒に設定する＝生成した瞬間にすでに期限切れのトークンになる
            JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1);
            String expiredToken = provider.generateAccessToken(userId);

            // when / then
            assertThat(provider.validateToken(expiredToken)).isFalse();
        }

        @Test
        void 違う秘密鍵で署名されたトークンならfalseを返す() {
            // given：改ざん検知の確認。別の鍵で発行したトークンを、元の鍵を持つproviderで検証する
            JwtTokenProvider otherProvider = new JwtTokenProvider("different-secret-value-also-32-bytes-or-more", 3600);
            String tokenFromOtherSecret = otherProvider.generateAccessToken(userId);

            JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);

            // when / then
            assertThat(provider.validateToken(tokenFromOtherSecret)).isFalse();
        }
    }

    // ── getAccessExpirationSeconds ───────────────────────────────
    @Nested
    class GetAccessExpirationSeconds {

        @Test
        void コンストラクタに渡した値をそのまま返す() {
            // given
            JwtTokenProvider provider = new JwtTokenProvider(SECRET, 900);

            // when / then
            assertThat(provider.getAccessExpirationSeconds()).isEqualTo(900);
        }
    }
}
