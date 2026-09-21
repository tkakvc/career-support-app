package com.example.backend.controller;

import com.example.backend.dto.request.UpdatePasswordRequest;
import com.example.backend.dto.request.UpdateProfileRequest;
import com.example.backend.dto.response.PasswordUpdateResponse;
import com.example.backend.dto.response.UserProfileResponse;
import com.example.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// パスに {id} を持たせない：操作対象は常にトークンのユーザー自身であり、
// 他人のIDを指定して情報を書き換える経路をそもそも作らないため（docs/settings/api/endpoints.md）
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserProfileResponse getProfile(@AuthenticationPrincipal UUID userId) {
        return userService.getProfile(userId);
    }

    @PatchMapping
    public UserProfileResponse updateProfile(@AuthenticationPrincipal UUID userId,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }

    @PutMapping("/password")
    public PasswordUpdateResponse updatePassword(@AuthenticationPrincipal UUID userId,
                                                 @Valid @RequestBody UpdatePasswordRequest request) {
        return userService.updatePassword(userId, request);
    }
}
