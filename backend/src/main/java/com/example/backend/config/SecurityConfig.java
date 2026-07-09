package com.example.backend.config;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Spring Security を使うか
//   → 認証（ログイン済みか）・認可（そのURLにアクセスできるか）の仕組みを自前実装すると
//     セキュリティホールを作りやすい。Spring Security はそれを標準的な方法で提供してくれる。
//     「/api/auth/** は認証不要、それ以外は認証必須」という設定を数行で書ける。
// 【面接で説明できるようにする】なぜセッションを使わずステートレス（JWT）にするか
//   → セッションはサーバー側に状態を持つため、サーバーを増やしたとき（スケールアウト）に
//     サーバー間でセッションを共有する仕組みが必要になる。
//     JWT はトークン自体に情報が入っていてサーバーが状態を持たないため、
//     どのサーバーに来ても同じように検証できる（ステートレス）。
// 【面接で説明できるようにする】なぜ CSRF を無効化するか
//   → CSRF攻撃はブラウザが Cookie を自動送信する仕組みを悪用する。
//     このアプリは Cookie を使わず Authorization ヘッダーで JWT を送るため、
//     CSRF が成立しない。無効化することで不要な制約を取り除いている。
// 【AI任せでOK】@EnableWebSecurity / http.build() などのボイラープレートな書き方
// ============================================================
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

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRFを無効化: REST APIはCookieでセッション管理しないため不要。
            // CSRFはブラウザがCookieを自動送信する仕組みを悪用した攻撃で、
            // JWTをAuthorizationヘッダーで送る方式では成立しない。
            .csrf(csrf -> csrf.disable())

            // CORS: 異なるオリジン（ドメイン・ポート）からのリクエストをブラウザが許可するかのルール。
            // フロント(localhost:3000)からバックエンド(localhost:8080)へのリクエストは
            // ポートが違うだけで「別オリジン」と判断され、デフォルトでブロックされる。
            // corsConfigurationSource() で「どのオリジンを許可するか」を定義し、ここで Spring Security に渡している。
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // セッションを使わない設定（ステートレス）
            // サーバーがセッションを保持しないため、毎回JWTで認証する
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // エンドポイントごとのアクセス制御
            .authorizeHttpRequests(auth -> auth
                // /api/auth/** は認証不要（ログイン・サインアップはトークンなしで呼べる）
                .requestMatchers("/api/auth/**").permitAll()
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
