package com.example.backend.controller;

// ============================================================
// 【このファイル全体の方針】
// 認証エンドポイントの入口。方式変更で /refresh（再発行）と /logout（失効）を追加した。
// これらは SecurityConfig で /api/auth/** が permitAll のため、アクセストークン無しで呼べる
// （リフレッシュトークン自体を本文で受け取って認証するため）。
//
// 【面接で説明できるようにする】Controller / Service / Repository の役割分担（レイヤードアーキテクチャ）
//   → Controller はHTTPの入出力だけ担当し、判断（トークン検証やDBアクセス）はServiceに委譲する。
// 【AI任せでOK】@RestController / @PostMapping / @Valid / @RequestBody のアノテーション
// ============================================================
import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.request.RefreshRequest;
import com.example.backend.dto.request.SignupRequest;
import com.example.backend.dto.response.AccessTokenResponse;
import com.example.backend.dto.response.AuthResponse;
import com.example.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    // 新規リソース作成なので 201 Created を返す
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        // signup も2種類のトークンを返す（登録と同時にログイン状態になる）
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    // リフレッシュトークンを受け取り、新しいアクセストークンを返す
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    // リフレッシュトークンを失効させる。返す本文は無いので 204 No Content
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
    }
}
