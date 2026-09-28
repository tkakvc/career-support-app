# AI API 概要

このAPIは、ユーザーの学習記録・興味（または特定の学習記録）をもとにWeb検索を行い、得られた最新情報をOpenAIで要約した参考資料を生成する。生成した参考資料は既存のタグで分類して保存する。

## 主な機能

- 情報収集・要約：直近の学習内容・任意の興味・または学習記録の詳細画面から指定した特定の1件をもとにWeb検索を行い、関連する最新情報を取得してHTML形式の参考資料として要約する
- タグ分類：生成した参考資料を、既存タグに一致させる。一致しない場合はタグを新規作成せず、AIが提案したタグ名だけを保存する（実際の作成はユーザーが学習記録をつける操作をした時点まで行わない）

- 学習記録の詳細画面から特定の記録を起点に生成する導線がある
- タグの新規作成は非同期ジョブでは行わない（詳しくは[usecase.md](usecase.md)のタグ分類の扱い参照）

## 共通仕様

| 項目 | 内容 |
|---|---|
| ベース URL | `/api` |
| 認証 | `Authorization: Bearer {JWT}` ヘッダー必須 |
| Content-Type | `application/json` |
| レスポンス形式 | JSON |

## 使用モデル

| 項目 | 内容 |
|---|---|
| モデル | `gpt-4o-mini` |
| 選定理由 | コストが低く、テキスト生成・JSON出力の精度が十分。参考資料生成用途には過剰スペック不要 |

## Web検索API

| 項目 | 内容 |
|---|---|
| サービス | Tavily（LLM向けに作られた検索API） |
| 認証 | API Key（環境変数 `TAVILY_API_KEY`） |
| 呼び出し方 | Spring の `RestClient`（`WebSearchClient`） |

## OpenAI APIキーの管理

APIキーは環境変数 `OPENAI_API_KEY` で管理する。`application.yaml` に直接書かない。

```yaml
app:
  openai:
    api-key: ${OPENAI_API_KEY}
```

## 実装方針

Spring AI（`spring-ai-openai-spring-boot-starter`）を使う。
Spring公式のAI統合ライブラリで、OpenAI APIの呼び出し・プロンプト管理・レスポンスパースを簡潔に書ける。

RestTemplateで手書きするより保守性が高く、モデルの切り替えも容易。
