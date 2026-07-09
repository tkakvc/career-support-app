package com.example.backend.repository;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ JpaRepository を使うか（Repository パターンの理由）
//   → Repository の役割は「DBアクセスを担当する」こと。Service は SQL の書き方を知らなくていい。
//     JpaRepository を extends するだけで save() / findById() / delete() などの基本メソッドが使える。
//     メソッド名（findByLearningRecordId / countByLearningRecordId）から SQL を自動生成してくれる。
// 【AI任せでOK】JpaRepository の extends 構文・命名規則によるメソッドの書き方
// ============================================================
import com.example.backend.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByLearningRecordId(UUID learningRecordId);

    long countByLearningRecordId(UUID learningRecordId);
}
