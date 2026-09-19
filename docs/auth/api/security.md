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

## リフレッシュトークンの形式

アクセストークン（JWT）とは別の仕組み。JWTのような自己完結した構造ではなく、**中身に意味を持たない、ただのランダムな文字列**。

```
生成方法：SecureRandomで32バイトの乱数を作り、URLセーフなBase64に変換する
例：dUwrAp6J4rw4gOEZREg5Rzc96N55suo6EkSjKXECPyo（44文字前後）
```

| 項目 | JWT（アクセストークン） | リフレッシュトークン |
|---|---|---|
| 構造 | Header.Payload.Signatureの3パート | ただのランダム文字列（構造なし） |
| userIdの持ち方 | Payloadに埋め込み、署名検証だけで分かる（自己完結） | 文字列自体には含まれない |
| userIdの引き方 | JWTをデコードするだけ | Redisに`token → userId`として別途保存し、そこを引く |
| 失効のさせ方 | 有効期限が来るまで無効化できない（ステートレスの弱点） | Redis上の対応を削除すれば即座に失効する |
| 保管場所 | メモリ（Zustand、persistしない） | HttpOnly Cookie |

- 実装：`RefreshTokenService.generateRandomToken()`（生成）、`issue()`（Redisへの保存）、`revoke()`／`revokeAllForUser()`（Redisからの削除＝失効）
- 「トークンを失効させる」とは、トークン文字列そのものを変更・無効化しているのではなく、Redis上の`token → userId`という対応づけを消して「誰のものか引けなくする」こと

### Redisのキー設計

`RefreshTokenService`はRedisのキーを2種類使い分けている。向きが逆の検索を1つのキー構造だけで両方はできないため。

| 定数名 | キーの例 | 値 | 何に使うか |
|---|---|---|---|
| `TOKEN_KEY_PREFIX`（`"refresh:"`） | `refresh:aZ9xQ...` | userId 1個 | トークン文字列 → 誰のものかを引く（本人確認）。`issue`／`findUserId`／`revoke`が使う |
| `USER_TOKENS_KEY_PREFIX`（`"refresh:user:"`） | `refresh:user:3fa85f64-...` | そのユーザーが発行したトークン文字列の集合（Set） | userId → そのユーザーの全トークンを引く（全端末ログアウト用の逆引き）。`issue`／`revokeAllForUser`が使う |

具体例：ユーザー`3fa85f64-...`がスマホとPCの2台でログインしている状態

```
refresh:tokenA            → 3fa85f64-...          （スマホ分。本人確認に使う）
refresh:tokenB            → 3fa85f64-...          （PC分。本人確認に使う）
refresh:user:3fa85f64-... → {"tokenA", "tokenB"}   （逆引き用の集合。全端末ログアウトに使う）
```

パスワード変更などで「このユーザーの全端末をログアウトさせたい」とき、`TOKEN_KEY_PREFIX`側の検索だけでは「userIdからトークン一覧」という逆方向の検索ができない（Redisは全キーを1個ずつ舐める検索を想定していない＝遅すぎる）。そのため`issue()`でトークンを発行するたびに、`USER_TOKENS_KEY_PREFIX`側にも同じトークンを追記して、逆引き専用の索引を維持している。

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
