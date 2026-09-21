package com.example.backend.service;

import com.example.backend.dto.request.UpdatePasswordRequest;
import com.example.backend.dto.request.UpdateProfileRequest;
import com.example.backend.dto.response.PasswordUpdateResponse;
import com.example.backend.dto.response.UserProfileResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.UserRepository;
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
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        return new UserProfileResponse(findUserOrThrow(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);
        user.setDisplayName(request.getDisplayName());
        return new UserProfileResponse(userRepository.save(user));
    }

    @Transactional
    public PasswordUpdateResponse updatePassword(UUID userId, UpdatePasswordRequest request) {
        User user = findUserOrThrow(userId);

        // 現在のパスワードが一致しない → 403（401ではない。JWTで本人確認は済んでいるため）
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "現在のパスワードが正しくありません");
        }

        // 新しいパスワードが現在のパスワードと同一 → 400
        // currentPassword は直前で照合済みなので、newPassword とそのまま文字列比較すればよい
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新しいパスワードは現在のパスワードと異なるものにしてください");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 他端末で不正ログインされていた場合に備え、このユーザーの全リフレッシュトークンを失効させる。
        // 操作した本人の端末も対象になる（docs/settings/api/request-response.md の設計通り）。
        refreshTokenService.revokeAllForUser(userId);

        return new PasswordUpdateResponse();
    }

    private User findUserOrThrow(UUID userId) {
        // 404 は理論上発生しない：userId は JWT 検証を通過した本人のIDであり、
        // トークン発行時点で存在するユーザーだったことが保証されている。
        // それでも呼び出し可能なメソッドである以上、想定外の状態（削除済み等）に
        // 倒れずエラーとして扱えるよう防御的に例外を投げておく。
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("ユーザーが見つかりません"));
    }
}
