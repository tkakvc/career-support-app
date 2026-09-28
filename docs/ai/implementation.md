# 実装設計

自動テストは無い（方針として意図的）。動作確認は手動のみ。

対応するAPI仕様は [api/usecase.md](api/usecase.md)・[api/overview.md](api/overview.md)・[api/endpoints.md](api/endpoints.md)・[api/request-response.md](api/request-response.md)・[api/error.md](api/error.md)、画面設計は [screen/overview.md](screen/overview.md)・[screen/flow.md](screen/flow.md)参照。

## 使用ライブラリ

| ライブラリ | 用途 |
|---|---|
| Spring AI（`spring-ai-openai-spring-boot-starter:1.0.0-M6`） | OpenAIのChat・Moderation呼び出し |
| Tavily（`app.websearch.*`経由でREST呼び出し。専用SDKは使わずRestClientで直接叩く） | LLM向けのWeb検索API。要約前に実際の検索結果を取得する |
| commonmark（`org.commonmark:commonmark:0.22.0`） | OpenAIが生成したMarkdownをHTMLに変換する |

### なぜMarkdown→HTML変換を挟むか

OpenAIに直接HTMLを生成させず、Markdownを生成させてから`MarkdownHtmlConverter`でHTMLに変換している。LLMは複雑なHTML（閉じタグの対応等）よりMarkdownの方が構文を崩さずに出力しやすく、生成失敗率が下がるため。

### なぜTavilyを使うか（RAGという考え方）

OpenAI単体は学習時点までの知識しか持たず、最新の情報には弱い。そこで「まず外部から関連情報を検索して取ってくる（Retrieval）→ それをプロンプトに含めた上でLLMに生成させる（Augmented Generation）」というRAG（Retrieval-Augmented Generation）の考え方を採用し、TavilyというLLM向けの検索APIで実際のWeb検索結果を取得してからOpenAIに要約させる。Tavilyは検索結果のページ取得・本文抽出までAPI側で行い、`content`フィールドに本文の抜粋を入れて返すため、スクレイピング処理を自前で書かずに済む。

---

## 依存関係（build.gradle）

```gradle
implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M6'
implementation 'org.commonmark:commonmark:0.22.0'
```

Tavily自体はSDKを使わず、Spring標準の`RestClient`で直接HTTP呼び出しする（`WebSearchClient`参照）ため、追加の依存関係は無い。

`1.0.0-M6`はマイルストーン版。`ModerationModel`はこの版の`OpenAiAutoConfiguration`に含まれており、追加の依存関係は不要。

---

## application.yaml への設定

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7
      moderation:
        options:
          model: omni-moderation-latest

app:
  websearch:
    api-key: ${TAVILY_API_KEY:}
    base-url: https://api.tavily.com
```

- `temperature` は生成テキストのランダム性。0に近いほど一定の答えを返す。1に近いほど多様な答えを返す
- `app.websearch.api-key`：末尾の`:`は「環境変数が無ければ空文字」という意味。`TAVILY_API_KEY`未設定でもアプリ自体は起動できるが、検索呼び出し時に失敗しジョブが`FAILED`になる

---

## クラス構成

```
AiController        リクエスト受付・ジョブ作成・一覧取得・ポーリング応答
    │
AiService            ガードレール判定・enqueueReference（ジョブ作成＋SQS送信）
    │                ・generateResult（Web検索→OpenAI呼び出し→保存の本体）・getJob（ポーリング応答）
    │
AiReferenceService   GET /api/ai/references（保存済み一覧の読み取り専用サービス。生成側とは責務を分離）
    │
AiJobWorker          SQSからのメッセージ受信・冪等性チェック・AiService.generateResult()の呼び出し（@SqsListener）
    │
WebSearchClient       Tavily呼び出し（検索結果のtitle/url/contentを取得）
MarkdownHtmlConverter  commonmarkでMarkdown→HTML変換
TagService            findMatchingVisibleTag：AIが提案したタグ名が既存タグに一致するかだけ調べる（新規作成はしない）
    │
