# データモデル定義

---

## ユーザー（User）

| フィールド | 型 | 必須 | 制約 | 説明 |
|---|---|---|---|---|
| id | UUID | - | 自動採番 | ユーザーID |
| email | string | ○ | UNIQUE・最大255文字 | メールアドレス（ログイン識別子） |
| passwordHash | string | ○ | BCryptハッシュ済み | パスワード（平文不可） |
| displayName | string | ○ | 最大100文字 | 表示名 |
| githubUsername | string | - | 最大100文字 | GitHubユーザー名（未設定時はnull） |
| createdAt | string (ISO 8601) | - | 自動設定 | 作成日時 |
| updatedAt | string (ISO 8601) | - | 自動更新 | 更新日時 |

---

## JWTペイロード

| フィールド | 型 | 説明 |
|---|---|---|
| sub | string (UUID) | ユーザーID |
| iat | number | トークン発行日時（UnixTime） |
| exp | number | トークン有効期限（UnixTime） |

---

## 備考

- `passwordHash` はBCryptでハッシュ化した値のみ保存する
- JWTにはユーザーIDのみ含め、パスワードやメールアドレスは含めない
- トークンの有効期限は1時間。期限切れの場合は再ログインが必要
