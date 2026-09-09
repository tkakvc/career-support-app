# データモデル定義（タグ）

学習記録のモデル定義は [../../learning-records/api/model.md](../../learning-records/api/model.md) を参照。

---

## タグ（Tag）

| フィールド | 型 | 必須 | 制約 | 説明 |
|---|---|---|---|---|
| id | UUID | - | 自動採番 | タグID |
| name | string | ○ | 最大50文字・ユーザー単位でユニーク | タグ名 |
| type | string | ○ | "default" or "user" | タグ種別 |
| createdBy | UUID | - | type が "user" の場合のみ存在 | 作成者のユーザーID |
| createdAt | string (ISO 8601) | - | 自動設定 | 作成日時（タグ管理画面の作成順ソートに使用） |

---

## ER図

```
Tag N --- N LearningRecord
（中間テーブル: learning_record_tags）
```

---

## 備考

- `default` タグはシステムが事前に用意する固定タグ。ユーザーによる編集・削除は不可。
- `user` タグはユーザーが自由に作成できるタグ。作成したユーザー本人のみ編集・削除可能。
- タグを削除しても、紐づく学習記録は削除されない。タグとの紐付けのみ解除される。
