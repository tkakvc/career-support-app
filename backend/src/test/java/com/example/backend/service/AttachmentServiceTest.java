package com.example.backend.service;

import com.example.backend.dto.response.AttachmentDownload;
import com.example.backend.dto.response.AttachmentResponse;
import com.example.backend.entity.Attachment;
import com.example.backend.entity.LearningRecord;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.AttachmentRepository;
import com.example.backend.repository.LearningRecordRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

// UserServiceTest・TagServiceTestと同じ方針：@SpringBootTestは使わず、
// Repository/StorageServiceをMockに差し替えてServiceのロジックだけを検証する。
// MultipartFileは本来HTTPリクエストから作られるものだが、テストではSpringが用意している
// MockMultipartFile（テスト専用の偽ファイル）で代用する
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private LearningRecordRepository learningRecordRepository;

    @Mock
    private StorageService storageService;

    private AttachmentService attachmentService;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID learningRecordId = UUID.randomUUID();
    private final UUID attachmentId = UUID.randomUUID();

    // @InjectMocksを使わず自分でnewする（コンストラクタ引数の順番に依存しないようにするため）
    private AttachmentService newService() {
        return new AttachmentService(attachmentRepository, learningRecordRepository, storageService);
    }

    private LearningRecord buildRecord(UUID ownerId) {
        return LearningRecord.builder().id(learningRecordId).userId(ownerId).build();
    }

    private Attachment buildAttachment() {
        return Attachment.builder()
                .id(attachmentId)
                .learningRecordId(learningRecordId)
                .fileName("memo.txt")
                .storageKey("attachments/" + userId + "/" + attachmentId + "/memo.txt")
                .contentType("text/plain")
                .fileSize(10L)
                .build();
    }

    // ── getList ──────────────────────────────────────────────────
    @Nested
    class GetList {

        @Test
        void 所有者本人なら添付ファイル一覧を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.findByLearningRecordId(learningRecordId)).willReturn(List.of(buildAttachment()));
            attachmentService = newService();

            // when
            List<AttachmentResponse> result = attachmentService.getList(userId, learningRecordId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo("memo.txt");
        }

        @Test
        void 他ユーザーの学習記録なら403を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(otherUserId)));
            attachmentService = newService();

            // when / then
            assertThatThrownBy(() -> attachmentService.getList(userId, learningRecordId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        }

        @Test
        void 存在しない学習記録なら404を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.empty());
            attachmentService = newService();

            // when / then
            assertThatThrownBy(() -> attachmentService.getList(userId, learningRecordId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── upload ───────────────────────────────────────────────────
    @Nested
    class Upload {

        @Test
        void 正常なファイルならアップロードして保存する() throws IOException {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.countByLearningRecordId(learningRecordId)).willReturn(0L);
            attachmentService = newService();

            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "dummy-bytes".getBytes());

            // when
            AttachmentResponse result = attachmentService.upload(userId, learningRecordId, file);

            // then
            assertThat(result.getFileName()).isEqualTo("photo.png");
            then(attachmentRepository).should(times(1)).save(any(Attachment.class));
            then(storageService).should(times(1)).upload(anyString(), eq(file));
        }

        @Test
        void ファイルが空なら400を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            attachmentService = newService();

            MultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

            // when / then
            assertThatThrownBy(() -> attachmentService.upload(userId, learningRecordId, emptyFile))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
            then(attachmentRepository).should(never()).save(any());
        }

        @Test
        void ファイルサイズが10MB超なら400を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            attachmentService = newService();

            MultipartFile hugeFile = mock(MultipartFile.class);
            given(hugeFile.isEmpty()).willReturn(false);
            given(hugeFile.getSize()).willReturn(11L * 1024 * 1024);

            // when / then
            assertThatThrownBy(() -> attachmentService.upload(userId, learningRecordId, hugeFile))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
            then(attachmentRepository).should(never()).save(any());
        }

        @Test
        void 既に10件添付済みなら400を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.countByLearningRecordId(learningRecordId)).willReturn(10L);
            attachmentService = newService();

            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "dummy-bytes".getBytes());

            // when / then
            assertThatThrownBy(() -> attachmentService.upload(userId, learningRecordId, file))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
            then(attachmentRepository).should(never()).save(any());
        }

        @Test
        void 他ユーザーの学習記録には403を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(otherUserId)));
            attachmentService = newService();

            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "dummy-bytes".getBytes());

            // when / then
            assertThatThrownBy(() -> attachmentService.upload(userId, learningRecordId, file))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        }

        @Test
        void ストレージへの保存に失敗したら500を返す() throws IOException {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.countByLearningRecordId(learningRecordId)).willReturn(0L);
            willThrow(new IOException("S3障害")).given(storageService).upload(anyString(), any());
            attachmentService = newService();

            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "dummy-bytes".getBytes());

            // when / then
            assertThatThrownBy(() -> attachmentService.upload(userId, learningRecordId, file))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(500));
        }
    }

    // ── download ─────────────────────────────────────────────────
    @Nested
    class Download {

        @Test
        void 正常な添付ファイルならバイト列を返す() throws IOException {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.findById(attachmentId)).willReturn(Optional.of(buildAttachment()));
            given(storageService.download(anyString())).willReturn("dummy-bytes".getBytes());
            attachmentService = newService();

            // when
            AttachmentDownload result = attachmentService.download(userId, learningRecordId, attachmentId);

            // then
            assertThat(result.fileName()).isEqualTo("memo.txt");
            assertThat(result.bytes()).isEqualTo("dummy-bytes".getBytes());
        }

        @Test
        void 存在しない添付ファイルなら404を返す() {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.findById(attachmentId)).willReturn(Optional.empty());
            attachmentService = newService();

            // when / then
            assertThatThrownBy(() -> attachmentService.download(userId, learningRecordId, attachmentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void 別の学習記録に紐づく添付ファイルなら404を返す() {
            // given：URLのlearningRecordIdとattachmentが実際に紐づくlearningRecordIdが食い違うケース
            // （他人の記録IDを推測してこのURLを叩いても中身を見せない、という横断アクセス防止）
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            Attachment attachmentOfAnotherRecord = Attachment.builder()
                    .id(attachmentId)
                    .learningRecordId(UUID.randomUUID())
                    .fileName("memo.txt")
                    .build();
            given(attachmentRepository.findById(attachmentId)).willReturn(Optional.of(attachmentOfAnotherRecord));
            attachmentService = newService();

            // when / then
            assertThatThrownBy(() -> attachmentService.download(userId, learningRecordId, attachmentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void ストレージからの取得に失敗したら500を返す() throws IOException {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.findById(attachmentId)).willReturn(Optional.of(buildAttachment()));
            given(storageService.download(anyString())).willThrow(new IOException("S3障害"));
            attachmentService = newService();

            // when / then
            assertThatThrownBy(() -> attachmentService.download(userId, learningRecordId, attachmentId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(500));
        }
    }

    // ── delete ───────────────────────────────────────────────────
    @Nested
    class Delete {

        @Test
        void 正常な添付ファイルならストレージとDBの両方から削除する() throws IOException {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            Attachment attachment = buildAttachment();
            given(attachmentRepository.findById(attachmentId)).willReturn(Optional.of(attachment));
            attachmentService = newService();

            // when
            attachmentService.delete(userId, learningRecordId, attachmentId);

            // then：ストレージ→DBの順で削除される（孤立ファイルを残さないため）
            then(storageService).should(times(1)).delete(attachment.getStorageKey());
            then(attachmentRepository).should(times(1)).delete(attachment);
        }

        @Test
        void ストレージからの削除に失敗したら500を返しDBも削除しない() throws IOException {
            // given
            given(learningRecordRepository.findById(learningRecordId)).willReturn(Optional.of(buildRecord(userId)));
            given(attachmentRepository.findById(attachmentId)).willReturn(Optional.of(buildAttachment()));
            willThrow(new IOException("S3障害")).given(storageService).delete(anyString());
            attachmentService = newService();

            // when / then
            assertThatThrownBy(() -> attachmentService.delete(userId, learningRecordId, attachmentId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(500));
            then(attachmentRepository).should(never()).delete(any());
        }
    }

    // ── deleteAllByLearningRecordId ─────────────────────────────
    @Nested
    class DeleteAllByLearningRecordId {

        @Test
        void 紐づく添付ファイルを全て削除する() throws IOException {
            // given
            Attachment a1 = buildAttachment();
            Attachment a2 = Attachment.builder().id(UUID.randomUUID()).learningRecordId(learningRecordId)
                    .storageKey("attachments/other").build();
            given(attachmentRepository.findByLearningRecordId(learningRecordId)).willReturn(List.of(a1, a2));
            attachmentService = newService();

            // when
            attachmentService.deleteAllByLearningRecordId(learningRecordId);

            // then
            then(storageService).should(times(1)).delete(a1.getStorageKey());
            then(storageService).should(times(1)).delete(a2.getStorageKey());
            then(attachmentRepository).should(times(1)).deleteAll(List.of(a1, a2));
        }

        @Test
        void ストレージ削除が一部失敗してもDB削除は続行する() throws IOException {
            // given：ベストエフォート仕様の確認。片方のストレージ削除が例外を投げても、
            // 例外を握りつぶして最後まで処理を続け、DBのdeleteAllは必ず呼ばれる
            Attachment a1 = buildAttachment();
            Attachment a2 = Attachment.builder().id(UUID.randomUUID()).learningRecordId(learningRecordId)
                    .storageKey("attachments/other").build();
            given(attachmentRepository.findByLearningRecordId(learningRecordId)).willReturn(List.of(a1, a2));
            willThrow(new IOException("S3障害")).given(storageService).delete(a1.getStorageKey());
            attachmentService = newService();

            // when / then
            assertThatCode(() -> attachmentService.deleteAllByLearningRecordId(learningRecordId))
                    .doesNotThrowAnyException();
            then(attachmentRepository).should(times(1)).deleteAll(List.of(a1, a2));
        }
    }
}
