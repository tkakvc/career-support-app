# データモデル定義（設定）

ユーザー（User）の完全な定義は [../../auth/api/model.md](../../auth/api/model.md) を参照。ここでは設定APIが読み書きするフィールドだけを再掲する。

---

## 設定APIが扱う User のフィールド

| フィールド | 型 | 読取 | 書込 | 制約 | 説明 |
|---|---|---|---|---|---|
| id | UUID | ○ | × | 自動採番 | ユーザーID |
| email | string | ○ | ×（本APIでは変更不可） | UNIQUE・最大255文字 | メールアドレス（ログイン識別子） |
| displayName | string | ○ | ○ | 1文字以上・最大100文字 | 画面表示名 |
| passwordHash | string | ×（レスポンスに含めない） | ○（パスワード変更時のみ） | BCryptハッシュ済み | パスワード |
| createdAt | string (ISO 8601) | ○ | × | 自動設定 | 作成日時 |
| updatedAt | string (ISO 8601) | ○ | × | 自動更新 | 更新日時（PATCH / パスワード変更で更新される） |

---

## レスポンスに含めないフィールド

- `passwordHash`：ハッシュ値でも外部に出す理由がない。総当たり攻撃の材料を与えないため常に除外する
- 他ユーザーの情報：本APIは自分の情報しか返さない

---

## パスワードの扱い

- リクエストで受け取るのは平文の `currentPassword` / `newPassword`
- サーバーは `currentPassword` を保存済み `passwordHash` と BCrypt で照合し、一致した場合のみ `newPassword` を新たに BCrypt ハッシュ化して `passwordHash` を上書きする
- 平文パスワードはログ・DB・レスポンスのいずれにも残さない

---

## 備考

- `updatedAt` は Hibernate の `@UpdateTimestamp` により、PATCH やパスワード変更で User 行が更新されると自動で現在時刻に更新される
- `email` の変更は本APIのスコープ外（`overview.md` 参照）。将来対応する場合は確認メール送信フローを伴う別エンドポイントとして追加する
