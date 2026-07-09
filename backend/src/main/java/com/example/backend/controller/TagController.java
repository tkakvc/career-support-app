package com.example.backend.controller;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Controller / Service / Repository に分けるか
//   → 各レイヤーが単一の責務を持つことでテストしやすく変更に強い構造になる（単一責任の原則）。
//     Controller はHTTPの入出力だけ、Service は「defaultタグは編集不可」などのビジネスルールだけを担当する。
// 【AI任せでOK】@RestController / @RequestMapping / @RequiredArgsConstructor などのアノテーションの書き方
// ============================================================
import com.example.backend.dto.request.TagCreateRequest;
import com.example.backend.dto.request.TagUpdateRequest;
import com.example.backend.dto.response.DeleteResponse;
import com.example.backend.dto.response.TagResponse;
import com.example.backend.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public List<TagResponse> getTags(@AuthenticationPrincipal UUID userId) {
        return tagService.getVisibleTags(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse createTag(@AuthenticationPrincipal UUID userId,
                                 @Valid @RequestBody TagCreateRequest request) {
        return tagService.createTag(userId, request);
    }

    @PutMapping("/{id}")
    public TagResponse updateTag(@AuthenticationPrincipal UUID userId,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody TagUpdateRequest request) {
        return tagService.updateTag(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse deleteTag(@AuthenticationPrincipal UUID userId,
                                    @PathVariable UUID id) {
        return tagService.deleteTag(userId, id);
    }
}
