package com.example.backend.service;

import com.example.backend.dto.request.LearningRecordCreateRequest;
import com.example.backend.dto.request.LearningRecordSearchCriteria;
import com.example.backend.dto.request.LearningRecordUpdateRequest;
import com.example.backend.dto.response.DeleteResponse;
import com.example.backend.dto.response.LearningRecordResponse;
import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.LearningRecordRepository;
import com.example.backend.repository.TagRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class LearningRecordServiceTest {

    @Mock
    private LearningRecordRepository learningRecordRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private LearningRecordService learningRecordService;

    private final UUID userId   = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();
    private final UUID tagId    = UUID.randomUUID();

    private LearningRecord buildRecord(UUID id, UUID owner) {
        return LearningRecord.builder()
                .id(id)
                .userId(owner)
                .date(LocalDate.of(2024, 1, 1))
                .content("Javaの学習")
                .duration(60)
                .build();
    }

    private Tag buildTag(UUID id, String name) {
        return Tag.builder().id(id).name(name).type("user").createdBy(userId).build();
    }

    // ── getLearningRecords ───────────────────────────────────────────
    @Nested
    class GetLearningRecords {

        @Test
        void リポジトリが返した記録をLearningRecordResponseに変換して返す() {
            // given
            List<LearningRecord> records = List.of(
                    buildRecord(UUID.randomUUID(), userId),
                    buildRecord(UUID.randomUUID(), userId)
            );
            given(learningRecordRepository.findAll(ArgumentMatchers.<Specification<LearningRecord>>any(), any(Sort.class)))
                    .willReturn(records);

            LearningRecordSearchCriteria criteria = new LearningRecordSearchCriteria();

            // when
            List<LearningRecordResponse> result = learningRecordService.getLearningRecords(userId, criteria);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getContent()).isEqualTo("Javaの学習");
        }
    }

    // ── getById ──────────────────────────────────────────────────────
    @Nested
    class GetById {

        @Test
        void 正常に取得できる() {
            // given
            LearningRecord record = buildRecord(recordId, userId);
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.of(record));

            // when
            LearningRecordResponse result = learningRecordService.getById(userId, recordId);

            // then
            assertThat(result.getId()).isEqualTo(recordId);
            assertThat(result.getContent()).isEqualTo("Javaの学習");
        }

        @Test
        void 存在しないIDは404を返す() {
            // given
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> learningRecordService.getById(userId, recordId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("学習記録が見つかりません");
        }

        @Test
        void 他ユーザーの記録は403を返す() {
            // given
            UUID otherUserId = UUID.randomUUID();
            given(learningRecordRepository.findById(recordId))
                    .willReturn(Optional.of(buildRecord(recordId, otherUserId)));

            // when / then
            assertThatThrownBy(() -> learningRecordService.getById(userId, recordId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        }
    }

    // ── createLearningRecord ─────────────────────────────────────────
    @Nested
    class CreateLearningRecord {

        @Test
        void タグなしで正常に作成できる() {
            // given
            // tagIds が null → タグなしで作成する
            LearningRecordCreateRequest request = mock(LearningRecordCreateRequest.class);
            given(request.getTagIds()).willReturn(null);
            given(request.getDate()).willReturn(LocalDate.of(2024, 1, 1));
            given(request.getContent()).willReturn("Javaの学習");
            given(request.getDuration()).willReturn(60);

            LearningRecord saved = buildRecord(recordId, userId);
            given(learningRecordRepository.save(any(LearningRecord.class))).willReturn(saved);

            // when
            LearningRecordResponse result = learningRecordService.createLearningRecord(userId, request);

            // then
            assertThat(result.getContent()).isEqualTo("Javaの学習");
            then(learningRecordRepository).should(times(1)).save(any(LearningRecord.class));
        }

        @Test
        void タグありで正常に作成できる() {
            // given
            List<UUID> tagIds = List.of(tagId);
            LearningRecordCreateRequest request = mock(LearningRecordCreateRequest.class);
            given(request.getTagIds()).willReturn(tagIds);
            given(request.getDate()).willReturn(LocalDate.of(2024, 1, 1));
            given(request.getContent()).willReturn("Javaの学習");
            given(request.getDuration()).willReturn(60);

            Tag tag = buildTag(tagId, "Java");
            given(tagRepository.findAllById(tagIds)).willReturn(List.of(tag));

            LearningRecord saved = LearningRecord.builder()
                    .id(recordId)
                    .userId(userId)
                    .date(LocalDate.of(2024, 1, 1))
                    .content("Javaの学習")
                    .duration(60)
                    .tags(List.of(tag))
                    .build();
            given(learningRecordRepository.save(any(LearningRecord.class))).willReturn(saved);

            // when
            LearningRecordResponse result = learningRecordService.createLearningRecord(userId, request);

            // then
            assertThat(result.getTags()).hasSize(1);
            assertThat(result.getTags().get(0).getName()).isEqualTo("Java");
        }

        @Test
        void 存在しないtagIdが含まれる場合は404を返す() {
            // given
            // tagIds に 2件指定しているが findAllById が 1件しか返さない → 404
            List<UUID> tagIds = List.of(tagId, UUID.randomUUID());
            LearningRecordCreateRequest request = mock(LearningRecordCreateRequest.class);
            given(request.getTagIds()).willReturn(tagIds);
            given(tagRepository.findAllById(tagIds)).willReturn(List.of(buildTag(tagId, "Java")));

            // when / then
            assertThatThrownBy(() -> learningRecordService.createLearningRecord(userId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("指定されたタグが見つかりません");
        }
    }

    // ── update ───────────────────────────────────────────────────────
    @Nested
    class Update {

        @Test
        void 正常に更新できる() {
            // given
            LearningRecord existing = buildRecord(recordId, userId);
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.of(existing));

            LearningRecordUpdateRequest request = mock(LearningRecordUpdateRequest.class);
            given(request.getDate()).willReturn(LocalDate.of(2024, 6, 1));
            given(request.getContent()).willReturn("更新後の内容");
            given(request.getDuration()).willReturn(90);
            given(request.getTagIds()).willReturn(null); // null のとき既存タグを維持

            LearningRecord saved = LearningRecord.builder()
                    .id(recordId)
                    .userId(userId)
                    .date(LocalDate.of(2024, 6, 1))
                    .content("更新後の内容")
                    .duration(90)
                    .build();
            given(learningRecordRepository.save(any(LearningRecord.class))).willReturn(saved);

            // when
            LearningRecordResponse result = learningRecordService.update(userId, recordId, request);

            // then
            assertThat(result.getContent()).isEqualTo("更新後の内容");
            assertThat(result.getDuration()).isEqualTo(90);
        }

        @Test
        void 存在しないIDは404を返す() {
            // given
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.empty());
            LearningRecordUpdateRequest request = mock(LearningRecordUpdateRequest.class);

            // when / then
            assertThatThrownBy(() -> learningRecordService.update(userId, recordId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("学習記録が見つかりません");
        }

        @Test
        void 他ユーザーの記録は403を返す() {
            // given
            UUID otherUserId = UUID.randomUUID();
            given(learningRecordRepository.findById(recordId))
                    .willReturn(Optional.of(buildRecord(recordId, otherUserId)));
            LearningRecordUpdateRequest request = mock(LearningRecordUpdateRequest.class);

            // when / then
            assertThatThrownBy(() -> learningRecordService.update(userId, recordId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        }

        @Test
        void tagIdsがnullのとき既存タグを維持しtagRepositoryを呼ばない() {
            // given
            // tagIds が null → tagRepository.findAllById を呼ばずに既存タグをそのまま維持する
            Tag existingTag = buildTag(tagId, "Java");
            LearningRecord existing = LearningRecord.builder()
                    .id(recordId)
                    .userId(userId)
                    .date(LocalDate.of(2024, 1, 1))
                    .content("Javaの学習")
                    .duration(60)
                    .tags(List.of(existingTag))
                    .build();
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.of(existing));

            LearningRecordUpdateRequest request = mock(LearningRecordUpdateRequest.class);
            given(request.getDate()).willReturn(LocalDate.of(2024, 1, 1));
            given(request.getContent()).willReturn("Javaの学習");
            given(request.getDuration()).willReturn(60);
            given(request.getTagIds()).willReturn(null);

            given(learningRecordRepository.save(any(LearningRecord.class))).willReturn(existing);

            // when
            LearningRecordResponse result = learningRecordService.update(userId, recordId, request);

            // then
            assertThat(result.getTags()).hasSize(1);
            then(tagRepository).should(never()).findAllById(any());
        }

        @Test
        void 存在しないtagIdが含まれる場合は404を返す() {
            // given
            LearningRecord existing = buildRecord(recordId, userId);
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.of(existing));

            // tagIds に 2件指定しているが findAllById が 1件しか返さない → 404
            List<UUID> tagIds = List.of(tagId, UUID.randomUUID());
            LearningRecordUpdateRequest request = mock(LearningRecordUpdateRequest.class);
            given(request.getDate()).willReturn(LocalDate.of(2024, 1, 1));
            given(request.getContent()).willReturn("Javaの学習");
            given(request.getDuration()).willReturn(60);
            given(request.getTagIds()).willReturn(tagIds);
            given(tagRepository.findAllById(tagIds)).willReturn(List.of(buildTag(tagId, "Java")));

            // when / then
            assertThatThrownBy(() -> learningRecordService.update(userId, recordId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("指定されたタグが見つかりません");
        }
    }

    // ── delete ───────────────────────────────────────────────────────
    @Nested
    class Delete {

        @Test
        void 正常に削除できる() {
            // given
            LearningRecord record = buildRecord(recordId, userId);
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.of(record));

            // when
            DeleteResponse result = learningRecordService.delete(userId, recordId);

            // then
            assertThat(result.getResult()).isEqualTo("deleted");
            then(learningRecordRepository).should(times(1)).delete(record);
        }

        @Test
        void 存在しないIDは404を返す() {
            // given
            given(learningRecordRepository.findById(recordId)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> learningRecordService.delete(userId, recordId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("学習記録が見つかりません");

            then(learningRecordRepository).should(never()).delete(any(LearningRecord.class));
        }

        @Test
        void 他ユーザーの記録は403を返す() {
            // given
            UUID otherUserId = UUID.randomUUID();
            given(learningRecordRepository.findById(recordId))
                    .willReturn(Optional.of(buildRecord(recordId, otherUserId)));

            // when / then
            assertThatThrownBy(() -> learningRecordService.delete(userId, recordId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

            then(learningRecordRepository).should(never()).delete(any(LearningRecord.class));
        }
    }
}
