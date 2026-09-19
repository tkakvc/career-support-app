package com.example.backend.controller;

import com.example.backend.config.SecurityConfig;
import com.example.backend.dto.response.AccessTokenResponse;
import com.example.backend.dto.response.AuthResponse;
import com.example.backend.security.JwtAuthenticationFilter;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.service.AuthService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ============================================================
// /api/auth/** は SecurityConfig で permitAll なので、UserControllerTest と違い
// JwtAuthenticationFilter は関与しない（誰でも叩けるエンドポイント）。
// このテストで見たいのはむしろ AuthController 自身が持つロジック：
//   ① リクエストのバリデーション（@Valid）
//   ② リフレッシュトークンをHttpOnly CookieとしてSet-Cookieヘッダーで返しているか
//   ③ refreshTokenがJSON本文には含まれない（@JsonIgnore）か
//   ④ /refresh・/logout がCookieの有無で分岐する独自ロジック
// これらは全部「実際のHTTPレスポンスの中身」を見ないと確認できないので、
// AuthServiceTest（Service層）だけではカバーできない
// ============================================================
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // AuthControllerは/api/auth/**でpermitAllだが、SecurityConfig自体は@WebMvcTestが
    // 自動的に読み込む（実際に読み込まれることをこのテストで確認済み）。SecurityConfigが依存する
    // JwtAuthenticationFilterはさらにJwtTokenProviderに依存しているため、
    // permitAllなControllerのテストであってもこのBeanが無いとコンテキスト起動に失敗する
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    // AuthResponseのフィールド順は (accessToken, expiresIn, refreshToken, refreshExpiresIn)
    private AuthResponse buildAuthResponse() {
        return new AuthResponse("access-token", 3600, "refresh-token", 1209600);
    }

    // ── POST /api/auth/signup ────────────────────────────────────
    @Nested
    class Signup {

        @Test
        void メール形式が不正なら400を返しAuthServiceは呼ばれない() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"invalid-email\",\"password\":\"password123\",\"displayName\":\"テスト\"}"))
                    .andExpect(status().isBadRequest());

            then(authService).should(never()).signup(any());
        }

        @Test
        void 正常なリクエストなら201とCookieを返しレスポンス本文にrefreshTokenを含まない() throws Exception {
            // given
            given(authService.signup(any())).willReturn(buildAuthResponse());

            // when / then
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"new@example.com\",\"password\":\"password123\",\"displayName\":\"テスト\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    // @JsonIgnoreによりJSON本文にはrefreshTokenが出ない
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    // その代わりHttpOnly CookieのSet-Cookieヘッダーに載っている
                    .andExpect(cookie().value(REFRESH_TOKEN_COOKIE, "refresh-token"))
                    .andExpect(cookie().httpOnly(REFRESH_TOKEN_COOKIE, true));
        }
    }

    // ── POST /api/auth/login ─────────────────────────────────────
    @Nested
    class Login {

        @Test
        void 正常なリクエストなら200とCookieを返す() throws Exception {
            // given
            given(authService.login(any())).willReturn(buildAuthResponse());

            // when / then
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value(REFRESH_TOKEN_COOKIE, "refresh-token"));
        }

        @Test
        void Serviceが投げた401がそのままレスポンスになる() throws Exception {
            // given：メール・パスワード不一致のケース（Service層のロジック自体はAuthServiceTestで検証済み）
            given(authService.login(any()))
                    .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"));

            // when / then
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/auth/refresh ───────────────────────────────────
    @Nested
    class Refresh {

        @Test
        void リフレッシュトークンCookieが無ければ401を返す() throws Exception {
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized());

            then(authService).should(never()).refresh(any());
        }

        @Test
        void リフレッシュトークンCookieがあれば新しいアクセストークンを返す() throws Exception {
            // given
            given(authService.refresh("valid-refresh-token"))
                    .willReturn(new AccessTokenResponse("new-access-token", 3600));

            // when / then
            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new jakarta.servlet.http.Cookie(REFRESH_TOKEN_COOKIE, "valid-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"));
        }
    }

    // ── POST /api/auth/logout ────────────────────────────────────
    @Nested
    class Logout {

        @Test
        void Cookieが無くても204を返しServiceは呼ばれない() throws Exception {
            // given：ブラウザ側で既にCookieが消えているケースでもエラーにしない仕様
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent());

            then(authService).should(never()).logout(any());
        }

        @Test
        void Cookieがあれば失効させた上でCookie削除のレスポンスを返す() throws Exception {
            // when / then
            mockMvc.perform(post("/api/auth/logout")
                            .cookie(new jakarta.servlet.http.Cookie(REFRESH_TOKEN_COOKIE, "some-token")))
                    .andExpect(status().isNoContent())
                    // maxAge(0)で削除を指示するSet-Cookie
                    .andExpect(cookie().maxAge(REFRESH_TOKEN_COOKIE, 0));

            then(authService).should(times(1)).logout("some-token");
        }
    }
}
