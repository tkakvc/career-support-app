-- Flyway導入前（ddl-auto: update時代）に作られていた既存テーブルのベースライン。
-- 各テーブルの定義は entity/User.java・Tag.java・LearningRecord.java・Attachment.java と1:1対応。
--
-- IF NOT EXISTSを付けている理由：baseline-on-migrate（application.yaml）は
-- 「flyway_schema_historyテーブルが存在しない、かつスキーマが空でない」場合にだけ働く
-- 一度きりの仕組みで、一度でも中途半端に失敗して先にそのテーブルが作られてしまうと、
-- 以後はbaseline-versionの値を変えても再適用されない（本番で実際にこれが起きた：
-- 最初にbaseline-version: 0のまま一度migrateが走って失敗し、schema_historyだけ残った
-- 状態になり、後からbaseline-version: 1に直しても無効だった）。
-- IF NOT EXISTSにしておけば、baselineの状態に関わらず「既にあれば何もしない・
-- 無ければ作る」で安全に収束する。

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    github_username VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tags (
    id         UUID PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    type       VARCHAR(10) NOT NULL,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS learning_records (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    date       DATE NOT NULL,
    content    TEXT NOT NULL,
    duration   INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- LearningRecord.tags（@ManyToMany）の中間テーブル
CREATE TABLE IF NOT EXISTS learning_record_tags (
    learning_record_id UUID NOT NULL REFERENCES learning_records(id),
    tag_id             UUID NOT NULL REFERENCES tags(id),
    PRIMARY KEY (learning_record_id, tag_id)
);

CREATE TABLE IF NOT EXISTS learning_record_attachments (
    id                 UUID PRIMARY KEY,
    learning_record_id UUID NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(500) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    file_size          BIGINT NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);
