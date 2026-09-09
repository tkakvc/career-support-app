# データモデル定義

---

## 学習記録（LearningRecord）

| フィールド | 型 | 必須 | 制約 | 説明 |
|---|---|---|---|---|
| id | UUID | - | 自動採番 | レコードID |
| userId | UUID | ○ | JWT から取得 | 作成者のユーザーID |
| date | string (YYYY-MM-DD) | ○ | 過去日・当日のみ許可 | 学習日 |
| content | string | ○ | 最大2000文字 | 学習内容 |
| duration | number (分) | ○ | 1以上 1440以下 | 学習時間 |
| tags | Tag[] | - | 最大10件 | 紐づくタグ一覧 |
| createdAt | string (ISO 8601) | - | 自動設定 | 作成日時 |

---

## タグ（Tag）

| フィールド | 型 | 必須 | 制約 | 説明 |
|---|---|---|---|---|
| id | UUID | - | 自動採番 | タグID |
| name | string | ○ | 最大50文字・ユーザー単位でユニーク | タグ名 |
| type | string | ○ | "default" or "user" | タグ種別 |
| createdBy | UUID | - | type が "user" の場合のみ存在 | 作成者のユーザーID |

---

## ER図

```
LearningRecord N --- N Tag
（中間テーブル: learning_record_tags）
```

---

## 備考

- `default` タグはシステムが事前に用意する固定タグ。ユーザーによる編集・削除は不可。
- `user` タグはユーザーが自由に作成できるタグ。作成したユーザー本人のみ編集・削除可能。
- タグを削除しても、紐づく学習記録は削除されない。タグとの紐付けのみ解除される。
