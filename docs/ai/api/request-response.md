# リクエスト・レスポンス定義

---

## POST /api/ai/references　参考資料生成API

### 概要

ログインユーザーの直近30件の学習記録とタグ情報、任意入力の `interest`（興味のある技術・分野）、または特定の学習記録（`recordId`）をもとにWeb検索を行い、得られた記事とあわせてOpenAIに送って要約する。生成した参考資料（要約HTML・参考リンク一覧・分類先のタグ）を保存する。

ユーザーIDはJWTから取得する。

### リクエスト

```json
{ "interest": "フロントエンドよりバックエンドを伸ばしたい" }
```

```json
{ "recordId": "1e2d3c4b-5678-4a5b-8c9d-abcdef123456" }
```

| フィールド | 型 | 必須 | 制約 |
|---|---|---|---|
| interest | string | × | 200文字以内。省略時は学習記録のみをもとに検索・要約する |
| recordId | string(UUID) | × | 学習記録の詳細画面から「この記録について参考資料を作る」で生成する場合のみ指定する。指定した記録の内容を最優先の材料として使う。存在しない、または他人の記録の場合は404 |

`interest`と`recordId`（またはその記録のタグ）は併用できる。`recordId`が指定されている場合、学習記録の件数や`interest`の有無にかかわらず必ずジョブを作成する。`recordId`が指定されておらず、学習記録が0件、かつ `interest` も省略された場合はWeb検索・OpenAIを呼ばず、固定メッセージ（後述）を返す。

### 内部処理の流れ（概要）

```
1. recordId指定時はその記録を取得し、他の材料より優先する
2. 学習記録・interest・（recordId指定時はその記録のタグ）から検索クエリを組み立てる
3. Web検索APIを呼び、関連記事を取得する
4. 検索結果をOpenAIに送り、Markdown形式の要約を生成する
5. MarkdownをHTMLに変換する
6. 分類先のタグを決める（既存タグに一致すればそれ、一致しなければ新規作成せず提案名だけ保持）
7. 参考リンク一覧・要約HTML・分類先のタグ（または提案タグ名）をai_referencesに保存する
```

### レスポンス

`POST /api/ai/references`はSQS経由の非同期処理。このエンドポイント自体は結果を待たず、ジョブを受け付けたことだけを返す。

**`recordId`が指定されている、学習記録が1件以上、または`interest`が入力されている場合（202 Accepted）**

```json
{ "jobId": "b3f1c2e0-1234-4a5b-8c9d-abcdef123456" }
```

**`recordId`未指定、学習記録が0件、かつ`interest`も未入力の場合（200 OK、ジョブは作らない）**

```json
{ "message": "学習記録がまだありません。記録を追加すると参考資料が生成できるようになります。" }
```

結果は `GET /api/ai/jobs/{jobId}` を`jobId`付きでポーリングして取得する（後述）。

---

## GET /api/ai/jobs/{jobId}　ジョブ状態取得API

### 概要

`POST /api/ai/references`が返した`jobId`を使って、生成の進行状況と結果を取得する。フロントは数秒おきにこのAPIを呼び、`status`が`DONE`または`FAILED`になるまでポーリングする。

### リクエスト

パスパラメータ`jobId`のみ。ボディなし。

### レスポンス（200 OK）

```json
// 処理待ち・処理中
{ "status": "PENDING" }
{ "status": "PROCESSING" }

// 生成が完了した場合
{
  "status": "DONE",
  "result": {
    "summaryHtml": "<h2>Spring Securityの認可設定</h2><p>…</p>",
    "links": [
      { "url": "https://...", "title": "Spring Security Reference" }
    ],
    "tagId": "3fae...",
    "tagName": "Spring Security"
  }
}

// 失敗した場合
{ "status": "FAILED", "message": "現在AIサービスが利用できません。しばらく経ってから再度お試しください" }
```

- `jobId`が存在しない、または自分以外のユーザーのジョブである場合は`404`を返す（他人のジョブIDを推測して結果を覗き見できないようにするため、「存在しない」と「他人のもの」を区別せず同じ404にする）
- `tagId`/`tagName`は、AIが提案したタグ名が既存タグに一致した場合だけ入る。一致しなかった場合は両方省略され、代わりに`suggestedTagName`（まだ作成されていない提案タグ名）が入る。タグの新規作成はここでは行わず、ユーザーが「学習記録をつける」等の操作をした時点で初めて作られる

---

## GET /api/ai/references　保存済み参考資料一覧取得API

### 概要

過去に生成し保存済みの参考資料を一覧取得する。

### リクエスト

```
GET /api/ai/references?tag={tagId}
```

| クエリパラメータ | 必須 | 説明 |
|---|---|---|
| tag | × | タグID。指定するとそのタグが付いた参考資料だけに絞り込む。省略時は全件返す |

ページングの詳細は実装時に確定する。

### レスポンス（200 OK）

```json
{
  "references": [
    {
      "id": "d1e2f3...",
      "interest": "フロントエンドよりバックエンドを伸ばしたい",
      "summaryHtml": "<h2>...</h2>",
      "links": [ { "url": "https://...", "title": "Spring Security Reference" } ],
      "tagId": "3fae...",
      "tagName": "Spring Security",
      "createdAt": "2026-09-19T10:00:00Z"
    }
  ]
}
```

---

タグ分類の結果は`tagId`/`tagName`（既存タグに一致した場合）と`suggestedTagName`（一致しなかった場合）に分かれる。タグの新規作成はユーザーが「学習記録をつける」等の操作をした時点まで遅延させる設計（詳しくは[usecase.md](usecase.md)の「タグ分類の扱い」参照）。
