package com.example.backend.controller;

import com.example.backend.dto.request.LearningRecordCreateRequest;
import com.example.backend.dto.request.LearningRecordSearchCriteria;
import com.example.backend.dto.response.LearningRecordResponse;
import com.example.backend.service.LearningRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/learning-records")
@RequiredArgsConstructor
public class LearningRecordController {

    private final LearningRecordService learningRecordService;

    @GetMapping
    public List<LearningRecordResponse> getList(@AuthenticationPrincipal UUID userId,
                                                LearningRecordSearchCriteria criteria) {
        return learningRecordService.getLearningRecords(userId, criteria);
    }

    @GetMapping("/{id}")
    public LearningRecordResponse getById(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return learningRecordService.getById(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // @AuthenticationPrincipal: JwtAuthenticationFilter で SecurityContext にセットした userId を取り出す
    public LearningRecordResponse create(@AuthenticationPrincipal UUID userId,
                                         @Valid @RequestBody LearningRecordCreateRequest request) {
        return learningRecordService.createLearningRecord(userId, request);
    }
}
