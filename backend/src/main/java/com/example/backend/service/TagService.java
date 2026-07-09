package com.example.backend.service;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Controller には処理を書かず Service に書くか（レイヤードアーキテクチャ）
//   →「defaultタグは編集・削除不可」「他のユーザーのタグは変更不可」「同名タグは重複不可」といった
//     ビジネスルールはすべて Service の責務。Controller に書くと肥大化してテストが書けなくなる。
// 【面接で説明できるようにする】なぜ @Transactional を付けるか
//   → DB操作の途中で例外が起きたとき、途中まで保存された中途半端な状態にならないよう
//     自動でロールバックしてくれる（原子性の保証）。
//     readOnly = true を付けると読み取り専用と明示でき、パフォーマンスも上がる。
// 【AI任せでOK】@Transactional / @Service / @RequiredArgsConstructor の書き方
// ============================================================
import com.example.backend.dto.request.TagCreateRequest;
import com.example.backend.dto.request.TagUpdateRequest;
import com.example.backend.dto.response.DeleteResponse;
import com.example.backend.dto.response.TagResponse;
import com.example.backend.entity.Tag;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> getVisibleTags(UUID userId) {
        return tagRepository.findVisibleTags(userId).stream()
                .map(TagResponse::new)
                .toList();
    }

    @Transactional
    public TagResponse createTag(UUID userId, TagCreateRequest request) {
        // 同ユーザー内でタグ名が重複している場合は 409 を返す
        tagRepository.findByNameAndCreatedBy(request.getName(), userId).ifPresent(t -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同じ名前のタグが既に存在します");
        });

        Tag tag = Tag.builder()
                .name(request.getName())
                .type("user")
                .createdBy(userId)
                .build();

        return new TagResponse(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse updateTag(UUID userId, UUID id, TagUpdateRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("タグが見つかりません"));

        // default タグは編集不可
        if ("default".equals(tag.getType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "デフォルトタグは編集できません");
        }

        // 他ユーザーのタグは編集不可
        if (!userId.equals(tag.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーのタグは編集できません");
        }

        // 同ユーザー内で同名のタグが既に存在する場合は 409
        tagRepository.findByNameAndCreatedBy(request.getName(), userId)
                .filter(t -> !t.getId().equals(id))
                .ifPresent(t -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "同じ名前のタグが既に存在します");
                });

        tag.setName(request.getName());
        return new TagResponse(tagRepository.save(tag));
    }

    @Transactional
    public DeleteResponse deleteTag(UUID userId, UUID id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("タグが見つかりません"));

        if ("default".equals(tag.getType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "デフォルトタグは削除できません");
        }

        if (!userId.equals(tag.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーのタグは削除できません");
        }

        tagRepository.delete(tag);
        return new DeleteResponse();
    }
}
