package com.example.backend.controller;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Controller には処理を書かず Service に委譲するか（レイヤードアーキテクチャ）
//   → Controller の責務は「HTTPリクエストを受け取りレスポンスを返す」ことだけ。
//     ビジネスロジック（AIを呼ぶ、キャッシュする、レート制限する）を Controller に書くと、
//     テストが書きにくくなり、同じ処理を別のエンドポイントからも呼びたくなったときに重複する。
//     Service に書くことで Controller とビジネスロジックの責務を分離できる（単一責任の原則）。
// 【AI任せでOK】@RestController / @RequestMapping / @PostMapping などアノテーションの書き方
// 【AI任せでOK】@RequiredArgsConstructor の Lombok 構文
// ============================================================
import com.example.backend.dto.request.DecomposeRequest;
import com.example.backend.dto.response.DecomposeResponse;
import com.example.backend.dto.response.SuggestResponse;
import com.example.backend.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// AI機能のエンドポイントを2本だけ持つシンプルなController。
// ビジネスロジックは全て AiService に委譲する。
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // POST /api/ai/suggest
    // リクエストボディなし。ユーザーIDはJWTから取得するので送る必要がない。
    @PostMapping("/suggest")
    public SuggestResponse suggest(@AuthenticationPrincipal UUID userId) {
        return aiService.suggest(userId);
    }

    // POST /api/ai/decompose
    // @Valid: DecomposeRequest のバリデーション（@NotBlank, @Size）を実行する
    @PostMapping("/decompose")
    public DecomposeResponse decompose(@AuthenticationPrincipal UUID userId,
                                       @Valid @RequestBody DecomposeRequest request) {
        return aiService.decompose(userId, request.getGoal());
    }
}
