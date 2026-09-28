# エンドポイント一覧

| メソッド | パス | 概要 |
|---|---|---|
| POST | /api/ai/references | 参考資料生成をリクエストする（SQSにジョブを積み、202 + jobId を返す。`recordId`未指定かつ学習記録0件かつinterest未入力の場合のみジョブを作らず200で即返す） |
| GET | /api/ai/references | 保存済み参考資料の一覧を取得する（`?tag={tagId}`でタグ絞り込み可） |
| GET | /api/ai/jobs/{jobId} | ジョブの状態・結果を取得する（フロントがポーリングする） |

## 補足

- 全エンドポイントともJWT認証必須
- `POST /api/ai/references`はSQS経由の非同期処理（`docs/ai/implementation.md`の「非同期化（SQS）」参照）。Web検索・OpenAI呼び出し自体は`GET /api/ai/jobs/{jobId}`をポーリングする間にバックグラウンドで行われる
- OpenAI API・Web検索APIのレート制限に達した場合は 429 を返す
- `recordId`（学習記録の詳細画面から生成する場合のみ）が存在しない、または他人の学習記録である場合は404を返す
