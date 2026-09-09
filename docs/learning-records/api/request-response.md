# リクエスト・レスポンス定義（学習記録）

タグのAPIは [../../tags/api/request-response.md](../../tags/api/request-response.md) を参照。

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
| tagIds | UUID[] | - | 最大10件。送った内容でタグ紐付けを全置き換え。省略・null・空配列はいずれも「タグなし」扱い |

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
