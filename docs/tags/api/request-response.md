# リクエスト・レスポンス定義（タグ）

学習記録のAPIは [../../learning-records/api/request-response.md](../../learning-records/api/request-response.md) を参照。

---

## GET /api/tags　タグ一覧取得API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- default タグ全件 + ログインユーザーが作成した user タグを返す

### レスポンス（200 OK）

- ソート（作成順・名前順など）は行わず、そのまま返す。並び替えはフロント側（タグ管理画面での列ソート）で行う（`docs/tags/screen/overview.md` 参照）

```json
[
  { "id": "550e8400-e29b-41d4-a716-446655440010", "name": "API", "type": "default", "createdAt": "2026-01-01T00:00:00Z" },
  { "id": "550e8400-e29b-41d4-a716-446655440011", "name": "Linux", "type": "default", "createdAt": "2026-01-01T00:00:00Z" },
  {
    "id": "550e8400-e29b-41d4-a716-446655440012",
    "name": "独自タグ",
    "type": "user",
    "createdBy": "550e8400-e29b-41d4-a716-446655440002",
    "createdAt": "2026-05-20T09:15:00Z"
  }
]
```

---

## POST /api/tags　タグ作成API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 作成されるタグは type: "user"、createdBy: ログインユーザーのID

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| name | string | ○ | 最大50文字・同ユーザー内でユニーク |

```json
{
  "name": "新しいタグ"
}
```

### レスポンス（201 Created）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440013",
  "name": "新しいタグ",
  "type": "user",
  "createdBy": "550e8400-e29b-41d4-a716-446655440002",
  "createdAt": "2026-05-27T12:34:56Z"
}
```

---

## PUT /api/tags/{id}　タグ編集API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 作成者本人のタグのみ編集可。default タグは編集不可（403）

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| name | string | ○ | 最大50文字・同ユーザー内でユニーク |

```json
{
  "name": "編集後のタグ名"
}
```

### レスポンス（200 OK）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440012",
  "name": "編集後のタグ名",
  "type": "user",
  "createdBy": "550e8400-e29b-41d4-a716-446655440002",
  "createdAt": "2026-05-20T09:15:00Z"
}
```

- `createdAt` は編集しても変わらない（作成日時のまま。更新日時を別途持ちたい場合は `updatedAt` を後日追加する）

---

## DELETE /api/tags/{id}　タグ削除API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 作成者本人のタグのみ削除可。default タグは削除不可（403）
- タグを削除しても、紐づく学習記録は削除されない（紐付けのみ解除）

### レスポンス（200 OK）

```json
{
  "result": "deleted"
}
```
