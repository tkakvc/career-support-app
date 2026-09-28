# エラー設計

## エラーコード一覧

| HTTPステータス | 内容 | 発生ケース |
|---|---|---|
| 400 | バリデーションエラー | interest（`POST /api/ai/references`の任意入力）が200文字超過、または禁止パターンを含む（[guardrail-design.md の7](../guardrail-design.md#7-入力フィルタプロンプトインジェクション対策)）・有害コンテンツと判定された（[guardrail-design.md の9](../guardrail-design.md#9-有害コンテンツ判定openai-moderation-api)） |
| 401 | 認証エラー | JWT不正・期限切れ |
| 404 | ジョブ・学習記録が見つからない | `GET /api/ai/jobs/{jobId}` で存在しない`jobId`、または自分以外のユーザーのジョブを指定した（区別せず404にする）。`POST /api/ai/references`の`recordId`が存在しない、または他人の学習記録である場合も同様（区別せず404にする） |
| 429 | レート制限 | OpenAI API・Web検索APIのレート制限に達した。同一ユーザーの未完了ジョブがすでに存在する場合、当日のAI利用回数が10回に達している場合も同様 |
| 500 | サーバーエラー | 出力バリデーション失敗（[guardrail-design.md の6](../guardrail-design.md#6-出力バリデーション)）。Web検索・OpenAI呼び出しの失敗・タイムアウト（リトライ後）・パース失敗はジョブが `FAILED` になる形で伝わるため、`POST /api/ai/references`自体のレスポンスとしては発生しない（`GET /api/ai/jobs/{jobId}`の`status: FAILED`を参照） |

## 429 の扱い

OpenAI API・Web検索APIが `429 Too Many Requests` を返した場合、そのまま 429 をクライアントに返す。
リトライはしない（無限リトライによるコスト増加を防ぐため）。

## OpenAI APIの出力が壊れていた場合

OpenAI は確率的に不正な形式（Markdownとしてパースできない、HTML変換に失敗する等）の出力を返すことがある。
その場合はジョブの状態を `FAILED` にし、`GET /api/ai/jobs/{jobId}` 経由でユーザーに「もう一度試してください」を促す。

