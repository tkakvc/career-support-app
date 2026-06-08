# 学習・キャリア統合管理アプリ 要件定義 / 設計概要

# 1. システム概要

学習、キャリア、技術情報収集、タスク管理を一元管理する個人向けWebアプリケーション。

単なるTODOアプリではなく、

- 学習記録
- キャリア整理
- 技術情報収集
- AI学習支援
- GitHub連携
- 技術棚卸し

を統合し、
「エンジニアとして継続的に成長するための個人用プラットフォーム」
を目指す。

---

# 2. 背景

技術学習やキャリア形成において、

- 学習記録
- キャリア整理
- 情報収集
- タスク管理
- 技術棚卸し

が複数サービスへ分散しやすい。

また、

- 過去何を学んだか
- 何を目指しているか
- 次に何をやるべきか

を忘れやすく、
継続的な学習が難しくなる。

---

# 3. 目的

以下を実現する。

- 学習内容を一元管理する
- キャリア情報を整理する
- 技術情報収集を集約する
- AIによる学習補助を行う
- GitHub活動を可視化する
- モダンWebアプリケーション構成を学習する

---

# 4. 想定ユーザー

## メインターゲット

- Webエンジニア
- 転職活動中エンジニア
- 技術学習中エンジニア

---

# 5. システム構成

| 項目 | 技術 |
|---|---|
| Frontend | Vue3 + Nuxt |
| UI Framework | Vuetify |
| Backend | Spring Boot |
| DB | PostgreSQL |
| 認証 | Spring Security + JWT |
| インフラ | Docker / AWS |
| API連携 | GitHub API / OpenAI API |
| デプロイ | EC2 + RDS |

---

# 6. Nuxt / Vuetify を使うべきか

# 結論

## Nuxt
→ 使った方が良い

理由：

- Vue3学習になる
- ディレクトリ構成整理しやすい
- 実務感がある
- SPA構成理解しやすい

---

## Vuetify
→ 使った方が良い

理由：

- UI作成速度が大幅に上がる
- 見た目が整う
- 実装コスト削減
- AIとの相性が良い

---

# 7. 機能一覧

| 機能 | 内容 | 工数(概算) |
|---|---|---|
| ログイン | JWT認証 | 8h |
| ユーザー管理 | ユーザー情報管理 | 4h |
| 学習記録管理 | 学習内容CRUD | 10h |
| タスク管理 | タスクCRUD | 10h |
| キャリアシート管理 | 将来像・振り返り管理 | 8h |
| 技術棚卸し | 技術一覧・経験整理 | 6h |
| 情報収集媒体管理 | Qiita / Zenn / Udemy等管理 | 8h |
| GitHub連携 | GitHub API取得 | 10h |
| AI学習提案 | OpenAI API利用 | 12h |
| AIタスク分解 | タスク分解支援 | 8h |
| ダッシュボード | 一覧表示 | 10h |
| Docker対応 | Docker Compose | 6h |
| AWSデプロイ | EC2 + RDS | 10h |

---

# 8. 想定総工数

## AI活用前提

# 約90〜120時間

---

# 9. 設計方針

# 9.1 基本方針

- MVP重視
- 小さく作る
- 実務感を重視
- 過度な複雑化を避ける

---

# 9.2 アーキテクチャ

## Backend

レイヤードアーキテクチャ

- Controller
- Service
- Repository
- Entity
- DTO

を分離する。

---

## Frontend

Nuxt構成を利用し、

- pages
- components
- composables
- services

を整理する。

---

# 9.3 API設計

REST APIを採用する。

例：

- GET /tasks
- POST /tasks
- PUT /tasks/{id}
- DELETE /tasks/{id}

---

# 9.4 認証認可

JWT認証を採用する。

## 理由

- SPAとの相性が良い
- 実務利用が多い
- Spring Security学習になる

---

# 10. 機能詳細

# 10.1 学習記録管理

## 概要

日々の学習内容を記録する。

## 項目

| 項目 | 内容 |
|---|---|
| タイトル | 学習タイトル |
| 内容 | 学習詳細 |
| カテゴリ | Spring / AWS 等 |
| 学習時間 | 時間 |
| 日付 | 学習日 |

---

# 10.2 タスク管理

## 概要

技術学習や転職活動タスクを管理する。

## 項目

| 項目 | 内容 |
|---|---|
| タイトル | タスク名 |
| 内容 | 詳細 |
| 優先度 | 高 / 中 / 低 |
| ステータス | 未着手 / 進行中 / 完了 |
| 期限 | 期限日 |

---

# 10.3 キャリアシート

## 概要

キャリア情報を記録する。

## 管理内容

- 数年後の目標
- やりたい技術
- 振り返り
- 仕事でやったこと
- 強み / 弱み
- 転職理由整理

---

# 10.4 技術棚卸し

## 概要

過去に学習した技術を整理する。

## 例

| 技術 | 経験 |
|---|---|
| Vue2 | 2年 |
| Spring Boot | 半年 |
| Docker | 学習中 |

---

# 10.5 情報収集媒体管理

## 概要

普段利用する技術媒体を管理する。

## 管理対象例

- Qiita
- Zenn
- Udemy
- paiza
- Ping-t
- YouTube
- Connpass
- GitHub

## 目的

- 存在を忘れない
- 学習導線整理
- 技術収集の一元化

---

# 10.6 GitHub連携

## 概要

GitHub APIを利用し、
GitHub活動を取得する。

## 取得内容

- Repository一覧
- Commit履歴
- Contributions
- Issue一覧

---

# 10.7 AI学習提案

## 概要

OpenAI APIを利用し、
次に学ぶべき内容を提案する。

## 例

入力：

- Spring Boot学習中
- React未経験
- AWS経験少

出力：

- REST API実装推奨
- React Hooks推奨

---

# 10.8 AIタスク分解

## 概要

入力したタスクをAIが分解する。

例：

入力：
「JWTログインを実装したい」

出力：

- JWT発行API
- Security設定
- Login画面
- Token保存処理

---

# 11. DB設計（概要）

# 主テーブル

| テーブル | 内容 |
|---|---|
| users | ユーザー |
| study_records | 学習記録 |
| tasks | タスク |
| career_notes | キャリア情報 |
| tech_skills | 技術棚卸し |
| learning_resources | 学習媒体 |

---

# 12. AWS構成

| サービス | 用途 |
|---|---|
| EC2 | アプリ実行 |
| RDS | PostgreSQL |
| S3 | 画像保存（必要時） |

---

# 13. セキュリティ

- JWT認証
- Password Hash化
- Validation
- 環境変数管理
- CORS制御
- API認可制御

---

# 14. このポートフォリオでアピールしたいこと

- Spring Boot
- REST API
- JWT認証
- Vue/Nuxt
- Vuetify
- Docker
- AWS
- GitHub API
- OpenAI API
- CRUD設計
- 実務感ある設計
- AI活用
- UI/UX意識

---

# 15. やらないこと

- マイクロサービス
- Kubernetes
- 高度DDD
- Kafka
- 複雑リアルタイム同期
- 大規模AI基盤
