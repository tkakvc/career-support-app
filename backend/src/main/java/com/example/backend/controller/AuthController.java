package com.example.backend.controller;

import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.request.SignupRequest;
import com.example.backend.dto.response.AccessTokenResponse;
import com.example.backend.dto.response.AuthResponse;
import com.example.backend.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
// final フィールド(authService)だけを引数に取るコンストラクタをLombokが自動生成 → @Autowired 不要
@RequiredArgsConstructor
public class AuthController {

    // リフレッシュトークンを保存するCookieの名前。フロントはこの名前を直接参照しない
    // （Cookieの中身はHttpOnlyでJavaScriptから読めないので、そもそも参照できない）
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final AuthService authService;

    // Cookieの Secure 属性の値。application.yaml の app.cookie.secure から注入される
    // （本番=true・ローカル(http)=false）
    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    // 正常終了時に 200 OK ではなく 201 Created を返す（RESTの慣習: 新規リソース作成=201）
    @ResponseStatus(HttpStatus.CREATED)
    // @Valid: SignupRequest のフィールドに付いたバリデーション(@NotBlank等)を実行する
    // @RequestBody: リクエストのJSON本文をSignupRequestオブジェクトに変換する
    public AuthResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.signup(request);
        setRefreshTokenCookie(response, authResponse.getRefreshToken(), authResponse.getRefreshExpiresIn());
        return authResponse;
    }

    @PostMapping("/login")
    // @ResponseStatus 省略 → デフォルトの 200 OK が返る
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        setRefreshTokenCookie(response, authResponse.getRefreshToken(), authResponse.getRefreshExpiresIn());
        return authResponse;
    }

    @PostMapping("/refresh")
    // リフレッシュトークンを受け取り、新しいアクセストークンを再発行する。
    // リフレッシュトークンはリクエストボディではなく、ブラウザが自動で送ってくるCookieから読む
    public AccessTokenResponse refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "リフレッシュトークンがありません");
        }
        return authService.refresh(refreshToken);
    }

    @PostMapping("/logout")
    // リフレッシュトークンを失効させる。返す本文が無いので 204 No Content
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        // Cookieが無い（例：既に期限切れでブラウザ側から消えている）場合でも、
        // フロントは必ずログアウト処理を完了させたいので、エラーにはせず何もせず204を返す
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearRefreshTokenCookie(response);
    }

    // リフレッシュトークンをHttpOnly CookieとしてSet-Cookieヘッダーで発行する。
    // HttpOnly: JavaScriptから読めなくする（XSSで盗まれても中身を取り出せない）
    // Secure: HTTPS通信のときだけCookieを送る（本番のみtrue）
    // SameSite=Lax: 別サイトからのリクエストにはこのCookieを付けない（CSRF対策）
    // Path: /api/auth 配下（/refresh・/logout）にだけ送られれば十分なので絞る
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // ログアウト時にCookieを削除する。
    // 値を空にして maxAge(0) を指定したSet-Cookieを返すと、ブラウザはそのCookieを即座に消す
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
