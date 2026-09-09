# ユースケース・業務フロー（設定）

学習記録・タグ・添付ファイル・認証のユースケースはそれぞれの `usecase.md` を参照。設定APIのユースケース番号は本ファイル内で UC-01 から独立して振る。

---

## UC-01　自分のプロフィールを表示する

**アクター:** ログイン済みユーザー
**目的:** 設定画面で現在の登録情報（メールアドレス・表示名・作成日時）を確認する
**事前条件:** JWT 認証済み

**正常フロー:**
1. プロフィール取得をリクエストする（`GET /api/users/me`）
2. トークンのユーザー自身の情報が返る（`passwordHash` は含まない）

**例外フロー:**
- 認証トークン不正 → 401

**受け入れ基準:**
- WHEN ログイン済みユーザーが自分のプロフィールを取得したとき THE SYSTEM SHALL メールアドレス・表示名・作成日時を返し、passwordHash は含めない
- IF 認証トークンが不正のとき THEN THE SYSTEM SHALL 401 を返す

---

## UC-02　表示名を変更する

**アクター:** ログイン済みユーザー
**目的:** 画面に表示される名前を変更する
**事前条件:** JWT 認証済み

**正常フロー:**
1. 新しい表示名を送信する（`PATCH /api/users/me`）
2. 表示名が更新され、更新後のプロフィール全体が返る
3. `updatedAt` が現在時刻に更新される

**例外フロー:**
- `displayName` が空 or 100文字超 → 400
- 認証トークン不正 → 401

**受け入れ基準:**
- WHEN ユーザーが1〜100文字の表示名を送信したとき THE SYSTEM SHALL 表示名を更新し、更新後のプロフィール全体（更新された `updatedAt` を含む）を返す
- IF displayName が空または未送信のとき THEN THE SYSTEM SHALL 400 を返す
- IF displayName が100文字を超えるとき THEN THE SYSTEM SHALL 400 を返す
- IF 認証トークンが不正のとき THEN THE SYSTEM SHALL 401 を返す

---

## UC-03　パスワードを変更する

**アクター:** ログイン済みユーザー
**目的:** パスワードを新しいものに変更する
**事前条件:** JWT 認証済み、現在のパスワードを知っている

**正常フロー:**
1. 現在のパスワードと新しいパスワードを送信する（`PUT /api/users/me/password`）
2. サーバーが現在のパスワードを保存済みハッシュと照合する
3. 一致すれば新しいパスワードをハッシュ化して保存する
4. そのユーザーのリフレッシュトークンを全て失効させる（他端末を締め出す）
5. `{ "result": "updated" }` が返る
6. 操作した端末のフロントは、再ログイン、またはトークンの再取得を行う

**例外フロー:**
- 現在のパスワードが一致しない → 403（「現在のパスワードが正しくありません」）
- 新しいパスワードが8文字未満 or 72文字超 → 400
- 新しいパスワードが現在のパスワードと同一 → 400
- 認証トークン不正 → 401

**受け入れ基準:**
- WHEN ユーザーが正しい現在パスワードと8〜72文字（現在パスワードと異なる）新パスワードを送信したとき THE SYSTEM SHALL パスワードを更新し、そのユーザーの全リフレッシュトークンを失効させる
- IF currentPassword が保存済みハッシュと一致しないとき THEN THE SYSTEM SHALL 403 を返し、パスワードを更新しない
- IF newPassword が8文字未満または72文字を超えるとき THEN THE SYSTEM SHALL 400 を返す
- IF newPassword が currentPassword と同一のとき THEN THE SYSTEM SHALL 400 を返す
- IF 認証トークンが不正のとき THEN THE SYSTEM SHALL 401 を返す

---

## 動作確認（curl）

テストユーザーは `DataInitializer` で作成済み（`test@example.com` / `password123`）。

```bash
# 0. ログインしてトークン取得
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"password123"}' | jq -r .accessToken)

# 1. プロフィール取得（UC-01）
curl -s http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN" | jq

# 2. 表示名を変更（UC-02）
curl -s -X PATCH http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"displayName":"新しい名前"}' | jq

# 3. パスワード変更（UC-03）
curl -s -X PUT http://localhost:8080/api/users/me/password \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"currentPassword":"password123","newPassword":"newpassword456"}' | jq

# 4. 現在パスワード不一致で 403 になること
curl -s -o /dev/null -w '%{http_code}\n' -X PUT http://localhost:8080/api/users/me/password \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"currentPassword":"wrong","newPassword":"whatever12"}'
```
