# セキュリティ設計

---

## JWT構造

JWTは `.` で区切られた3つのパートで構成される。

```
{Base64(Header)}.{Base64(Payload)}.{Signature}
```

| パート | 内容 |
|---|---|
| Header | アルゴリズム（HS256）とトークン種別（JWT） |
| Payload | userId（sub）、発行日時（iat）、有効期限（exp） |
| Signature | Header + Payload を秘密鍵でHS256署名したもの。改ざん検知に使用 |

> **注意**: 秘密鍵はJWTの中身ではなく、署名を作るために使うサーバー側の鍵。JWTの外に存在する。

---

## 認証フロー

```
[クライアント]                    [サーバー]

POST /api/auth/login ─────────▶ メールアドレス・パスワード検証
                                 BCryptでパスワード検証
                                 JWT生成（userId埋め込み）
JWT トークン ◀─────────────────── 返却

GET /api/learning-records ──────▶ Authorizationヘッダーを取り出す
Authorization: Bearer {token}    JWTを検証（署名・有効期限）
                                 userIdをSecurityContextにセット
                                 @AuthenticationPrincipalで取得可能に
レスポンス ◀──────────────────── 処理実行・返却
```

---

## Spring Securityの設定方針

| パス | 設定 |
|---|---|
| `/api/auth/**` | 認証不要（permitAll） |
| それ以外の `/api/**` | JWT必須（authenticated） |

- CSRF: 無効化（REST APIはセッションを使わないため不要）
- CORS: フロントエンドのオリジン（localhost:3000）のみ許可

---

## パスワード管理

- BCryptを使用してハッシュ化する
- BCryptはハッシュに乱数（salt）を自動で含むため、同じパスワードでも毎回異なるハッシュ値になる
- 平文パスワードはDBに一切保存しない
- 検証時は `BCryptPasswordEncoder.matches(rawPassword, hashedPassword)` で照合する

---

## シークレット管理

| 設定値 | 管理方法 |
|---|---|
| JWT秘密鍵 | 環境変数（`APP_JWT_SECRET`）で管理。application.yamlに直書き禁止 |
| JWT有効期限 | application.yaml（`app.jwt.expiration`）で設定。デフォルト3600秒 |
