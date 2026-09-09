# データモデル定義（学習記録）

タグのモデル定義は [../../tags/api/model.md](../../tags/api/model.md) を参照。

---

## 学習記録（LearningRecord）

| フィールド | 型 | 必須 | 制約 | 説明 |
|---|---|---|---|---|
| id | UUID | - | 自動採番 | レコードID |
| userId | UUID | ○ | JWT から取得 | 作成者のユーザーID |
| date | string (YYYY-MM-DD) | ○ | 過去日・当日のみ許可 | 学習日 |
| content | string | ○ | 最大2000文字 | 学習内容 |
| duration | number (分) | ○ | 1以上 1440以下 | 学習時間 |
| tags | Tag[] | - | 最大10件 | 紐づくタグ一覧（Tagの定義は tags/model.md 参照） |
| createdAt | string (ISO 8601) | - | 自動設定 | 作成日時 |

---

## ER図

```
LearningRecord N --- N Tag
（中間テーブル: learning_record_tags）
```
