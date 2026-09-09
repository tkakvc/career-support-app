# ユースケース・業務フロー（AI）

対応する処理フロー・コスト管理・キャッシュ設計の詳細は [../implementation.md](../implementation.md) を参照。エラーコードの詳細は [error.md](error.md) を参照。

---

## UC-01　学習提案を生成する

**アクター:** ログイン済みユーザー
**目的:** 直近の学習記録をもとに、次に学ぶべき技術の提案を受け取る
**事前条件:** JWT 認証済み

**正常フロー:**
1. 学習提案の生成をリクエストする（`POST /api/ai/suggest`、ボディなし）
2. そのユーザーの有効なキャッシュ（24時間以内）があれば、OpenAIを呼ばずキャッシュ済みの提案を返す
3. キャッシュが無く学習記録が1件以上あれば、直近30件の学習記録をOpenAIに送信し、提案3件を生成・キャッシュして返す
4. 学習記録が0件であれば、OpenAIを呼ばず固定メッセージ（`suggestions: []`、`message` あり）を返す

**例外フロー:**
- 同一ユーザーの処理中リクエストがすでに存在する（連打） → 429
- そのユーザーの当日のAI利用回数（suggest + decompose 合算）が10回に達している → 429
- OpenAI API 自体がレート制限に達した → 429
- OpenAI 呼び出しがタイムアウトまたは5xxで、1回のリトライ後も失敗した → 500（ただし期限切れの古いキャッシュがあればそれを返す。無ければ「現在AIサービスが利用できません」を返す）
- OpenAI のレスポンスが JSON としてパースできない → 500
- 認証トークン不正 → 401

**受け入れ基準:**
- WHEN ユーザーが学習提案の生成をリクエストし、有効なキャッシュが存在するとき THE SYSTEM SHALL OpenAI を呼ばずキャッシュ済みの提案を返す
- WHEN ユーザーが学習提案の生成をリクエストし、有効なキャッシュが無く学習記録が1件以上あるとき THE SYSTEM SHALL 直近30件の学習記録をもとに OpenAI へ提案を要求し、結果を24時間キャッシュして返す
- WHEN ユーザーが学習提案の生成をリクエストし、学習記録が0件のとき THE SYSTEM SHALL OpenAI を呼ばず、固定メッセージ付きの空提案（`suggestions: []`）を返す
- IF 同一ユーザーの処理中リクエストがすでに存在するとき THEN THE SYSTEM SHALL 429 を返す
- IF そのユーザーの当日のAI利用回数（suggest + decompose 合算）が10回に達しているとき THEN THE SYSTEM SHALL 429 を返す
- IF OpenAI 呼び出しがタイムアウトまたは5xxで1回のリトライ後も失敗し、期限切れの古いキャッシュが存在するとき THEN THE SYSTEM SHALL その古い提案を返す
- IF OpenAI 呼び出しが失敗し、キャッシュも存在しないとき THEN THE SYSTEM SHALL 「現在AIサービスが利用できません。しばらく経ってから再度お試しください」を返す
- IF OpenAI のレスポンスが JSON としてパースできないとき THEN THE SYSTEM SHALL 500 を返す
- IF 認証トークンが不正のとき THEN THE SYSTEM SHALL 401 を返す

---

## UC-02　目標をタスクに分解する

**アクター:** ログイン済みユーザー
**目的:** 実装したい目標を、実装順のサブタスクに分解してもらう
**事前条件:** JWT 認証済み

**正常フロー:**
1. 目標テキスト `goal`（1〜200文字）を送信する（`POST /api/ai/decompose`）
2. サーバーが `goal` を OpenAI に送信し、実装順に並んだタスクの文字列リストを生成して返す
3. 結果はキャッシュしない（`goal` はリクエストごとに異なるため）

**例外フロー:**
- `goal` が空または未送信 → 400
- `goal` が200文字超 → 400
- 同一ユーザーの処理中リクエストがすでに存在する（連打） → 429
- そのユーザーの当日のAI利用回数（suggest + decompose 合算）が10回に達している → 429
- OpenAI API 自体がレート制限に達した → 429
- OpenAI 呼び出しがタイムアウトまたは5xxで、1回のリトライ後も失敗した → 500
- OpenAI のレスポンスが JSON としてパースできない → 500
- 認証トークン不正 → 401

**受け入れ基準:**
- WHEN ユーザーが1〜200文字の `goal` を送信したとき THE SYSTEM SHALL OpenAI へタスク分解を要求し、実装順に並んだタスクリストを返す
- IF `goal` が空または未送信のとき THEN THE SYSTEM SHALL 400 を返す
- IF `goal` が200文字を超えるとき THEN THE SYSTEM SHALL 400 を返す
- IF 同一ユーザーの処理中リクエストがすでに存在するとき THEN THE SYSTEM SHALL 429 を返す
- IF そのユーザーの当日のAI利用回数（suggest + decompose 合算）が10回に達しているとき THEN THE SYSTEM SHALL 429 を返す
- IF OpenAI 呼び出しがタイムアウトまたは5xxで1回のリトライ後も失敗したとき THEN THE SYSTEM SHALL 500 を返す
- IF OpenAI のレスポンスが JSON としてパースできないとき THEN THE SYSTEM SHALL 500 を返す
- IF 認証トークンが不正のとき THEN THE SYSTEM SHALL 401 を返す
