package com.example.backend.dto;

import java.util.UUID;

// SQSに乗せるメッセージの中身。ジョブIDだけを運び、詳細はai_jobsテーブルを正本として参照する
// （SQSの1メッセージは256KBの上限があり、学習記録やgoalの内容そのものは乗せない）
public record AiJobMessage(UUID jobId) {
}
