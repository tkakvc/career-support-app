package com.example.backend.service;

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
import java.util.Optional;
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

    // AI参考資料の分類用（AiService.generateResult）。既存の可視タグ（defaultタグ＋自分のuserタグ）に
    // 大文字小文字を無視して一致するものがあれば返すだけの読み取り専用メソッド。ここでタグを新規作成
    // しない。タグの作成は常にユーザーの明示的な操作を起点にすべきで（既存のcreateTag()も必ず
    // ユーザーの操作から呼ばれる）、非同期ジョブがAIの判断だけで勝手にタグを作るのは一貫性が無いため。
    // 一致しなかった場合、実際の新規作成は「学習記録をつける」等のユーザー操作時にcreateTag()を
    // 呼ぶ形で行う（AiService・AiReferenceにはタグ名の"提案"だけが保存される）。
    @Transactional(readOnly = true)
    public Optional<Tag> findMatchingVisibleTag(UUID userId, String name) {
        String normalized = name.strip();
        return tagRepository.findVisibleTags(userId).stream()
                .filter(t -> t.getName().equalsIgnoreCase(normalized))
                .findFirst();
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
