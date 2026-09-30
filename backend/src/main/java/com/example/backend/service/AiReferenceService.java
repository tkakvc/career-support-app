package com.example.backend.service;

import com.example.backend.dto.response.AiReferenceListResponse;
import com.example.backend.dto.response.AiReferenceResponse;
import com.example.backend.entity.AiReference;
import com.example.backend.repository.AiReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// GET /api/ai/references（保存済み参考資料の一覧取得）専用のサービス。
// 生成（非同期ジョブ・ガードレール）はAiServiceの責務、こちらは単純な読み取りのみなので分けている。
@Service
@RequiredArgsConstructor
public class AiReferenceService {

    private final AiReferenceRepository aiReferenceRepository;

    @Transactional(readOnly = true)
    public AiReferenceListResponse list(UUID userId, UUID tagId) {
        List<AiReference> references = tagId == null
                ? aiReferenceRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : aiReferenceRepository.findByUserIdAndTagIdOrderByCreatedAtDesc(userId, tagId);

        return new AiReferenceListResponse(references.stream().map(AiReferenceResponse::from).toList());
    }
}
