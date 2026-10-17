-- V1__baseline_schema.sqlのCREATE TABLE IF NOT EXISTSは、既にテーブルが存在する環境では
-- 中身を見ずにスキップするだけなので、ddl-auto（Hibernate）時代に後から足されたはずの
-- カラムが本番の実テーブルには無い、というズレがあり得る（本番で実際にtags.created_atが
-- 無く、Hibernateのスキーマ検証で起動失敗して発覚した）。
--
-- V1は既に本番で一度成功扱いになっているため、V1自体を直しても再実行されない
-- （Flywayは成功済みのバージョンを再実行しない。チェックサムのずれはFlywayConfigの
-- repair()で解消しているが、それは記録上のチェックサムを合わせるだけで、
-- SQLを再実行するものではない）。そのため新しいバージョンとしてここに切り出す。
--
-- DEFAULT付き・nullable列だけを対象にしている。NOT NULLかつDEFAULT無しの列
-- （email・password_hash等）は、既存行がある場合ALTER自体が失敗しうるため対象外＝
-- 元から存在する前提の列として扱う。
ALTER TABLE users ADD COLUMN IF NOT EXISTS github_username VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE tags ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE learning_records ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE learning_record_attachments ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now();
