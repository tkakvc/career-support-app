# 学習・キャリア統合管理アプリ 要件定義 / 設計概要

> このファイルは開発初期に考えを整理するために書いたラフメモで、正式ドキュメントではないため docs/archive/ に置いている。正式な要件定義は ../requirements.md、正式な設計は ../design.md にまとめてあり、内容が食い違う場合はそちらを優先する。当初は「学習・キャリア統合管理アプリ」として構想していたが、後にMVPのスコープを学習記録管理に絞ったため、本メモ中の学習記録以外の機能（タスク管理・キャリアシート・技術棚卸し・情報収集媒体管理・GitHub連携・AIタスク分解）は構想段階で終わり、実装していない。

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
| Frontend | React + Next.js (App Router) |
| UI Framework | shadcn/ui (Radix UI + Tailwind CSS) |
| サーバー状態管理 | TanStack Query |
| クライアント状態管理 | Zustand |
| フォーム・バリデーション | React Hook Form + Zod |
| Backend | Spring Boot |
| DB | PostgreSQL |
| 認証 | Spring Security + JWT |
| インフラ | Docker / AWS |
| API連携 | GitHub API / OpenAI API |
| デプロイ | EC2 + RDS |

---

# 6. Next.js / shadcn/ui を使う理由

# 結論

## Next.js (App Router)
→ React の標準フレームワークとして採用

理由：

- React は国内外の求人で最も需要が高い（転職で直結する）
- App Router により pages/ ではなく app/ でルーティングを管理する
  - 例：`app/study/page.tsx` にファイルを置くだけで `/study` にルーティングされる
- サーバーコンポーネント / クライアントコンポーネントの概念を学べる
- Vercel との親和性が高く、デプロイが容易

---

## shadcn/ui
→ UIコンポーネントライブラリとして採用

理由：

- **コンポーネントを「コピー」して自分のコードベースに持つ**設計思想
  - `npx shadcn-ui@latest add button` を実行すると `components/ui/button.tsx` が生成される
  - Vuetify のように「ライブラリが管理するコンポーネント」ではなく、自分で所有するコードになる
- Radix UI（アクセシビリティ対応のヘッドレスUI）+ Tailwind CSS の組み合わせ
- 企業の採用率が急上昇しており、ポートフォリオでのアピール度が高い
- デザインのカスタマイズ自由度が高い

---

## TanStack Query（旧 React Query）
→ APIデータ取得・キャッシュ管理として採用

理由：

- `useQuery` でAPIを呼ぶだけで、ローディング状態・エラー状態・キャッシュを自動管理する
  ```tsx
  // これだけで /api/v1/study-records の取得・再フェッチ・キャッシュが完結する
  const { data, isLoading } = useQuery({
    queryKey: ['studyRecords'],
    queryFn: studyService.getAll,
  });
  ```
- 実務で最も使われているデータフェッチライブラリ

---

## Zustand
→ クライアント側の状態管理（ログイン状態など）として採用

理由：

- Redux より大幅にシンプル
- JWTトークンやログインユーザー情報を `authStore.ts` 1ファイルで管理できる

---

## React Hook Form + Zod
→ フォーム入力・バリデーションとして採用

理由：

- React Hook Form: フォームの状態管理を簡潔に書ける
- Zod: TypeScript で型安全なバリデーションスキーマを定義できる
  ```tsx
  // Zodスキーマで「contentは最大2000文字」というルールを型として定義する
  const schema = z.object({
    content: z.string().max(2000),
    duration: z.number().min(1),
  });
  ```

---

# 7. 機能一覧

| 機能 | 内容 |
|---|---|
| ログイン | JWT認証 |
| ユーザー管理 | ユーザー情報管理 |
| 学習記録管理 | 学習内容CRUD |
| タスク管理 | タスクCRUD |
| キャリアシート管理 | 将来像・振り返り管理 |
| 技術棚卸し | 技術一覧・経験整理 |
| 情報収集媒体管理 | Qiita / Zenn / Udemy等管理 |
| GitHub連携 | GitHub API取得 |
| AI学習提案 | OpenAI API利用 |
| AIタスク分解 | タスク分解支援 |
| ダッシュボード | 一覧表示 |
| Docker対応 | Docker Compose |
| AWSデプロイ | EC2 + RDS |

---

# 8. 設計方針

# 8.1 基本方針

- MVP重視
- 小さく作る
- 実務感を重視
- 過度な複雑化を避ける

---

# 8.2 アーキテクチャ

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

Next.js (App Router) 構成を利用し、

- app（ルーティング + ページコンポーネント）
- components（UI・機能別コンポーネント）
- hooks（TanStack Query ラッパー等のカスタムフック）
- services（APIリクエスト処理）
- store（Zustand ストア）
- lib（Zodスキーマ・ユーティリティ）

を整理する。

---

# 8.3 API設計

REST APIを採用する。

例：

- GET /tasks
- POST /tasks
- PUT /tasks/{id}
- DELETE /tasks/{id}

---

# 8.4 認証認可

JWT認証を採用する。

## 理由

- SPAとの相性が良い
- 実務利用が多い
- Spring Security学習になる

---

# 9. 機能詳細

# 9.1 学習記録管理

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

# 9.2 タスク管理

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

# 9.3 キャリアシート

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

# 9.4 技術棚卸し

## 概要

過去に学習した技術を整理する。

## 例

| 技術 | 経験 |
|---|---|
| Vue2 | 2年 |
| Spring Boot | 半年 |
| Docker | 学習中 |

---

# 9.5 情報収集媒体管理

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

# 9.6 GitHub連携

## 概要

GitHub APIを利用し、
GitHub活動を取得する。

## 取得内容

- Repository一覧
- Commit履歴
- Contributions
- Issue一覧

---

# 9.7 AI学習提案

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

# 9.8 AIタスク分解

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

# 10. DB設計（概要）

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

# 11. AWS構成

| サービス | 用途 |
|---|---|
| EC2 | アプリ実行 |
| RDS | PostgreSQL |
| S3 | 画像保存（必要時） |

---

# 12. セキュリティ

- JWT認証
- Password Hash化
- Validation
- 環境変数管理
- CORS制御
- API認可制御

---

