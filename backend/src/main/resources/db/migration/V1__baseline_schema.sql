-- Flyway導入前（ddl-auto: update時代）に作られていた既存テーブルのベースライン。
-- 既にテーブルがあるDB（このPFのローカルcareer-postgresコンテナ等）では
-- baseline-on-migrate（application.yaml）によりこのファイル自体は実行されず、
-- 「バージョン1は適用済み」として記録されるだけ。
-- 一方、新しく作った空のDB（CI・別マシン・コンテナを作り直した場合）では、
-- このファイルが実際に実行されて全テーブルが作られる。
-- 各テーブルの定義は entity/User.java・Tag.java・LearningRecord.java・Attachment.java と1:1対応。

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    github_username VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tags (
    id         UUID PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    type       VARCHAR(10) NOT NULL,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE learning_records (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    date       DATE NOT NULL,
    content    TEXT NOT NULL,
    duration   INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- LearningRecord.tags（@ManyToMany）の中間テーブル
CREATE TABLE learning_record_tags (
    learning_record_id UUID NOT NULL REFERENCES learning_records(id),
    tag_id             UUID NOT NULL REFERENCES tags(id),
    PRIMARY KEY (learning_record_id, tag_id)
);

CREATE TABLE learning_record_attachments (
    id                 UUID PRIMARY KEY,
    learning_record_id UUID NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    file_size          BIGINT NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);
