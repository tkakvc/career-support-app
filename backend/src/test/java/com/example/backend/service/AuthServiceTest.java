package com.example.backend.service;

import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.request.SignupRequest;
import com.example.backend.dto.response.AccessTokenResponse;
import com.example.backend.dto.response.AuthResponse;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.security.RefreshTokenService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

// UserServiceTest・TagServiceTest と同じ方針：@SpringBootTest は使わず、
// Repository/PasswordEncoder/JwtTokenProvider/RefreshTokenService を Mock に差し替えて
// Service のロジックだけを検証する。
//
// LoginRequest・SignupRequest には @AllArgsConstructor が無く newできないので、
// UserServiceTest の UpdatePasswordRequest と同じく mock() して getter だけ差し替える
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private final UUID userId = UUID.randomUUID();

    private User buildUser(String passwordHash) {
        return User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash(passwordHash)
                .displayName("テストユーザー")
                .build();
    }

    // issueTokens() 内で毎回呼ばれる4つのモックをまとめてスタブする
    // （アクセストークン生成・有効期限取得・リフレッシュトークン発行・有効期限取得）
    private void stubTokenIssuing() {
        given(jwtTokenProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtTokenProvider.getAccessExpirationSeconds()).willReturn(3600L);
        given(refreshTokenService.issue(userId)).willReturn("refresh-token");
        given(refreshTokenService.getRefreshExpirationSeconds()).willReturn(1209600L);
    }

    // ── signup ───────────────────────────────────────────────────
    @Nested
    class Signup {

        @Test
        void 新規登録してアクセストークンとリフレッシュトークンを発行する() {
            // given
            given(userRepository.findByEmail("new@example.com")).willReturn(Optional.empty());
            given(passwordEncoder.encode("password123")).willReturn("hashed-pw");
            given(userRepository.save(any(User.class))).willAnswer(inv -> {
                User saved = inv.getArgument(0);
                saved.setId(userId);
                return saved;
            });
            stubTokenIssuing();

            SignupRequest request = mock(SignupRequest.class);
            given(request.getEmail()).willReturn("new@example.com");
            given(request.getPassword()).willReturn("password123");
            given(request.getDisplayName()).willReturn("テストユーザー");

            // when
            AuthResponse result = authService.signup(request);

            // then
            assertThat(result.getAccessToken()).isEqualTo("access-token");
            assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
            then(userRepository).should(times(1)).save(any(User.class));
        }

        @Test
        void 既に登録済みのメールアドレスなら409を返す() {
            // given
            given(userRepository.findByEmail("exists@example.com"))
                    .willReturn(Optional.of(buildUser("hashed-old")));

            SignupRequest request = mock(SignupRequest.class);
            given(request.getEmail()).willReturn("exists@example.com");

            // when / then
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

            then(userRepository).should(never()).save(any());
        }
    }

    // ── login ────────────────────────────────────────────────────
    @Nested
    class Login {

        @Test
        void メールとパスワードが正しければトークンを発行する() {
            // given
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(buildUser("hashed-pw")));
            given(passwordEncoder.matches("password123", "hashed-pw")).willReturn(true);
            stubTokenIssuing();

            LoginRequest request = mock(LoginRequest.class);
            given(request.getEmail()).willReturn("user@example.com");
            given(request.getPassword()).willReturn("password123");

            // when
            AuthResponse result = authService.login(request);

            // then
            assertThat(result.getAccessToken()).isEqualTo("access-token");
            assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        }

        @Test
        void メールアドレスが存在しなければ401を返す() {
            // given
            given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

            LoginRequest request = mock(LoginRequest.class);
            given(request.getEmail()).willReturn("unknown@example.com");

            // when / then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
        }

        @Test
        void パスワードが一致しなければ401を返す() {
            // given：メールが存在するかどうかの手がかりを与えないため、存在しない場合と同じ401＆同じメッセージにする
            given(userRepository.findByEmail("user@example.com"))
                    .willReturn(Optional.of(buildUser("hashed-pw")));
            given(passwordEncoder.matches("wrong-password", "hashed-pw")).willReturn(false);

            LoginRequest request = mock(LoginRequest.class);
            given(request.getEmail()).willReturn("user@example.com");
            given(request.getPassword()).willReturn("wrong-password");

            // when / then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException e = (ResponseStatusException) ex;
                        assertThat(e.getStatusCode().value()).isEqualTo(401);
                        assertThat(e.getReason()).isEqualTo("メールアドレスまたはパスワードが正しくありません");
                    });
        }
    }

    // ── refresh ──────────────────────────────────────────────────
    @Nested
    class Refresh {

        @Test
        void 有効なリフレッシュトークンなら新しいアクセストークンを返す() {
            // given
            given(refreshTokenService.findUserId("valid-token")).willReturn(Optional.of(userId));
            given(jwtTokenProvider.generateAccessToken(userId)).willReturn("new-access-token");
            given(jwtTokenProvider.getAccessExpirationSeconds()).willReturn(3600L);

            // when
            AccessTokenResponse result = authService.refresh("valid-token");

            // then
            assertThat(result.getAccessToken()).isEqualTo("new-access-token");
            assertThat(result.getExpiresIn()).isEqualTo(3600L);
        }

        @Test
        void 無効なリフレッシュトークンなら401を返す() {
            // given：失効済み・期限切れ・でたらめな文字列、いずれもfindUserIdが空を返すので区別しない
            given(refreshTokenService.findUserId("invalid-token")).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> authService.refresh("invalid-token"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
        }
    }

    // ── logout ───────────────────────────────────────────────────
    @Nested
    class Logout {

        @Test
        void 渡されたリフレッシュトークンを失効させる() {
            // when
            authService.logout("some-token");

            // then
            then(refreshTokenService).should(times(1)).revoke("some-token");
        }
    }
}