ChatClient            Spring AI が提供するOpenAI呼び出しクライアント（自分で実装しない）
ModerationModel        Spring AI が提供するOpenAI Moderation APIクライアント（自分で実装しない）
SqsTemplate            Spring Cloud AWS が提供するSQS送受信クライアント（自分で実装しない）
```

---

## ガードレール実装（`docs/ai/guardrail-design.md` 対応）

`AiService`が実装しているガードレールは以下。項目1（スコープ制限）・4（フォールバック）は実装していない（4はキャッシュ自体が無いため対象外、1は入力の危険性を項目7・項目9でカバーする方針にしている）。8（開示UI）はフロント側のみ（`ai/page.tsx`の外部送信の開示表示）。

### 2. タイムアウト設定

`AiConfig`に`RestClientCustomizer`のBeanを追加し、OpenAI呼び出しに使われる`RestClient`のタイムアウトを設定する（`connectTimeout` 5秒・`readTimeout` 30秒）。`WebSearchClient`も同じ考え方でTavily呼び出し用に別途タイムアウト設定（`connectTimeout` 5秒・`readTimeout` 10秒）を持つ。

```java
@Bean
public RestClientCustomizer aiRestClientCustomizer() {
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(5))
            .withReadTimeout(Duration.ofSeconds(30));
    return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
}
```

### 3. リトライ

`callOpenAi()`で明示的な`for`ループでリトライを実装する（`@Retryable`は使わない）。`ResourceAccessException`（タイムアウト・接続エラー）のみ最大1回リトライし、`HttpClientErrorException.TooManyRequests`（429）はリトライせずそのまま429として伝播させる。**Tavily呼び出し（`WebSearchClient.search()`）自体にはこのリトライ機構が無い**。失敗すると`generateResult()`全体が例外を投げ、`AiJobWorker`側でジョブが`FAILED`になるだけ（詳しくは下記「AiServiceの処理フロー」）。

### 5. ログ・コスト計測

`callOpenAi`の中でSLF4Jのloggerを使い、呼び出しごとに成功/失敗とレイテンシを記録する（`type=reference`で記録される）。

### 6. 出力バリデーション

`validateLlmOutput()`で、OpenAIの応答（`summaryMarkdown`・`tagName`）の妥当性をチェックする。`summaryMarkdown`は空でないこと・20000文字以内、`tagName`は空でないこと・50文字以内。

### 7. 入力フィルタ（プロンプトインジェクション対策）

`interest`が入力されている場合のみ、代表的なインジェクション文言を弾く簡易フィルタ（`validateGoalContent`）を適用する。

### 9. 有害コンテンツ判定（OpenAI Moderation API）

`interest`・学習記録の内容（`recordsText`）・`focusRecordText`（学習記録詳細画面から生成した場合の対象記録）を結合した文字列に対して、`ModerationModel`でチェックする。

---

## AiService の処理フロー

処理は「受付（`enqueueReference`、同期）」と「生成（`AiJobWorker`経由の`generateResult`、非同期）」の2段階。ガードレール（重複チェック・レート制限・入力フィルタ・Moderation）は**すべて受付側で同期的に行う**。Web検索・OpenAI呼び出しは失敗してもジョブを`FAILED`にするだけでよい性質（ユーザーがもう一度ボタンを押せばよい）のため、こちらは非同期側（`generateResult`）で行う。

### ① 受付（`enqueueReference`、同期）

```
1. recordIdが指定されていれば、その学習記録（focusRecord）を取得する。
   存在しない・他人の記録の場合は404（所有チェックは他のuserId所有チェックと同じ考え方）
2. learningRecordRepository から直近30件を取得
3. 学習記録が0件、かつinterestも未入力、かつrecordId未指定なら固定メッセージを返す
   （Web検索・OpenAIを呼ばない。ジョブも作らない）
