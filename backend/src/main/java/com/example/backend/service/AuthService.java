package com.example.backend.service;

// ============================================================
// 【このファイル全体の方針】
// 認証のビジネスロジック。方式変更に伴い、トークン発行が「アクセス＋リフレッシュの2種類」になった。
//   アクセストークン：JwtTokenProvider が発行（短命JWT）
//   リフレッシュトークン：RefreshTokenService が発行しRedisに保存（長命・失効可能）
//
// 【面接で説明できるようにする】なぜ BCrypt でパスワードをハッシュ化するか
//   → 平文保存はDB漏洩時に即バレる。BCryptは意図的に低速＆自動ソルトで、総当たり/レインボーテーブルに強い。
// 【面接で説明できるようにする】なぜログイン失敗のメッセージを共通化するか
//   → 「メール無し」「パスワード違い」を分けると、メールの登録有無を攻撃者に教えてしまう（ユーザー列挙攻撃）。
// 【面接で説明できるようにする】ログアウトの仕組み
//   → リフレッシュトークンをRedisから削除する＝再発行できなくする。
//     アクセストークンは短命(15分)なので、残り時間が過ぎれば自然に無効になる。
// 【AI任せでOK】@Transactional / passwordEncoder / Optional の書き方
// ============================================================
import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.request.SignupRequest;
import com.example.backend.dto.response.AccessTokenResponse;
import com.example.backend.dto.response.AuthResponse;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "このメールアドレスはすでに登録されています");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .build();

        userRepository.save(user);
        // 登録完了と同時にログイン状態にする（2種類のトークンを発行して返す）
        return issueTokens(user.getId());
    }

    // ログイン処理。DB読み取りに加えてRedisへの書き込み(リフレッシュトークン保存)があるので readOnly にはしない
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
        }

        return issueTokens(user.getId());
    }

    // リフレッシュトークンを使ってアクセストークンを再発行する。
    // Redisにトークンが存在しなければ（失効済み・期限切れ・偽物）401を返す。
    public AccessTokenResponse refresh(String refreshToken) {
        UUID userId = refreshTokenService.findUserId(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "リフレッシュトークンが無効です"));

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        return new AccessTokenResponse(accessToken, jwtTokenProvider.getAccessExpirationSeconds());
    }

    // ログアウト。リフレッシュトークンをRedisから削除して失効させる。
    // 既に無効なトークンを渡されても、削除は何も起きないだけなのでエラーにはしない（冪等）。
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    // アクセストークン＋リフレッシュトークンをまとめて発行する共通処理
    private AuthResponse issueTokens(UUID userId) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = refreshTokenService.issue(userId);
        return new AuthResponse(
                accessToken,
                jwtTokenProvider.getAccessExpirationSeconds(),
                refreshToken,
                refreshTokenService.getRefreshExpirationSeconds());
    }
}
