package com.example.backend.security;

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
