# リクエスト・レスポンス定義（設定）

認証系（signup / login / logout）は [../../auth/api/request-response.md](../../auth/api/request-response.md) を参照。

---

## GET /api/users/me　プロフィール取得API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 返すのはトークンのユーザー自身の情報のみ

### リクエスト

なし（ボディ・クエリともになし）。

### レスポンス（200 OK）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "email": "user@example.com",
  "displayName": "テストユーザー",
  "createdAt": "2026-01-10T09:00:00Z",
  "updatedAt": "2026-06-01T12:00:00Z"
}
```

- `passwordHash` は含めない

---

## PATCH /api/users/me　プロフィール更新API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| displayName | string | ○ | 1文字以上・最大100文字 |

- 現状の更新対象は表示名のみ。PATCH（部分更新）を採る理由は、将来プロフィール項目が増えたときに「送ったフィールドだけ更新」を維持するため

```json
{
  "displayName": "新しい表示名"
}
```

### レスポンス（200 OK）

更新後のプロフィール全体を返す（`GET /api/users/me` と同じ形）。

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "email": "user@example.com",
  "displayName": "新しい表示名",
  "createdAt": "2026-01-10T09:00:00Z",
  "updatedAt": "2026-06-09T08:30:00Z"
}
```

- `updatedAt` が更新されている

---

## PUT /api/users/me/password　パスワード変更API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- `currentPassword` が保存済みハッシュと一致しない限り変更しない

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| currentPassword | string | ○ | 現在のパスワード（平文） |
| newPassword | string | ○ | 8文字以上・72文字以下。`currentPassword` と同一は不可 |

```json
{
  "currentPassword": "oldpassword123",
  "newPassword": "newpassword456"
}
```

- `newPassword` の上限を72文字にしている理由：BCrypt は72バイトを超える入力を切り捨てる仕様のため、それより長いパスワードを許可すると「末尾を変えても同じハッシュになる」混乱が起きる

### レスポンス（200 OK）

```json
{
  "result": "updated"
}
```

### パスワード変更後のトークンの扱い

- アクセストークン（JWT）はステートレスなので、変更後も期限（15分）までは有効なまま。これは許容する
- リフレッシュトークンは Redis で失効管理しているため（`docs/auth/api/security.md`）、**パスワード変更時に、そのユーザーのリフレッシュトークンを全て失効させる**
  - 目的：パスワードが漏れて他端末でログインされている場合に、変更操作で他端末を締め出せるようにする
  - 結果として、他端末は次回のトークン更新で再ログインを求められる。操作した本人の端末は、レスポンス受領後にフロントが再ログイン or トークン再取得を行う

---

## バリデーションまとめ

| エンドポイント | フィールド | ルール |
|---|---|---|
| PATCH /api/users/me | displayName | 必須・1〜100文字 |
| PUT /api/users/me/password | currentPassword | 必須・空不可 |
| PUT /api/users/me/password | newPassword | 8〜72文字、currentPassword と異なること |
