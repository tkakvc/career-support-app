# AI API 概要

このAPIは、ユーザーの学習記録・タグ情報をもとにOpenAIへリクエストを送り、学習提案とタスク分解を返す。

## 主な機能

- 学習提案生成：最近の学習内容を分析し、次に学ぶべき技術を提案する
- タスク分解：「〇〇を実装したい」という目標を具体的なサブタスクに分解する

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
| 選定理由 | コストが低く、テキスト生成・JSON出力の精度が十分。学習提案・タスク分解用途には過剰スペック不要 |

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