4. 重複リクエストチェック（同一ユーザーの未完了ジョブがai_jobsテーブルにあれば429）
5. レート制限チェック（1日10回）
6. interestが入力されていれば入力フィルタ（項目7）
7. 学習記録・focusRecord・interestを結合してModeration APIでチェック（項目9）
8. 検索クエリの材料（interest優先、無ければfocusRecordまたは直近記録の本文、タグは補足）と
   プロンプトの材料をReferenceJobInputとしてJSON化し、ai_jobsに保存（type: REFERENCE、状態: PENDING）
9. SQSにジョブIDを送信
10. 202 Accepted + jobId を返す（即時応答の場合は200 + messageのみ）
```

- `recordId`は、学習記録の詳細画面から「この記録について参考資料を作る」を押した場合だけ値が入る（`/ai`画面からの生成ではnull）
- 検索クエリの主役は`interest`。本文・タグは補足材料（`buildSearchQuery`のコメント参照。同じ「AWS」というタグでも「未経験からインフラエンジニアを目指す」「資格を取りたい」では検索すべき内容が変わるが、その違いを表現できるのは`interest`であって本文・タグではないため）

### ② 生成（`AiJobWorker.handle()` → `AiService.generateResult()`、非同期）

```
1. SQSからジョブIDを受け取る（AiJobWorker）
2. 冪等性チェック：ジョブがDONE/FAILEDならOpenAIを呼ばず終える
3. ジョブの状態を PROCESSING に更新
4. 保存しておいたReferenceJobInputから検索クエリを組み立て、WebSearchClient.search()でTavilyを呼ぶ
   （最大5件。HTTP自体の失敗はそのままAiJobWorkerのcatchまで伝播しジョブがFAILEDになる。
    HTTP成功だが結果0件の場合はエラー扱いせず空リストとして扱う）
5. ユーザーの可視タグ一覧（TagRepository.findVisibleTags）を取得し、分類先の候補としてプロンプトに含める
6. callOpenAi() 経由でOpenAIに送信（システムプロンプトとユーザープロンプトを分離。タイムアウト30秒、リトライ最大1回）
7. レスポンス文字列をJSONとしてパースしてLlmOutput（summaryMarkdown・tagName）に変換
8. 出力バリデーション（項目6）
9. MarkdownHtmlConverterでsummaryMarkdownをHTMLに変換
10. TagService.findMatchingVisibleTag()で、tagNameが既存の可視タグに（大文字小文字を無視して）
    一致するか調べる。一致すればそのタグをセット、しなければタグは作らずsuggestedTagNameに
    AIの提案名だけを保存する（タグの新規作成は常にユーザーの明示的な操作を起点にする方針。
    「学習記録をつける」フォームでの保存時にのみ実際に作成される）
11. AiReference（＋検索結果から作ったAiReferenceLinkのリスト）をDBに保存
12. 保存したAiReferenceをAiReferenceResponseの形にしてJSON文字列化し、ジョブの結果として保存。
    状態を DONE に更新（途中で例外が飛べば AiJobWorker 側で FAILED）
```

---

## セキュリティ設計

### プロンプトインジェクション対策

ユーザー入力をプロンプトに埋め込む際、システムプロンプトとユーザー入力を必ず分離する。

```java
chatClient.prompt()
    .system(REFERENCE_SYSTEM_PROMPT)
    .user(userPrompt)  // ← 学習記録・interest・Tavilyの検索結果はすべてここに入る
    .call()
    .content();
```

Web検索結果（Tavilyのレスポンス）もユーザー入力と同様、`user`側にしか入れない。検索結果の中身は外部サイトの文章であり、信頼できない入力として扱う必要があるため。

### APIキーのログ出力防止

```yaml
logging:
  level:
    org.springframework.ai: WARN
