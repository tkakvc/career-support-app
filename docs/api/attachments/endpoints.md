# エンドポイント一覧

| メソッド | パス | 概要 |
|---|---|---|
| GET | /api/learning-records/{id}/attachments | 添付ファイル一覧取得 |
| POST | /api/learning-records/{id}/attachments | ファイルアップロード |
| GET | /api/learning-records/{id}/attachments/{attachmentId}/download | ファイルダウンロード |
| DELETE | /api/learning-records/{id}/attachments/{attachmentId} | 添付ファイル削除 |

## パスパラメータ

| パラメータ | 説明 |
|---|---|
| `{id}` | 学習記録のID（UUID）。操作対象の学習記録を特定する |
| `{attachmentId}` | 添付ファイルのID（UUID）。操作対象のファイルを特定する |

## 補足

- 全エンドポイントで、`{id}` に指定した学習記録が自分のものでない場合は 403 を返す
- 詳細はrequest-response.mdを参照
