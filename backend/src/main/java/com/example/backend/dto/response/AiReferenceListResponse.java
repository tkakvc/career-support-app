package com.example.backend.dto.response;

import java.util.List;

// GET /api/ai/references のレスポンス
public record AiReferenceListResponse(List<AiReferenceResponse> references) {
}
