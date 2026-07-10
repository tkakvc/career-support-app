package com.example.backend.controller;

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