```

---

## 信頼性設計

### タイムアウト・リトライ

上記「ガードレール実装」の2・3参照。TavilyのタイムアウトはOpenAIより短い（`connectTimeout` 5秒・`readTimeout` 10秒）。

### レート制限・重複防止

レート制限（1日10回）はRedisの`INCR`＋`EXPIRE`（キー：`ai:ratelimit:{userId}`）で管理する。重複防止（同一ユーザーの未完了ジョブがあれば429）は`aiJobRepository.existsByUserIdAndStatusIn(userId, [PENDING, PROCESSING])`でDBを見て判定する。

---

## 品質設計

### 学習記録が0件・interest未入力・recordId未指定のケース

```json
{ "jobId": null, "message": "学習記録がまだありません。記録を追加すると参考資料が生成できるようになります。" }
```

この場合はWeb検索・OpenAIを呼ばない（コスト節約）。

### 重複リクエスト制御

同一ユーザーの未完了ジョブ（状態が`PENDING`または`PROCESSING`）がすでに存在する場合、2件目以降は429を返す。

---

## コスト管理

### レート制限

1ユーザーあたり1日10リクエストを上限とする。超過した場合は429を返す。**Tavily側にもこのレート制限がそのまま適用される**（同じenqueue経路を通るため、OpenAI・Tavily2つの外部APIコストがまとめて1つの上限で管理されている）。

### キャッシュ設計

結果をキャッシュしない（`@Cacheable`は使わない）。`interest`・`recordId`の組み合わせで同じユーザーでも欲しい結果が変わるため。コスト面の上限はキャッシュではなくレート制限（1日10回）で担保する。

### コスト試算

Tavilyは無料枠（月1,000クレジット）があるため、個人開発のPFとしての利用規模では無料枠内に収まる見込み。OpenAI側の正式な試算は未実施。

---

## JSONパースの方針

OpenAIはJSONを返すよう指示しても、まれに前後に余計なテキストを付けることがある。対策として、レスポンス文字列から`{`〜`}`の部分だけを正規表現で抽出してからパースする（`parseJson()`）。パースに失敗した場合は500を返す。

---

## Web検索（Tavily）・Markdown変換の実装

### WebSearchClient

```java
public List<SearchResult> search(String query, int maxResults) {
    SearchRequestBody body = new SearchRequestBody(apiKey, query, maxResults, "basic");
    SearchApiResponse response = restClient.post()
            .uri("/search")
            .body(body)
            .retrieve()
            .body(SearchApiResponse.class);

    if (response == null || response.results() == null) {
        return List.of();
    }
    return response.results().stream()
            .map(r -> new SearchResult(r.title(), r.url(), r.content()))
            .toList();
}
```

- APIキー・検索語・最大件数（5件固定）・検索の深さ（`"basic"`固定。Tavilyには`"advanced"`もあるがコスト増のため使わない）をリクエストボディに詰めて`POST /search`を呼ぶ
- Tavilyは検索結果のページ本文抜粋（`content`）まで返してくれるため、自前でスクレイピングする処理は不要
- HTTP自体が失敗した場合（タイムアウト・5xx等）は`retrieve()`が例外を投げ、そのまま`AiJobWorker`側の`catch(Exception)`まで伝播してジョブを`FAILED`にする。HTTP自体は200で成功したが結果が空だった場合はエラーではなく「検索結果0件」として空リストを返す

### MarkdownHtmlConverter

```java
public String toHtml(String markdown) {
    Node document = parser.parse(markdown);
    String body = renderer.render(document);
    return STYLE + "<div class=\"ai-reference\">\n" + body + "</div>";
}
```

commonmarkでパース・レンダリングし、最低限のインラインCSS（見出し・コードブロック・リンクの見た目）を付けたHTML断片にする。フロントではこれを`iframe`の`srcDoc`に渡して表示する（`dangerouslySetInnerHTML`は使わない。OpenAIが生成した内容を親ページから隔離するため。`frontend/src/app/(main)/ai/page.tsx`参照）。

---

## タグ分類の仕組み

生成した参考資料は、AIが提案したタグ名（`tagName`）をそのまま新規タグとして作らず、まず既存の可視タグ（defaultタグ＋自分のuserタグ）に大文字小文字を無視して一致するものがあるかだけを`TagService.findMatchingVisibleTag()`で調べる。

- 一致すれば`AiReference.tag`にセット（`GET /api/ai/references`のレスポンスでは`tagId`/`tagName`に値が入る）
- 一致しなければタグを作らず、`AiReference.suggestedTagName`にAIの提案名だけを保存する（レスポンスでは`suggestedTagName`に値が入り、`tagId`/`tagName`はnull）
- 実際のタグ新規作成は、フロントで「学習記録をつける」ボタンを押した瞬間（`useCreateTag`経由）にのみ行われる

**なぜ非同期ジョブの中でタグを自動作成しないか**：タグの作成は常にユーザーの明示的な操作を起点にすべきという方針（既存の`TagService.createTag()`も必ずユーザー操作から呼ばれる）。バックグラウンドの非同期ジョブがAIの判断だけで勝手にタグを作ってしまうと、この一貫性が崩れる。

---

## DBスキーマ

### ai_jobs（Flyway `V2__create_ai_jobs_table.sql`）

| カラム | 型 | 説明 |
|---|---|---|
| id | UUID | ジョブID（`jobId`としてクライアントに返す） |
| user_id | UUID | 実行したユーザー |
| type | VARCHAR | `REFERENCE` |
| status | VARCHAR | `PENDING`→`PROCESSING`→`DONE`または`FAILED` |
| input | TEXT | `ReferenceJobInput`をJSON化したもの（受付時に組み立て済み） |
| result | TEXT | 成功時の結果（`AiReferenceResponse`のJSON文字列）。未完了時はnull |
| error_message | TEXT | 失敗時のメッセージ。成功時はnull |
| created_at / updated_at | TIMESTAMP | 作成日時・更新日時 |

### ai_references・ai_reference_links（Flyway `V4__create_ai_references_tables.sql`）

生成された参考資料本体。学習記録（`learning_records`）とは独立したテーブルで、外部キーでの関連は持たせない（「学習記録をつける」は値をコピーして新規作成するだけ）。

| テーブル | 主なカラム |
|---|---|
| `ai_references` | `id`・`user_id`・`interest`（nullable）・`summary_html`・`tag_id`（nullable、既存タグ一致時のみ）・`suggested_tag_name`（nullable、tag_id無し時のみ）・`created_at` |
| `ai_reference_links` | `id`・`ai_reference_id`（FK、`ON DELETE CASCADE`）・`url`・`title`（nullable） |

---

## フロントエンドの実装

`frontend/src/app/(main)/ai/page.tsx`。詳しい画面設計は[screen/overview.md](screen/overview.md)・[screen/flow.md](screen/flow.md)参照。

- `interest`（任意入力、200文字まで）を入力して「参考資料を生成する」→`useGenerateReference`（`POST /api/ai/references`）→`202 + jobId`なら`useAiJob`でポーリング開始、`200 + message`なら即座にメッセージ表示
- ジョブが`DONE`になったら、要約HTML（`summaryHtml`）を`iframe`の`srcDoc`で表示＋参考リンク一覧＋「学習記録をつける」フォーム
- 学習記録の詳細画面から「この記録について参考資料を作る」を押した場合は`/ai?jobId=...`で遷移してくるため、`useSearchParams`で`jobId`を読み取り、最初からそのジョブをポーリング対象にする
- 画面下部に保存済み参考資料の一覧（`useAiReferences`、`GET /api/ai/references`）をタグ絞り込み付きで表示。カードをクリックすると同じ`ReferenceResult`コンポーネントで結果を再表示できる
