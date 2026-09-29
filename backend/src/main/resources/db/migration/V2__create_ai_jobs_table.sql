-- AI提案・タスク分解の非同期化（SQS）で使うジョブ状態テーブル。
-- entity/AiJob.java のフィールドと1:1で対応させる。
-- Flyway導入時の最初のマイグレーション（既存テーブルはbaseline-on-migrateで対象外）。
CREATE TABLE ai_jobs (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    type          VARCHAR(255) NOT NULL,
    status        VARCHAR(255) NOT NULL,
    input         TEXT NOT NULL,
    result        TEXT,
    error_message TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- checkDuplicate()（同一ユーザーの未完了ジョブがあるか）がこの条件で毎回検索するため
CREATE INDEX idx_ai_jobs_user_id_status ON ai_jobs (user_id, status);
