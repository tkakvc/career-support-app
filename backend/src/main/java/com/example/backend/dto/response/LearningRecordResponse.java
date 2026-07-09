package com.example.backend.dto.response;

import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
public class LearningRecordResponse {

    private final UUID id;
    private final UUID userId;
    private final LocalDate date;
    private final String content;
    private final Integer duration;
    private final List<TagResponse> tags;
    private final LocalDateTime createdAt;

    // Entity → Response への変換をコンストラクタで行う
    // Service 層で new LearningRecordResponse(entity) と呼ぶ
    public LearningRecordResponse(LearningRecord entity) {
        this.id = entity.getId();
        this.userId = entity.getUserId();
        this.date = entity.getDate();
        this.content = entity.getContent();
        this.duration = entity.getDuration();
        this.tags = entity.getTags().stream()
                .map(TagResponse::new)
                .toList();
        this.createdAt = entity.getCreatedAt();
    }

    // タグ情報を返す内部クラス
    @Getter
    public static class TagResponse {

        private final UUID id;
        private final String name;
        private final String type;
        private final UUID createdBy;

        public TagResponse(Tag tag) {
            this.id = tag.getId();
            this.name = tag.getName();
            this.type = tag.getType();
            this.createdBy = tag.getCreatedBy(); // default タグは null になる
        }
    }
}
