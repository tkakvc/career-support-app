# リクエスト・レスポンス定義

---

## POST /api/learning-records　学習記録作成API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- `userId` はトークンから取得するため、リクエストボディに含めない

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| date | string (YYYY-MM-DD) | ○ | 過去日・当日のみ |
| content | string | ○ | 最大2000文字 |
| duration | number | ○ | 1以上 1440以下（分） |
| tagIds | UUID[] | - | 最大10件。空配列可。存在するタグIDのみ指定可 |

```json
{
  "date": "2026-05-27",
  "content": "Spring Bootの基礎学習をした",
  "duration": 120,
  "tagIds": [
    "550e8400-e29b-41d4-a716-446655440010",
    "550e8400-e29b-41d4-a716-446655440012"
  ]
}
```

### レスポンス（201 Created）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "userId": "550e8400-e29b-41d4-a716-446655440002",
  "date": "2026-05-27",
  "content": "Spring Bootの基礎学習をした",
  "duration": 120,
  "tags": [
    { "id": "550e8400-e29b-41d4-a716-446655440010", "name": "API", "type": "default" },
    { "id": "550e8400-e29b-41d4-a716-446655440012", "name": "独自タグ", "type": "user" }
  ],
  "createdAt": "2026-05-27T12:34:56Z"
}
```

---

## GET /api/learning-records　学習記録一覧取得API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- ログインユーザー自身の学習記録のみ返す

### クエリパラメータ

| パラメータ | 型 | 必須 | 説明 |
|---|---|---|---|
| tag | string | - | タグ名で絞り込み |
| from | string (YYYY-MM-DD) | - | 開始日（含む） |
| to | string (YYYY-MM-DD) | - | 終了日（含む） |
| keyword | string | - | 学習内容の部分一致検索 |

`from` と `to` は片方だけの指定も可。

### レスポンス（200 OK）

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "userId": "550e8400-e29b-41d4-a716-446655440002",
    "date": "2026-05-27",
    "content": "Spring Bootの基礎学習をした",
    "duration": 120,
    "tags": [
      { "id": "550e8400-e29b-41d4-a716-446655440010", "name": "API", "type": "default" }
    ],
    "createdAt": "2026-05-27T12:34:56Z"
  }
]
```

---

## GET /api/learning-records/{id}　学習記録詳細取得API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 他ユーザーのレコードを指定した場合は 403

### レスポンス（200 OK）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "userId": "550e8400-e29b-41d4-a716-446655440002",
  "date": "2026-05-27",
  "content": "Spring Bootの基礎学習をした",
  "duration": 120,
  "tags": [
    { "id": "550e8400-e29b-41d4-a716-446655440010", "name": "API", "type": "default" }
  ],
  "createdAt": "2026-05-27T12:34:56Z"
}
```

---

## PUT /api/learning-records/{id}　学習記録更新API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 他ユーザーのレコードを指定した場合は 403

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| date | string (YYYY-MM-DD) | ○ | 過去日・当日のみ |
| content | string | ○ | 最大2000文字 |
| duration | number | ○ | 1以上 1440以下（分） |
| tagIds | UUID[] | - | 最大10件。空配列可。省略時は既存タグをそのまま維持 |

```json
{
  "date": "2026-05-27",
  "content": "Spring Bootの応用学習をした",
  "duration": 180,
  "tagIds": ["550e8400-e29b-41d4-a716-446655440011"]
}
```

### レスポンス（200 OK）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "userId": "550e8400-e29b-41d4-a716-446655440002",
  "date": "2026-05-27",
  "content": "Spring Bootの応用学習をした",
  "duration": 180,
  "tags": [
    { "id": "550e8400-e29b-41d4-a716-446655440011", "name": "Linux", "type": "default" }
  ],
  "createdAt": "2026-05-27T12:34:56Z"
}
```

---

## DELETE /api/learning-records/{id}　学習記録削除API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 他ユーザーのレコードを指定した場合は 403

### レスポンス（200 OK）

```json
{
  "result": "deleted"
}
```

---

## GET /api/tags　タグ一覧取得API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- default タグ全件 + ログインユーザーが作成した user タグを返す

### レスポンス（200 OK）

```json
[
  { "id": "550e8400-e29b-41d4-a716-446655440010", "name": "API", "type": "default" },
  { "id": "550e8400-e29b-41d4-a716-446655440011", "name": "Linux", "type": "default" },
  {
    "id": "550e8400-e29b-41d4-a716-446655440012",
    "name": "独自タグ",
    "type": "user",
    "createdBy": "550e8400-e29b-41d4-a716-446655440002"
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
  "createdBy": "550e8400-e29b-41d4-a716-446655440002"
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
  "createdBy": "550e8400-e29b-41d4-a716-446655440002"
}
```

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
