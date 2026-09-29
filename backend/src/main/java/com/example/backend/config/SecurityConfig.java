package com.example.backend.config;

import com.example.backend.security.JwtAuthenticationEntryPoint;
import com.example.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRFを無効化: REST APIはCookieでセッション管理しないため不要。
            // CSRFはブラウザがCookieを自動送信する仕組みを悪用した攻撃で、
            // JWTをAuthorizationヘッダーで送る方式では成立しない。
            .csrf(csrf -> csrf.disable())

            // CORSの設定を適用
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // セッションを使わない設定（ステートレス）
            // サーバーがセッションを保持しないため、毎回JWTで認証する
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 未認証時のレスポンスを担うAuthenticationEntryPoint。未登録だと既定の
            // Http403ForbiddenEntryPointにフォールバックし、401を前提にしたドキュメント・
            // フロントの分岐と食い違うため登録する。
            .exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint))

            // エンドポイントごとのアクセス制御
            .authorizeHttpRequests(auth -> auth
                // /api/auth/** は認証不要（ログイン・サインアップはトークンなしで呼べる）
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()  // ALBのヘルスチェック用に追加
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()  // 追加
                // それ以外は認証必須
                .anyRequest().authenticated())

            // JwtAuthenticationFilter を Spring Security 標準の認証フィルターの前に挟む
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // application.yaml で指定したオリジンのみ許可
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 全パスに対してCORS設定を適用
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
