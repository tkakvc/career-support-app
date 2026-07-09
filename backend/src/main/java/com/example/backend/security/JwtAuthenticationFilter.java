package com.example.backend.security;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Filter で JWT を検証するか
//   → Spring Security のフィルターチェーンは Controller の前に実行される。
//     ここで JWT を検証して SecurityContext に userId をセットすることで、
//     Controller が @AuthenticationPrincipal で userId を受け取れるようになる。
//     各 Controller に認証チェックを書かなくていい（横断的関心事の分離）。
// 【面接で説明できるようにする】なぜステートレスな JWT 認証を使うか
//   → サーバーがセッションを保持しないため、サーバーを増やしても（スケールアウト）
//     どのサーバーでも同じように JWT を検証できる。
//     セッション方式ではサーバー間でセッション共有が必要になる。
// 【AI任せでOK】OncePerRequestFilter の extends 構文・doFilterInternal の書き方
// 【AI任せでOK】UsernamePasswordAuthenticationToken / SecurityContextHolder の使い方
// 【理解する】filterChain.doFilter(request, response) の意味
//   フィルターとは「Controller に届く前にリクエストを横取りして処理する部品」のこと。
//   Spring Security は複数のフィルターを直列に並べていて、リクエストはそれを順番に通過する。
//   このクラス（JwtAuthenticationFilter）もその1つ。
//
//   リクエストの流れ:
//     ブラウザ
//       → JwtAuthenticationFilter（このクラス）
//       → 他のフィルター群（CSRFチェック・セッション管理など）
//       → Controller
//
//   filterChain.doFilter() = 「自分の処理はここまで。次のフィルターに渡す」という命令。
//   この1行を書かないと、リクエストがここで止まって Controller まで届かない。
// 【AI任せでOK】filterChain.doFilter の引数の書き方（request と response をそのまま渡すだけ）
// ============================================================
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

// OncePerRequestFilter: 1リクエストにつき必ず1回だけ実行されることを保証するフィルター
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            UUID userId = jwtTokenProvider.extractUserId(token);

            // UsernamePasswordAuthenticationToken: Springが認証情報を扱う標準クラス
            // principal（第1引数）にuserIdをセットすることで @AuthenticationPrincipal で取り出せる
            // authorities（第3引数）は権限リスト。今回はロール制御なしなので空
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());

            // SecurityContext に認証情報をセット → 以降のフィルター・Controllerから参照できる
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 次のフィルターへ処理を渡す
        filterChain.doFilter(request, response);
    }

    // Authorization ヘッダーから Bearer トークンを取り出す
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
