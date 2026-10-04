# AIガードレール設計書

対象：`AiService`（`backend/src/main/java/com/example/backend/service/AiService.java`）・`AiConfig`。実際のコードは[implementation.md](implementation.md#ガードレール実装)も参照。

| # | 項目 |
|---|---|
| 2 | タイムアウト設定 |
| 3 | リトライ |
| 5 | ログ・コスト計測 |
| 6 | 出力バリデーション |
| 7 | 入力フィルタ（プロンプトインジェクション対策） |
| 8 | 開示UI（画面側） |
| 9 | 有害コンテンツ判定（OpenAI Moderation API） |

---

## 2. タイムアウト設定

### 課題

`application.yaml`の`spring.ai.openai.chat.options`には`temperature`や`model`は設定できるが、「通信が何秒で諦めるか（タイムアウト）」を設定する項目はここには無い。この2つは階層が別：

| 種類 | 具体例 | 何を指定するか |
|---|---|---|
| `chat.options` | `temperature`・`model` | OpenAI**に送る**パラメータ。AIがどう応答を組み立てるかを指定するもの |
| タイムアウト | `connectTimeout`・`readTimeout` | 通信**そのもの**の設定。「何秒待って応答が無ければ諦めるか」 |

<details>
<summary>なぜ chat.options ではタイムアウトを設定できないか（詳しく）</summary>

**タイムアウトが無いと具体的に何が起きるか：** OpenAI側が何らかの理由（サーバー障害・回線の問題など）で応答を返さなくなったとする。タイムアウトの設定が無いと、Spring Bootはそのリクエストを処理しているスレッド（Tomcatの1本）を、**応答が来るまで無期限に**待たせ続ける。1秒後でも、10分後でも、返事が来ない限り解放されない。他のリクエストを処理できるスレッドが1本減った状態のまま固定される。

**`ChatClient`と`RestClient`の関係：** `ChatClient`はSpring AIが提供する「OpenAIと会話するための部品」。実際にHTTP通信（ネットワーク越しにリクエストを送って返事を受け取る処理）を行っているのは、その内部で使われている`RestClient`という別の部品。図にすると：

```
自分のコード → ChatClient（OpenAI用の会話インターフェース）
                    └→ 内部で RestClient を呼ぶ（実際にHTTP通信する部品）
```

`chat.options`の`temperature`・`model`は、`ChatClient`が組み立てる**リクエストの中身**（OpenAIに「このモデルで」「この程度ランダムに」と伝えるパラメータ）。一方「何秒待ったら諦めるか」は、リクエストの中身とは無関係で、実際に通信している`RestClient`自身が持つ設定。`chat.options`をどれだけ設定しても、`RestClient`の設定には一切影響しない。だからタイムアウトを効かせるには`RestClient`の方を直接設定する必要がある。

Tavily（Web検索）呼び出しも同じ構造（`WebSearchClient`が内部で`RestClient`を使っている）なので、同じ理由でこちらにも別途タイムアウトを設定する。

</details>

### 対応

`RestClientCustomizer`はSpring Bootの仕組みで、auto configurationが組み立てる全ての`RestClient.Builder`に対して自動的に適用される。Spring AIの`OpenAiApi`も内部でこの仕組みに乗って`RestClient`を作るので、Beanを1つ足すだけで反映される。

```java
// AiConfig.java
@Bean
public RestClientCustomizer aiRestClientCustomizer() {
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(5))
            .withReadTimeout(Duration.ofSeconds(30));
    return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
}
```

- `connectTimeout`：TCP接続を確立するまでの時間。5秒あれば十分（OpenAI側の接続確立自体は速い）
- `readTimeout`：接続後、レスポンスが返り切るまでの時間。OpenAIは30秒、Tavilyは10秒（`WebSearchClient`内で個別に設定）

---

## 3. リトライ

### 課題

一時的な障害（タイムアウト・OpenAI側の5xx）でも、リトライ無しではユーザーは即座にエラーを見ることになる。一方で429（レート制限）は再試行しても同じ理由で失敗し、無駄にAPI呼び出し回数（＝コスト）を増やすだけなので区別が要る（[api/error.md](api/error.md)の「429はリトライしない」というルールと対応）。

### 対応

`@Retryable`（Spring Retry）を使う方法もあるが、内部でAOPプロキシを介して制御されるため、呼び出し箇所を読むだけでは再試行の挙動が追いにくい。依存を増やさず、制御フローが一目で分かるよう明示的な`for`ループで実装する（`AiService.callOpenAi()`）。

```java
private String callOpenAi(UUID userId, String type, Supplier<String> call) {
    for (int attempt = 0; ; attempt++) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            // タイムアウト・接続エラー（一時的な障害とみなしてリトライ対象にする）
            if (attempt >= MAX_RETRY) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, AI_FAILURE_MESSAGE);
            }
            sleep(RETRY_INTERVAL);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 429（レート制限）はリトライせず、そのままクライアントに429として伝播させる
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "OpenAI APIのレート制限に達しました");
        }
    }
}
```

このリトライ機構はOpenAI呼び出しにのみ適用する。Tavily呼び出し（`WebSearchClient.search()`）にはリトライを実装していない。失敗すればそのまま例外が`AiJobWorker`まで伝播しジョブが`FAILED`になるだけで、ユーザーがもう一度ボタンを押せば良いという考え方で許容している。

### タイムアウトとの合わせ技での上限時間

- 通信タイムアウト30秒 × 最大2回（初回＋リトライ1回）＋ リトライ間隔2秒 ＝ 最悪ケースで約62秒
- ALB（Application Load Balancer）のデフォルトのアイドルタイムアウトは60秒のため、この62秒はそのままではALB側で先にコネクションが切られるリスクがある。この処理をSQS経由の非同期処理にしている（[implementation.md](implementation.md)の「非同期化（SQS）」参照）ことで、OpenAI呼び出しの待ち時間はバックグラウンドの`AiJobWorker`側で発生し、クライアントとのHTTP接続を占有しなくなるため、ALBのタイムアウトとは無関係になる

---

## 5. ログ・コスト計測

### 対応

`callOpenAi()`の中でSLF4Jのloggerを使い、呼び出しごとに成功/失敗とレイテンシを記録する。

```java
long start = System.currentTimeMillis();
try {
    String result = call.get();
    log.info("ai_call type={} userId={} latencyMs={} result=success", type, userId, System.currentTimeMillis() - start);
    return result;
} catch (Exception e) {
    log.warn("ai_call type={} userId={} latencyMs={} result=failure error={}", type, userId, System.currentTimeMillis() - start, e.getMessage());
    throw ...;
}
```

- `logging.level.org.springframework.ai: WARN`（[implementation.md](implementation.md)参照、APIキー漏洩防止用）はこのアプリ独自ログの出力レベルには影響しない
- 発展（Actuator + Micrometerでのメトリクス化）は未着手。優先度は低い

---

## 6. 出力バリデーション

### 課題

`parseJson()`は「JSONとして構文的に読めるか」しかチェックしていない。OpenAIが極端に長い文字列や空文字列を返しても、そのままクライアントに渡ってしまう可能性がある。

### 対応

パース後に`validateLlmOutput()`で妥当性チェックを挟む。

```java
private void validateLlmOutput(LlmOutput output) {
    if (output.summaryMarkdown() == null || output.summaryMarkdown().isBlank()
            || output.summaryMarkdown().length() > 20000) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIのレスポンスが不正です");
    }
    if (output.tagName() == null || output.tagName().isBlank() || output.tagName().length() > 50) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIのレスポンスが不正です");
    }
}
```

エラー時の扱いはJSONパース失敗時と同じ500に寄せる。画面側は追加対応不要（[error.md](api/error.md)の既存の500ハンドリングをそのまま使う）。

---

## 7. 入力フィルタ（プロンプトインジェクション対策）

### 課題

`interest`は文字数制限（200文字）はあるが自由記述。system/user分離（プロンプトインジェクション対策）が主防御としてすでにあるため必須ではないが、多層防御として安価に足せる。

### 対応

代表的なインジェクション文言を弾く簡易フィルタを`interest`に対して追加する（`validateGoalContent`）。

```java
private static final List<String> BLOCKED_PATTERNS = List.of(
        "ignore previous", "ignore all previous", "これまでの指示を無視",
        "以前の指示を無視", "システムプロンプト", "system prompt"
);

private void validateGoalContent(String text) {
    String lower = text.toLowerCase();
    boolean blocked = BLOCKED_PATTERNS.stream().anyMatch(lower::contains);
    if (blocked) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MODERATION_REJECT_MESSAGE);
    }
}
```

- 完全な対策ではない（表現を変えられれば回避される）ことを前提に、「安価な一次フィルタ」という位置づけで良い
- `400`として返す（[error.md](api/error.md)参照）

---

## 8. 開示UI（画面側）

バックエンドの変更は無い。画面設計側の追記のみ。詳細は[screen/overview.md](screen/overview.md#外部送信の開示)を参照。

---

## 9. 有害コンテンツ判定（OpenAI Moderation API）

### 課題

項目7の入力フィルタ（NGワード方式）は「プロンプトインジェクション対策の二次防御」であり、目的が異なる。犯罪・自傷・暴力等の内容が入力された場合を弾く仕組みが別途要る。

NGワードの文字列一致では、意味は明らかに有害でも特定の単語を含まない文章（言い換え表現等）を検知できない。実務では専用の判定モデルにテキストを通し、カテゴリごとの危険度スコアで判定するのが一般的。

### 対応

Spring AIがOpenAIのModeration API向けに用意している`ModerationModel`を使う。`ChatClient`と同じ「Spring AIの部品にプロンプトを渡して結果を受け取る」構造なので、扱い方の学習コストは低い。呼び出し自体は無料（追加のOpenAI利用料は発生しない）。

```java
private void checkModeration(String text) {
    ModerationPrompt prompt = new ModerationPrompt(text, OpenAiModerationOptions.builder().model(MODERATION_MODEL).build());
    Moderation moderation = moderationModel.call(prompt).getResult().getOutput();
    boolean flagged = moderation.getResults().get(0).isFlagged();
    if (flagged) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MODERATION_REJECT_MESSAGE);
    }
}
```

`interest`・学習記録の内容（`recordsText`）・`focusRecordText`を結合した文字列に対して通す（結局OpenAIに送られる入力である点は同じなので、入力経路として同列に扱う）。

モデル名（`MODERATION_MODEL = "omni-moderation-latest"`）を呼び出し側で明示しているのは、Spring AI 1.0.0-M6の`OpenAiModerationModel`自動設定が`application.yaml`の`spring.ai.openai.moderation.options.model`を反映しないバグがあるため。指定しないとライブラリ内蔵のデフォルト（`text-moderation-latest`、OpenAI側で既に廃止済み）が使われ、400エラーになる（実際に動かして発覚・修正した）。

### 項目7（NGワード）との役割分担

| | 対象 | 検知方法 |
|---|---|---|
| 項目7 入力フィルタ | プロンプトインジェクション文言 | 固定文字列の一致（安価・粗い） |
| 項目9 Moderation API | 犯罪・暴力・自傷・ヘイト等の有害内容 | 学習済み判定モデルによる意味的な分類 |

目的が異なるため、どちらか一方に統合せず両方残す。`400`のエラーレスポンス（[error.md](api/error.md)）は同じ形にまとめてよい。
