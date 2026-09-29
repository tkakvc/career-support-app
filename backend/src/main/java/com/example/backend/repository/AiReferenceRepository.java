package com.example.backend.repository;

import com.example.backend.entity.AiReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AiReferenceRepository extends JpaRepository<AiReference, UUID> {

    // tag・links をJOIN FETCHすることでN+1を防ぐ（LearningRecordRepositoryと同じ考え方）
    @Query("SELECT DISTINCT r FROM AiReference r LEFT JOIN FETCH r.tag LEFT JOIN FETCH r.links " +
            "WHERE r.userId = :userId ORDER BY r.createdAt DESC")
    List<AiReference> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT r FROM AiReference r LEFT JOIN FETCH r.tag LEFT JOIN FETCH r.links " +
            "WHERE r.userId = :userId AND r.tag.id = :tagId ORDER BY r.createdAt DESC")
    List<AiReference> findByUserIdAndTagIdOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("tagId") UUID tagId);
}
