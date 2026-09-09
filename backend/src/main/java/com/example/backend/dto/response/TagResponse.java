package com.example.backend.dto.response;

import com.example.backend.entity.Tag;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class TagResponse {

    private final UUID id;
    private final String name;
    private final String type;
    private final UUID createdBy;
    private final LocalDateTime createdAt;

    public TagResponse(Tag tag) {
        this.id = tag.getId();
        this.name = tag.getName();
        this.type = tag.getType();
        this.createdBy = tag.getCreatedBy();
        this.createdAt = tag.getCreatedAt();
    }
}
