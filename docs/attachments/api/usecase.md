# ユースケース・業務フロー

---

## UC-01　学習記録にファイルをアップロードする

**アクター:** ログイン済みユーザー  
**目的:** 学習で参照したPDFや手書きメモの画像を学習記録に紐付けて保存する  
**事前条件:** JWT 認証済み、対象の学習記録が存在する

**正常フロー:**
1. ファイルを選択して対象の学習記録IDに対してアップロードする
2. ファイルがストレージに保存され、DBにメタ情報（ファイル名・サイズ・MIMEタイプ・保存パス）が記録される
3. 作成された添付ファイル情報が返る

**例外フロー:**
- `file` が未添付 → 400
- ファイルサイズが10MBを超える → 400
- すでに10件の添付ファイルがある → 400
- 存在しない学習記録IDを指定 → 404
- 他ユーザーの学習記録IDを指定 → 403
- 認証トークン不正 → 401

---

## UC-02　添付ファイル一覧を確認する

**アクター:** ログイン済みユーザー  
**目的:** ある学習記録に紐づいているファイルの一覧を確認する  
**事前条件:** JWT 認証済み

**正常フロー:**
1. 対象の学習記録IDを指定して一覧を取得する
2. 紐づくファイルのメタ情報（ファイル名・サイズ・種類・日時）一覧が返る
3. 添付ファイルが0件の場合は空配列が返る

**例外フロー:**
- 存在しない学習記録IDを指定 → 404
- 他ユーザーの学習記録IDを指定 → 403
- 認証トークン不正 → 401

---

## UC-03　添付ファイルをダウンロードする

**アクター:** ログイン済みユーザー  
**目的:** 過去にアップロードしたファイルを手元に取り出す  
**事前条件:** JWT 認証済み、対象の添付ファイルが存在する

**正常フロー:**
1. 対象の学習記録IDと添付ファイルIDを指定してダウンロードする
2. ファイルのバイナリデータがレスポンスとして返り、ブラウザが保存ダイアログを表示する

**例外フロー:**
- 存在しない学習記録IDまたは添付ファイルIDを指定 → 404
- 他ユーザーの学習記録IDを指定 → 403
- 認証トークン不正 → 401

---

## UC-04　添付ファイルを削除する

**アクター:** ログイン済みユーザー  
**目的:** 不要になった添付ファイルを削除する  
**事前条件:** JWT 認証済み、対象の添付ファイルが存在する

**正常フロー:**
1. 対象の学習記録IDと添付ファイルIDを指定して削除する
2. ストレージ上のファイル実体とDBのレコードが両方削除される
3. 削除完了レスポンスが返る

**備考:**
- 学習記録を削除した場合、紐づく全添付ファイルもカスケード削除される

**例外フロー:**
- 存在しない学習記録IDまたは添付ファイルIDを指定 → 404
- 他ユーザーの学習記録IDを指定 → 403
- 認証トークン不正 → 401

---

## 動作確認手順（ローカル環境）

UC-01〜UC-04 を curl で一通り確認する手順。テストユーザーは `DataInitializer` で作成済み。

**1. DB を起動する**
```bash
docker run -d --name career-app-db -e POSTGRES_DB=career_app -e POSTGRES_PASSWORD=password -p 5432:5432 postgres:16
```

**2. バックエンドを起動する**
```bash
cd backend
APP_JWT_SECRET=（32文字以上の適当な文字列） OPENAI_API_KEY=dummy ./gradlew bootRun
```

**3. ログインしてトークンを取得する**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
```
レスポンスの `token` を以降で使う。

**4. 学習記録を1件作成する**（添付先が必要なため）
```bash
curl -X POST http://localhost:8080/api/learning-records \
  -H "Authorization: Bearer トークン" \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-07-10","content":"テスト","duration":30}'
```
レスポンスの `id` を以降で使う。

**5. アップロード（UC-01）**
```bash
curl -X POST http://localhost:8080/api/learning-records/レコードID/attachments \
  -H "Authorization: Bearer トークン" \
  -F "file=@/path/to/test.txt"
```
レスポンスの `id`（添付ファイルID）を以降で使う。

**6. 一覧取得（UC-02）**
```bash
curl -H "Authorization: Bearer トークン" \
  http://localhost:8080/api/learning-records/レコードID/attachments
```

**7. ダウンロード（UC-03）**
```bash
curl -H "Authorization: Bearer トークン" \
  http://localhost:8080/api/learning-records/レコードID/attachments/添付ファイルID/download \
  -o downloaded.txt
```

**8. 削除（UC-04）**
```bash
curl -X DELETE -H "Authorization: Bearer トークン" \
  http://localhost:8080/api/learning-records/レコードID/attachments/添付ファイルID
```
