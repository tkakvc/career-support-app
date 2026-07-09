package com.example.backend.controller;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Controller には処理を書かず Service に委譲するか（レイヤードアーキテクチャ）
//   → Controller の役割は「URLとメソッドを対応させること」だけ。
//     「そのユーザーがそのレコードを所有しているか」などのビジネスロジックは Service に書く。
//     こうすることで Controller と Service を独立してテストできる。
// 【面接で説明できるようにする】なぜ userId を @AuthenticationPrincipal で受け取るか
//   → JWT から取り出した userId を SecurityContext 経由で受け取る。
//     リクエストボディで userId を受け取ると、他人の userId を渡すなりすましができてしまう。
//     サーバー側で JWT を検証して取り出すことで、なりすましを防げる。
// 【AI任せでOK】@GetMapping / @PostMapping / @PutMapping / @DeleteMapping の書き方
// ============================================================
import com.example.backend.dto.request.LearningRecordCreateRequest;
import com.example.backend.dto.request.LearningRecordSearchCriteria;
import com.example.backend.dto.request.LearningRecordUpdateRequest;
import com.example.backend.dto.response.DeleteResponse;
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

    @PutMapping("/{id}")
    public LearningRecordResponse update(@AuthenticationPrincipal UUID userId,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody LearningRecordUpdateRequest request) {
        return learningRecordService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return learningRecordService.delete(userId, id);
    }
}
