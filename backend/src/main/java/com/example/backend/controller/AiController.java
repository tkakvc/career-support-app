package com.example.backend.controller;

import com.example.backend.dto.request.DecomposeRequest;
import com.example.backend.dto.response.DecomposeResponse;
import com.example.backend.dto.response.SuggestResponse;
import com.example.backend.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/suggest")
    public SuggestResponse suggest(@AuthenticationPrincipal UUID userId) {
        return aiService.suggest(userId);
    }

    @PostMapping("/decompose")
    public DecomposeResponse decompose(@AuthenticationPrincipal UUID userId,
                                       @Valid @RequestBody DecomposeRequest request) {
        return aiService.decompose(userId, request.getGoal());
    }
}
