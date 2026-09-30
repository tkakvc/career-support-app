package com.example.backend.controller;

import com.example.backend.dto.request.ReferenceRequest;
import com.example.backend.dto.response.AiJobAcceptedResponse;
import com.example.backend.dto.response.AiJobResponse;
import com.example.backend.dto.response.AiReferenceListResponse;
import com.example.backend.service.AiReferenceService;
import com.example.backend.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiReferenceService aiReferenceService;

    @PostMapping("/references")
    public ResponseEntity<AiJobAcceptedResponse> generateReference(@AuthenticationPrincipal UUID userId,
                                                                     @Valid @RequestBody(required = false) ReferenceRequest request) {
        String interest = request == null ? null : request.getInterest();
        UUID recordId = request == null ? null : request.getRecordId();
        AiJobAcceptedResponse response = aiService.enqueueReference(userId, interest, recordId);
        // 学習記録0件かつinterest未入力の場合はjobIdがnullで、その場での固定メッセージを含む形になる
        HttpStatus status = response.getJobId() == null ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/references")
    public AiReferenceListResponse listReferences(@AuthenticationPrincipal UUID userId,
                                                    @RequestParam(required = false) UUID tag) {
        return aiReferenceService.list(userId, tag);
    }

    @GetMapping("/jobs/{jobId}")
    public AiJobResponse getJob(@AuthenticationPrincipal UUID userId, @PathVariable UUID jobId) {
        return aiService.getJob(userId, jobId);
    }
}
