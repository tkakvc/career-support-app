-- AI情報収集・提案（参考資料生成）への作り替えに伴うテーブル追加。
-- 旧仕様（学習提案 type='SUGGEST'、タスク分解 type='DECOMPOSE'）は廃止したため、
-- ai_jobs はジョブの実行履歴でありビジネスデータではないので、既存の該当ジョブ履歴は削除してよい。
DELETE FROM ai_jobs WHERE type IN ('SUGGEST', 'DECOMPOSE');

-- entity/AiReference.java と1:1で対応させる。
-- tag_id は nullable：既存タグに一致した場合だけセットし、一致しない場合はタグを勝手に作らず
-- suggested_tag_name にAIの提案名だけを保存する（タグの新規作成は必ずユーザー起点で行うため）
CREATE TABLE ai_references (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL,
    interest           VARCHAR(200),
    summary_html       TEXT NOT NULL,
    tag_id             UUID REFERENCES tags(id),
    suggested_tag_name VARCHAR(50),
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_references_user_id ON ai_references (user_id);
CREATE INDEX idx_ai_references_tag_id ON ai_references (tag_id);

-- entity/AiReferenceLink.java と1:1で対応させる（参考資料1件に対する参考リンク、1対多）
CREATE TABLE ai_reference_links (
    id              UUID PRIMARY KEY,
    ai_reference_id UUID NOT NULL REFERENCES ai_references(id) ON DELETE CASCADE,
    url             VARCHAR(2000) NOT NULL,
    title           VARCHAR(255)
);

CREATE INDEX idx_ai_reference_links_ai_reference_id ON ai_reference_links (ai_reference_id);
