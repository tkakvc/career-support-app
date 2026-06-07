package com.example.backend.repository;

import com.example.backend.entity.LearningRecord;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningRecordRepository extends JpaRepository<LearningRecord, UUID>, JpaSpecificationExecutor<LearningRecord> {

    // @EntityGraph: Specification で絞り込んだ結果に対して tags を JOIN FETCH で一緒に取得する
    // これがないと tags にアクセスするたびに SQL が発行されて N+1 が発生する
    @Override
    @EntityGraph(attributePaths = "tags")
    List<LearningRecord> findAll(@Nullable Specification<LearningRecord> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = "tags")
    Optional<LearningRecord> findById(UUID id);
}
