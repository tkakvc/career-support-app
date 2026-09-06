package com.example.backend.controller;

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
// final フィールド(authService)だけを引数に取るコンストラクタをLombokが自動生成 → @Autowired 不要
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    // 正常終了時に 200 OK ではなく 201 Created を返す（RESTの慣習: 新規リソース作成=201）
    @ResponseStatus(HttpStatus.CREATED)
    // @Valid: SignupRequest のフィールドに付いたバリデーション(@NotBlank等)を実行する
    // @RequestBody: リクエストのJSON本文をSignupRequestオブジェクトに変換する
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    // @ResponseStatus 省略 → デフォルトの 200 OK が返る
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    // リフレッシュトークンを受け取り、新しいアクセストークンを再発行する
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    // リフレッシュトークンを失効させる。返す本文が無いので 204 No Content
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
    }
}
