package com.example.backend.service;

import com.example.backend.dto.request.LoginRequest;
import com.example.backend.dto.request.SignupRequest;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
// final フィールド(userRepository, passwordEncoder, jwtTokenProvider)を引数に取るコンストラクタをLombokが自動生成 → @Autowired 不要
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String signup(SignupRequest request) {
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
        // 保存後に採番された userId を使って JWT を生成して返す
        return jwtTokenProvider.generateToken(user.getId());
    }

    // DB読み取りのみなので readOnly = true（パフォーマンス最適化・誤った書き込みの防止）
    @Transactional(readOnly = true)
    public String login(LoginRequest request) {
        // メールアドレスでユーザーを検索。見つからなければ 401 を返す
        User user = userRepository.findByEmail(request.getEmail())
                // orElseThrow() で Optional<User> から User を取り出す。見つからない場合は例外を投げる
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"));

        // BCrypt の matches() は「平文パスワード」と「DBのハッシュ」を比較する
        // 「メールが見つからない場合」と同じエラーメッセージにすることで、どちらが間違いかを攻撃者に知らせない
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません");
        }

        return jwtTokenProvider.generateToken(user.getId());
    }
}
