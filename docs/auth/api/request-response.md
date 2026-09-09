# リクエスト・レスポンス定義

---

## POST /api/auth/signup　ユーザー登録API

### 認証
- 不要

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| email | string | ○ | メールアドレス形式・最大255文字 |
| password | string | ○ | 8文字以上 |
| displayName | string | ○ | 最大100文字 |

```json
{
  "email": "user@example.com",
  "password": "password123",
  "displayName": "テストユーザー"
}
```

### レスポンス（201 Created）

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## POST /api/auth/login　ログインAPI

### 認証
- 不要

### リクエスト

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| email | string | ○ | - |
| password | string | ○ | - |

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

### レスポンス（200 OK）

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```
