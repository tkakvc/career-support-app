package com.example.backend.controller;

import com.example.backend.config.SecurityConfig;
import com.example.backend.dto.response.AttachmentDownload;
import com.example.backend.dto.response.AttachmentResponse;
import com.example.backend.entity.Attachment;
import com.example.backend.security.JwtAuthenticationEntryPoint;
import com.example.backend.security.JwtAuthenticationFilter;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.service.AttachmentService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ============================================================
// UserControllerTest・AuthControllerTestまでは「JSONのリクエストボディを送る」パターンだった。
// AttachmentControllerはそれと2点違う、新しい書き方が必要になる箇所を持っている。
//   ① upload：ファイルそのものを送る（multipart/form-data）
//   ② download：JSONではなくバイト列（ファイルの中身そのもの）を返す
// この2つの技術要素を確認したいのがこのテストの目的（他のControllerと同じ
// バリデーション・認証パターンの反復は避け、ここでしか見れない部分だけに絞る）
// ============================================================
@WebMvcTest(AttachmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttachmentService attachmentService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private final UUID userId = UUID.randomUUID();
    private final UUID learningRecordId = UUID.randomUUID();
    private final UUID attachmentId = UUID.randomUUID();

    private static final String DUMMY_TOKEN = "Bearer dummy-token";

    private void givenValidToken() {
        given(jwtTokenProvider.validateToken("dummy-token")).willReturn(true);
        given(jwtTokenProvider.extractUserId("dummy-token")).willReturn(userId);
    }

    private Attachment buildAttachment() {
        return Attachment.builder()
                .id(attachmentId)
                .learningRecordId(learningRecordId)
                .fileName("photo.png")
                .contentType("image/png")
                .fileSize(11L)
                .build();
    }

    // ── POST /api/learning-records/{id}/attachments（ファイルアップロード）──
    @Nested
    class Upload {

        @Test
        void ファイルをアップロードすると201でAttachmentResponseを返す() throws Exception {
            // given
            givenValidToken();
            given(attachmentService.upload(eq(userId), eq(learningRecordId), any()))
                    .willReturn(new AttachmentResponse(buildAttachment()));

            // MockMultipartFile：MockMvcでファイルアップロードを再現するための偽ファイル。
            // 引数は (リクエストパラメータ名, 元のファイル名, Content-Type, 中身のバイト列)
            MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "dummy-bytes".getBytes());

            // when / then：multipart(...)はJSON用のpost(...)とは別の専用リクエストビルダー。
            //   .file(file)でファイルパーツを、通常のJSONボディの代わりに添付する
            mockMvc.perform(multipart("/api/learning-records/{learningRecordId}/attachments", learningRecordId)
                            .file(file)
                            .header("Authorization", DUMMY_TOKEN))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fileName").value("photo.png"));
        }
    }

    // ── GET /api/learning-records/{id}/attachments/{attachmentId}/download（ダウンロード）──
    @Nested
    class Download {

        @Test
        void ダウンロードするとファイルの中身とヘッダーを返す() throws Exception {
            // given：AttachmentServiceがファイルの中身（バイト列）とメタ情報を返すよう仕込む
            givenValidToken();
            byte[] fileBytes = "dummy-bytes".getBytes();
            given(attachmentService.download(userId, learningRecordId, attachmentId))
                    .willReturn(new AttachmentDownload(fileBytes, "image/png", "photo.png", fileBytes.length));

            // when / then：レスポンスはJSONではなく生のバイト列なので、jsonPath(...)ではなく
            //   .andExpect(content().bytes(...))で中身を、header(...)でダウンロード用の
            //   ヘッダー（ブラウザに「これはphoto.pngというファイルです」と伝える部分）を確認する
            mockMvc.perform(get("/api/learning-records/{learningRecordId}/attachments/{attachmentId}/download",
                            learningRecordId, attachmentId)
                            .header("Authorization", DUMMY_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "attachment; filename=\"photo.png\""))
                    .andExpect(content().bytes(fileBytes));
        }
    }
}
