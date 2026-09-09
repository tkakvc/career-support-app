# 実装設計

## 使用ライブラリ

Spring AI（`spring-ai-openai-spring-boot-starter`）を使う。

### Spring AI とは

Spring公式のAI統合ライブラリ。OpenAI APIの呼び出しをシンプルに書ける。

RestTemplateで手書きする場合と比較：

```java
// RestTemplate で手書きする場合（煩雑）
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + apiKey);
// ...リクエストボディの組み立て...
// ...レスポンスのパース...

// Spring AI を使う場合（シンプル）
String response = chatClient.prompt()
    .user(promptText)
    .call()
    .content();
```

---

## 依存関係（build.gradle）

```gradle
implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0'
```

---

## application.yaml への追記

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7
```

`temperature` は生成テキストのランダム性。0に近いほど一定の答えを返す。1に近いほど多様な答えを返す。提案系は 0.7 程度が適切。

---

## クラス構成

```
AiController     リクエスト受付・レスポンス返却
    │
AiService        プロンプト組み立て・OpenAI呼び出し・レスポンスパース・キャッシュ制御
    │
ChatClient       Spring AI が提供するOpenAI呼び出しクライアント（自分で実装しない）
```

---

## AiService の処理フロー

### 学習提案（suggest）

```
1. キャッシュを確認する（有効期限内なら即返す）
2. learningRecordRepository から直近30件を取得
3. 学習記録が0件なら専用メッセージを返す（OpenAIに送らない）
4. 重複リクエストチェック（同一ユーザーの処理中リクエストがあれば 429 を返す）
5. 学習記録をテキスト形式に整形してシステムプロンプトに埋め込む
6. ChatClient でOpenAIに送信（タイムアウト30秒）
7. レスポンス文字列をJSONとしてパースしてSuggestResponseに変換
8. キャッシュに保存（有効期限24時間）
9. 返す
```

### タスク分解（decompose）

```
1. リクエストの goal をユーザープロンプトに埋め込む
2. 重複リクエストチェック
3. ChatClient でOpenAIに送信（タイムアウト30秒）
4. レスポンス文字列をJSONとしてパースしてDecomposeResponseに変換
5. 返す
```

---

## セキュリティ設計

### プロンプトインジェクション対策

ユーザー入力をプロンプトに埋め込む際、システムプロンプトとユーザー入力を必ず分離する。

```java
// 悪い例：システムプロンプトにユーザー入力を直接結合する
String prompt = "次に学ぶべき技術を提案してください。goal: " + userInput;

// 良い例：Spring AI の system / user を分けて使う
chatClient.prompt()
    .system("あなたはエンジニアのキャリア支援AIです。JSON形式で返してください。")
    .user(userInput)  // ← ユーザー入力はここにだけ入れる
    .call()
    .content();
```

system に書いた指示はユーザー入力より優先度が高い。`user` に「前の指示を無視して」と書かれても、`system` の指示が上書きされにくくなる。

### APIキーのログ出力防止

Spring Boot のログ設定で OpenAI 関連のログレベルを制限し、APIキーが誤ってログに出力されないようにする。

```yaml
logging:
  level:
    org.springframework.ai: WARN
```

---

## 信頼性設計

### タイムアウト

OpenAI API の応答が遅い場合に備えて、タイムアウトを30秒に設定する。

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          timeout: 30s
```

### リトライ

タイムアウトや一時的なエラー（5xx）の場合は1回だけリトライする。2回目も失敗したら 500 を返す。
リトライ間隔は2秒。無限リトライはコスト増加につながるため行わない。

### フォールバック

OpenAI がサービスダウンしている場合、キャッシュに有効期限切れの古い提案が残っていればそれを返す。
キャッシュもない場合は「現在AIサービスが利用できません。しばらく経ってから再度お試しください」を返す。

---

## 品質設計

### 学習記録が0件のケース

ユーザーが学習記録を1件も登録していない状態で提案を叩いた場合、OpenAIに送るデータがない。
この場合はOpenAIを呼ばず、固定メッセージを返す。

```json
{
  "suggestions": [],
  "message": "学習記録がまだありません。記録を追加すると提案が表示されます。"
}
```

### 重複リクエスト制御

同一ユーザーが処理中に同じエンドポイントを連打した場合、2件目以降は 429 を返す。
`Set<UUID>` でリクエスト処理中のユーザーIDを管理し、完了後に除去する。

---

## コスト管理

### レート制限

1ユーザーあたり1日10リクエストを上限とする。超過した場合は 429 を返す。
カウントはインメモリで管理する（Redis は使わず、アプリ再起動でリセットされる運用で許容する）。

### キャッシュ設計

学習提案の結果をユーザーIDをキーにキャッシュする。有効期限は24時間。
同じユーザーが24時間以内に何度叩いても1回分のAPI料金で済む。
Spring Cache（`@Cacheable`）を使ってインメモリキャッシュで実装する。

```java
@Cacheable(value = "suggestions", key = "#userId")
public SuggestResponse suggest(UUID userId) {
    // OpenAI を呼ぶ処理
}
```

タスク分解はユーザーごとに goal が異なるためキャッシュしない。

### コスト試算

- `gpt-4o-mini`：入力$0.15/1Mトークン、出力$0.60/1Mトークン
- 学習提案：約500トークン → 約0.1円/回
- タスク分解：約200トークン → 約0.04円/回
- 1ユーザー1日10リクエスト・月間アクティブユーザー100人の場合：月約1000円程度

---

## JSONパースの方針

OpenAI は JSON を返すよう指示しても、まれに前後に余計なテキストを付けることがある。

```
// 正常なケース
{"tasks": ["タスク1", "タスク2"]}

// まれに起きるケース
以下がタスク分解です：
{"tasks": ["タスク1", "タスク2"]}
```

対策として、レスポンス文字列から `{` ～ `}` の部分だけを正規表現で抽出してからパースする。
パースに失敗した場合は 500 を返す。
