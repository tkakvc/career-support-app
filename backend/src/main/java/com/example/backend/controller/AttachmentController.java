package com.example.backend.controller;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Controller には処理を書かず Service に委譲するか（レイヤードアーキテクチャ）
//   → Controller の責務は「HTTPリクエストを受け取りレスポンスを返す」ことだけ。
//     ビジネスロジック（所有者チェック・ファイルサイズ検証など）を Controller に書くと
//     テストが書きにくく、再利用もしにくくなる。
// 【面接で説明できるようにする】なぜ @AuthenticationPrincipal で userId を受け取るか
//   → JwtAuthenticationFilter が JWT を検証し SecurityContext に userId をセットしている。
//     @AuthenticationPrincipal はその値を引数として受け取る仕組み。
//     Controller のメソッドが userId を自分で取り出さなくていいので、コードがシンプルになる。
// 【AI任せでOK】@RestController / @RequestMapping / @PathVariable などのアノテーションの書き方
// ============================================================
import com.example.backend.dto.response.AttachmentDownload;
import com.example.backend.dto.response.AttachmentResponse;
import com.example.backend.dto.response.DeleteResponse;
import com.example.backend.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/learning-records/{learningRecordId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public List<AttachmentResponse> getList(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId) {
        return attachmentService.getList(userId, learningRecordId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId,
            @RequestParam MultipartFile file) {
        return attachmentService.upload(userId, learningRecordId, file);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId,
            @PathVariable UUID attachmentId) {
        AttachmentDownload dl = attachmentService.download(userId, learningRecordId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.fileName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(dl.fileSize()))
                .body(dl.bytes());
    }

    @DeleteMapping("/{attachmentId}")
    public DeleteResponse delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID learningRecordId,
            @PathVariable UUID attachmentId) {
        attachmentService.delete(userId, learningRecordId, attachmentId);
        return new DeleteResponse();
    }
}
