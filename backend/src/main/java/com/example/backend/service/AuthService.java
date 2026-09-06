package com.example.backend.service;

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
// final フィールドを引数に取るコンストラクタをLombokが自動生成 → @Autowired 不要
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // findByEmail は Optional<User> を返す
        // 同じメールアドレスが既に存在する場合は 409 Conflict を返す
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "このメールアドレスはすでに登録されています");
        }

        User user = User.builder()
                .email(request.getEmail())
                // パスワードは平文のまま保存せず、BCryptでハッシュ化してから保存する
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .build();

        userRepository.save(user);
        // 登録と同時にアクセストークン＋リフレッシュトークンを発行して返す
        return issueTokens(user.getId());
    }

    // リフレッシュトークンを Redis に保存するため、readOnly にはしない
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // メールアドレスでユーザーを検索。見つからなければ 401 を返す
        User user = userRepository.findByEmail(request.getEmail())
                // orElseThrow() で Optional<User> から User を取り出す。見つからない場合は例外を投げる
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"));

        // BCrypt の matches() は「平文パスワード」と「DBのハッシュ」を比較する
        // 「メールが見つからない場合」と同じエラーメッセージにすることで、どちらが間違いかを攻撃者に知らせない
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
        }

        return issueTokens(user.getId());
    }

    // リフレッシュトークンから新しいアクセストークンを再発行する。無効なら 401 を返す
    public AccessTokenResponse refresh(String refreshToken) {
        UUID userId = refreshTokenService.findUserId(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "リフレッシュトークンが無効です"));

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        return new AccessTokenResponse(accessToken, jwtTokenProvider.getAccessExpirationSeconds());
    }

    // リフレッシュトークンを Redis から削除して失効させる（ログアウト）
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    // アクセストークンとリフレッシュトークンをまとめて発行する
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
