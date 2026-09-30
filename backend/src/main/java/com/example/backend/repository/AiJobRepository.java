package com.example.backend.repository;

import com.example.backend.entity.AiJob;
import com.example.backend.entity.AiJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
    boolean existsByUserIdAndStatusIn(UUID userId, List<AiJobStatus> statuses);
}
