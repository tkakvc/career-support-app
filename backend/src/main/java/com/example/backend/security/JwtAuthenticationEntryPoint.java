package com.example.backend.security;

import com.example.backend.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 【学習ポイント：押さえておく】未認証（JWTが無い・不正・期限切れ）のとき、何をSpring Securityに
// 「認証してください、と伝える係」として登録するかで401か403かが決まる（詳しくは
// memo/security/401と403の食い違い.md）。この係を1つも登録していなかったため、既定の
// 「何もしない403係」（Http403ForbiddenEntryPoint）にフォールバックし、401ではなく403が
// 返っていた。このクラスをSecurityConfigに登録することで、401＋JSONボディを返すようにする。
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "認証が必要です");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
