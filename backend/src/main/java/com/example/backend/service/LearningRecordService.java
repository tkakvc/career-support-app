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
import com.example.backend.repository.LearningRecordSpecification;
import com.example.backend.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearningRecordService {

    private final LearningRecordRepository learningRecordRepository;
    private final TagRepository tagRepository;
    private final AttachmentService attachmentService;

    @Transactional(readOnly = true)
    public List<LearningRecordResponse> getLearningRecords(UUID userId, LearningRecordSearchCriteria criteria) {

        Specification<LearningRecord> spec = LearningRecordSpecification.hasUserId(userId);

        if (criteria.getTag() != null)     spec = spec.and(LearningRecordSpecification.hasTagName(criteria.getTag()));
        if (criteria.getFrom() != null)    spec = spec.and(LearningRecordSpecification.fromDate(criteria.getFrom()));
        if (criteria.getTo() != null)      spec = spec.and(LearningRecordSpecification.toDate(criteria.getTo()));
        if (criteria.getKeyword() != null) spec = spec.and(LearningRecordSpecification.contentContains(criteria.getKeyword()));

        List<LearningRecord> records = learningRecordRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "date"));
        return records.stream().map(LearningRecordResponse::new).toList();
    }

    @Transactional(readOnly = true)
    public LearningRecordResponse getById(UUID userId, UUID id) {
        LearningRecord record = learningRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("学習記録が見つかりません"));
        if (!record.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの学習記録にはアクセスできません");
        }
        return new LearningRecordResponse(record);
    }

    @Transactional
    public LearningRecordResponse createLearningRecord(UUID userId, LearningRecordCreateRequest request) {

        // ① tagIds に対応する Tag を取得する
        // tagIds が null または空の場合はタグなしとして扱う
        List<Tag> tags = Collections.emptyList();
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            tags = tagRepository.findAllById(request.getTagIds());

            // ② 存在しない tagId が1件でも含まれていたら 404 を throw する
            // findAllById は存在しない ID を無視して返すため、件数で存在チェックする
            if (tags.size() != request.getTagIds().size()) {
                throw new ResourceNotFoundException("指定されたタグが見つかりません");
            }
        }

        // ③ LearningRecord を作成して保存する
        LearningRecord record = LearningRecord.builder()
                .userId(userId)
                .date(request.getDate())
                .content(request.getContent())
                .duration(request.getDuration())
                .tags(tags)
                .build();

        LearningRecord saved = learningRecordRepository.save(record);

        // ④ 保存した Entity を Response に変換して返す
        return new LearningRecordResponse(saved);
    }

    @Transactional
    public LearningRecordResponse update(UUID userId, UUID id, LearningRecordUpdateRequest request) {
        LearningRecord record = learningRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("学習記録が見つかりません"));
        // getById・delete と同じ所有者チェック。他人の学習記録を書き換えられないようにする。
        if (!record.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの学習記録は更新できません");
        }

        record.setDate(request.getDate());
        record.setContent(request.getContent());
        record.setDuration(request.getDuration());

        // PUT なので送られた内容でタグ紐付けを全置き換えする。
        // tagIds が null・空配列のどちらでも「タグなし」として扱い、既存の紐付けを解除する。
        // create（createLearningRecord）も null と空配列を同じ扱いにしており、それに揃えている。
        List<UUID> tagIds = request.getTagIds() == null ? Collections.emptyList() : request.getTagIds();
        List<Tag> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new ResourceNotFoundException("指定されたタグが見つかりません");
        }
        record.setTags(tags);

        return new LearningRecordResponse(learningRecordRepository.save(record));
    }

    @Transactional
    public DeleteResponse delete(UUID userId, UUID id) {
        LearningRecord record = learningRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("学習記録が見つかりません"));
        if (!record.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "他のユーザーの学習記録は削除できません");
        }
        // 添付ファイルを先に削除する。learning_record_attachments.learning_record_id は
        // learning_records.id への外部キーなので、先に LearningRecord を消すと
        // 添付ファイルの行が参照先を失い、外部キー制約違反でエラーになる。
        attachmentService.deleteAllByLearningRecordId(id);
        learningRecordRepository.delete(record);
        return new DeleteResponse();
    }
}
