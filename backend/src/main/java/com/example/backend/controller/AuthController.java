package com.example.backend.controller;

// ============================================================
// 【このファイル全体の方針】
// 認証エンドポイントの入口。方式変更で /refresh（再発行）と /logout（失効）を追加した。
// これらは SecurityConfig で /api/auth/** が permitAll のため、アクセストークン無しで呼べる
// （リフレッシュトークン自体はJSON本文ではなくHttpOnly Cookieで受け渡す。理由は下記）。
//
// 【面接で説明できるようにする】Controller / Service / Repository の役割分担（レイヤードアーキテクチャ）
//   → Controller はHTTPの入出力だけ担当し、判断（トークン検証やDBアクセス）はServiceに委譲する。
// 【面接で説明できるようにする】なぜリフレッシュトークンをJSON本文ではなくHttpOnly Cookieで渡すか
//   → 当初はJSON本文で返し、フロントがlocalStorageに保存していたが、
//     XSS（悪意あるJavaScriptの実行）が1件でも起きればlocalStorageを読むだけで
//     14日間有効なリフレッシュトークンが盗まれてしまう。
//     HttpOnly属性付きのCookieはJavaScriptから中身を読めないため、XSSが起きても盗まれない。
// 【AI任せでOK】@RestController / @PostMapping / @Valid / @RequestBody のアノテーション
// ============================================================
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
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // リフレッシュトークンを保存するCookieの名前。フロントはこの名前を直接参照しない
    // （Cookieの中身はHttpOnlyでJavaScriptから読めないので、そもそも参照できない）
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    // Cookieの Secure 属性の値。application.yaml の app.cookie.secure から注入される
    // （本番=true・ローカル(http)=false。HTTPS前提の属性をhttpのlocalhostに付けると
    //  ブラウザがCookieを送ってくれなくなるため、環境ごとに切り替える）
    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    // 新規リソース作成なので 201 Created を返す
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        // signup も2種類のトークンを返す（登録と同時にログイン状態になる）
        AuthResponse authResponse = authService.signup(request);
        setRefreshTokenCookie(response, authResponse.getRefreshToken(), authResponse.getRefreshExpiresIn());
        return authResponse;
    }

    @PostMapping("/login")
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
    // リフレッシュトークンを失効させる。返す本文は無いので 204 No Content
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
    // 【面接で説明できるようにする】各属性の意味
    //   HttpOnly: JavaScriptから読めなくする（XSSで盗まれても中身を取り出せない）
    //   Secure: HTTPS通信のときだけCookieを送る（本番のみtrue）
    //   SameSite=Lax: 別サイトからのリクエストにはこのCookieを付けない（CSRF対策）
    //   Path: /api/auth 配下（/refresh・/logout）にだけ送られれば十分なので絞る
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
