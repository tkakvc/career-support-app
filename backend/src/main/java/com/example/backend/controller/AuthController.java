package com.example.backend.controller;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Controller / Service / Repository に分けるか（レイヤードアーキテクチャ）
//   → Controller：HTTPの入出力を担当。URLとメソッドを対応させる。
//     Service：ビジネスロジックを担当。「メールが重複していたら409」などの判断をする。
//     Repository：DBアクセスを担当。SQLを書く（または JPA に生成させる）。
//     各レイヤーが1つの責務に集中することで、テストが書きやすく変更に強い構造になる（単一責任の原則）。
// 【AI任せでOK】@RestController / @RequestMapping / @PostMapping などのアノテーションの書き方
// 【AI任せでOK】@RequiredArgsConstructor の Lombok 構文
// ============================================================
import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.request.SignupRequest;
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
        String token = authService.signup(request);
        return new AuthResponse(token);
    }

    @PostMapping("/login")
    // @ResponseStatus 省略 → デフォルトの 200 OK が返る
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return new AuthResponse(token);
    }
}
