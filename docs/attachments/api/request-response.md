# リクエスト・レスポンス定義

---

## GET /api/learning-records/{id}/attachments　添付ファイル一覧取得API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 自分の学習記録のみアクセス可。他ユーザーの記録IDを指定した場合は 403

### レスポンス（200 OK）

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440020",
    "learningRecordId": "550e8400-e29b-41d4-a716-446655440001",
    "fileName": "spring-boot-memo.pdf",
    "contentType": "application/pdf",
    "fileSize": 102400,
    "createdAt": "2026-06-06T10:00:00Z"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440021",
    "learningRecordId": "550e8400-e29b-41d4-a716-446655440001",
    "fileName": "screenshot.png",
    "contentType": "image/png",
    "fileSize": 204800,
    "createdAt": "2026-06-06T10:05:00Z"
  }
]
```

添付ファイルが0件の場合は空配列 `[]` を返す。

---

## POST /api/learning-records/{id}/attachments　ファイルアップロードAPI

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 自分の学習記録のみアップロード可。他ユーザーの記録IDを指定した場合は 403

### リクエスト

`Content-Type: multipart/form-data` でファイルを送信する。

| フィールド | 説明 |
|---|---|
| `file` | アップロードするファイル本体 |

```
POST /api/learning-records/550e8400-e29b-41d4-a716-446655440001/attachments
Content-Type: multipart/form-data

file: (バイナリデータ)
```

### レスポンス（201 Created）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440020",
  "learningRecordId": "550e8400-e29b-41d4-a716-446655440001",
  "fileName": "spring-boot-memo.pdf",
  "contentType": "application/pdf",
  "fileSize": 102400,
  "createdAt": "2026-06-06T10:00:00Z"
}
```

---

## GET /api/learning-records/{id}/attachments/{attachmentId}/download　ファイルダウンロードAPI

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 自分の学習記録に紐づくファイルのみダウンロード可。他ユーザーのものは 403

### レスポンス（200 OK）

JSONではなく、ファイルのバイナリデータをそのままレスポンスボディとして返す。

レスポンスヘッダー：

| ヘッダー | 値の例 | 説明 |
|---|---|---|
| Content-Type | `application/pdf` | ファイルの種類 |
| Content-Disposition | `attachment; filename="spring-boot-memo.pdf"` | ブラウザに「保存ダイアログ」を表示させる指示 |
| Content-Length | `102400` | ファイルサイズ（バイト） |

---

## DELETE /api/learning-records/{id}/attachments/{attachmentId}　添付ファイル削除API

### 認証
- Authorization: Bearer {JWT} ヘッダー必須
- 自分の学習記録に紐づくファイルのみ削除可。他ユーザーのものは 403
- ストレージ上のファイル実体とDBのレコードを両方削除する

### レスポンス（200 OK）

```json
{
  "result": "deleted"
}
```
