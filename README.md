# 学習記録管理アプリ（career-support-app）

学習記録を一元管理し、タグ分類・ファイル添付・AIによる最新情報の収集＆提案までを行う個人開発 Web アプリ。**Spring Boot の実装力を伸ばすこと・React をキャッチアップすること・1つのサービスのAWSインフラを自力で構築すること**を目的に作った（転職・学習目的のポートフォリオ）。

現在は AWS 本番環境を構築中（ECS タスクは `desired_count=0` で一時停止、CD 未整備）のため、公開デモ URL は無し。[ローカルでの動かし方](#ローカルでの動かし方)を参照（スクリーンショットは準備中）。

---

## これは何のためのプロジェクトか

### このPFを作った理由

身につけたい技術が先にあり、それを一通り使う題材として学習記録管理アプリを選んだ。題材に学習記録を選んだのは、自分自身が学習を記録する必要があり、実際に使うものであれば要件の妥当性を自分で判断しながら作れるため。

- **Spring Boot**：実務で Java・Spring 系の開発は経験してきたが、Spring Boot 自体を改めて基礎から押さえ直し、認証・外部API連携・非同期処理まで自分で実装できる状態にしたかった
- **React**：フロントエンドは Vue2・TypeScript で約3年の実務経験があるが、現在広く使われている React は未経験だったため、ここでキャッチアップしておきたかった
- **認証（JWT）**：ライブラリに任せて動かすのではなく、アクセストークンとリフレッシュトークンの発行・失効を自分で実装して、仕組みを理解したかった
- **AWSインフラ**：AWS SAA の取得、実務での構成把握・運用、支出分析サービスの入稿システムでの構築、Terraform による環境移行と、断片的には経験してきた。ただし**1つのサービスのインフラを自力で構築しきる**経験が無かったため、それをやっておきたかった
  - 本プロジェクトは構成を理解することを優先してマネジメントコンソールで構築し、その後 Terraform でコード化した
  - もう1つのポートフォリオ（ETL PF）では逆に最初から Terraform で構築しており、**2つのPFで意図的にやり方を分けている**

### 実務での経験（規模感）

- ローコード開発基盤のプロダクトで、大規模顧客向け**タグ機能の企画・設計・実装を主担当**
  - 「進捗を管理したい」「品質でフィルタしたい」といった個別要望の本質は「リソースへの属性付与」に集約できると整理し、個別機能ではなく汎用的なタグ機能として作る方針を提案した
  - 顧客ヒアリング・要件整理・API設計・ER設計・UI設計・フロントエンド実装まで一貫して担当（UIはGitHubのLabel機能を参考にした）
  - 本プロジェクトのタグ設計（デフォルトタグ＋ユーザー作成タグのハイブリッド方式、学習記録とのM:N関連）は、このときの設計経験を下敷きにしている
- BtoB SaaS の新規事業でサブリーダーを担当。CSVによる数万〜数百万レコード規模のデータ入稿機能や、取引先管理機能の開発に従事
- Terraform による本番環境を含む AWS アカウント移行を、自分の担当マイクロサービス領域について実施

### 作る過程で踏み込んだこと

当初の学習目的には入れていなかったが、題材として成立させる中で必要になり、設計から踏み込んだ領域。

- **AI連携**（Spring AI + OpenAI）：RAG・プロンプトインジェクション対策・レート制限といったガードレール設計込み
- **SQSによる非同期ジョブ**：リクエスト受付と重い処理の分離、再配信を前提とした冪等性の設計
- **Flywayによるマイグレーション管理**：`ddl-auto`任せをやめ、スキーマ変更をバージョン管理する形に移行
- **GitHub ActionsによるCI**：バックエンドのテストとフロントエンドのlint・buildを自動化

もう1つのポートフォリオ [csv-data-pipeline](../csv-data-pipeline)（ETL PF）は **AWSインフラ設計・データパイプライン構築** に力点を置いており、このプロジェクトは **アプリケーション（バックエンド・フロントエンド）の実装** に力点を置いている。

| | 本プロジェクト（学習記録PF） | csv-data-pipeline（ETL PF） |
|---|---|---|
| 力点 | アプリケーション実装（バックエンド・フロントエンド） | AWSインフラ設計・データパイプライン構築 |
| アーキテクチャ | ECS Fargate（常時起動のWebアプリ） | イベント駆動（S3→Lambda）＋バックフィル用Fargate |
| 認証方式 | 自前JWT（アクセストークン＋リフレッシュトークン） | OIDC（Google） |
| 実務との関係 | ポートフォリオとして新規設計 | 実務で扱った仕組みを、当時できなかった部分まで一から作り直したもの |

学習記録・タスク管理・キャリアシートまで含めた統合管理アプリとして構想していたが、全部浅く作るより「学習記録」1機能を深く作り込む（タグ分類・ファイル添付・検索条件の組み合わせ・AIによる情報収集＆提案）方が実務で使われる設計パターンを身につける上で効果が高いと判断し、MVPのスコープを学習記録管理に絞った。

---

## アーキテクチャ

```mermaid
flowchart TD
    User["利用者（ブラウザ）"]
    CF["CloudFront"]
    ALB["ALB"]
    Next["Next.js (React + shadcn/ui)<br/>ECS Fargate"]
    Spring["Spring Boot<br/>ECS Fargate"]
    RDS[("RDS PostgreSQL")]
    Redis[("ElastiCache Redis<br/>リフレッシュトークン・AIレート制限")]
    S3["S3<br/>添付ファイル保存"]
    SQS[["SQS<br/>ai-jobsキュー"]]
    Tavily["Tavily<br/>Web検索API"]
    OpenAI["OpenAI API"]

    User -->|HTTPS| CF --> ALB
    ALB --> Next
    ALB --> Spring
    Spring --> RDS
    Spring --> Redis
    Spring -->|アップロード/ダウンロード| S3
    Spring -->|①参考資料生成を受付・ジョブ登録| SQS
    SQS -->|②@SqsListenerで自分自身が受信| Spring
    Spring -->|③検索クエリ実行| Tavily
    Spring -->|④検索結果を元に要約生成| OpenAI
```

- AI機能（参考資料生成）はSQSで非同期化しているが、**コンシューマーはSpring Boot自身**（`@SqsListener`）。Lambdaは使わない（受付処理と生成処理の責務を分けたいだけで、別ランタイムに切り出す必要性は無いと判断した）
- 詳細設計・DB設計・API設計は [docs/design.md](./docs/design.md)、要件は [docs/requirements.md](./docs/requirements.md)

---

## 使用技術

| 領域 | 技術 |
|---|---|
| フロントエンド | Next.js（App Router）+ React、shadcn/ui（Radix UI + Tailwind CSS）、TanStack Query、Zustand、React Hook Form + Zod |
| バックエンド | Spring Boot、Spring Security、Spring Data JPA、Spring AI（OpenAI連携）、Spring Cloud AWS（SQS） |
| データベース | PostgreSQL（RDS）、Redis（ElastiCache） |
| 認証 | 自前JWT（アクセストークン15分＋リフレッシュトークン14日をRedisで失効管理） |
| 外部API連携 | OpenAI API（gpt-4o-mini、要約生成・Moderation）、Tavily（Web検索API） |
| インフラ | ECS Fargate、ALB、CloudFront、ACM、RDS、ElastiCache、S3、SQS、VPC（パブリック/プライベートサブネット） |
| IaC | Terraform（VPC・サブネット・SG・ALB・ECS・RDS・ElastiCache・S3・CloudFront・SQS・IAM/OIDC） |
| CI | GitHub Actions（バックエンド`./gradlew test`・フロントエンド`lint`+`build`） |

このPFで新しく取り組んだのは実質 **Next.js（App Router）・React・Spring Security（JWT認証）・Spring AI（OpenAI連携）・SQSによる非同期処理** の5つ。Java・Spring Boot・PostgreSQL は実務経験の延長で、認証・外部API連携・非同期処理は自分で実装したかった領域。

---

## データベース設計

```
users
  └─ learning_records (1:N)
       ├─ learning_record_attachments (1:N)
       └─ tags (M:N、中間テーブル: learning_record_tags)

users
  └─ ai_jobs (1:N)       … AI参考資料生成の非同期ジョブ状態管理
  └─ ai_references (1:N) … 生成済み参考資料（learning_recordsとは外部キーで結合しない）
       └─ ai_reference_links (1:N)
```

<details>
<summary>各テーブルのカラム定義</summary>

**`users`**

| カラム名 | 型 | 説明 |
|---|---|---|
| id | UUID (PK) | ユーザーID |
| email | VARCHAR(255) UNIQUE | メールアドレス |
| password_hash | VARCHAR(255) | BCryptハッシュ済みパスワード |
| display_name | VARCHAR(100) | 表示名 |

**`learning_records`**

| カラム名 | 型 | 説明 |
|---|---|---|
| id | UUID (PK) | レコードID |
| user_id | UUID (FK) | ユーザーID |
| date | DATE | 学習日 |
| content | TEXT | 学習内容（最大2000文字） |
| duration | INTEGER | 学習時間（分） |

**`tags`**（デフォルトタグ＋ユーザー作成タグのハイブリッド方式）

| カラム名 | 型 | 説明 |
|---|---|---|
| id | UUID (PK) | タグID |
| name | VARCHAR(50) | タグ名 |
| type | VARCHAR(10) | `default`（全ユーザー共通）または`user`（個人作成） |
| created_by | UUID (FK, nullable) | 作成者ID（userタグのみ） |

**`learning_record_attachments`**

| カラム名 | 型 | 説明 |
|---|---|---|
| id | UUID (PK) | 添付ファイルID |
| learning_record_id | UUID (FK) | 学習記録ID |
| file_name | VARCHAR(255) | 元のファイル名 |
| storage_key | VARCHAR(500) | S3保存パス |
| content_type | VARCHAR(100) | MIMEタイプ |
| file_size | BIGINT | ファイルサイズ（バイト） |

**`ai_jobs`**（非同期ジョブの状態管理）

| カラム名 | 型 | 説明 |
|---|---|---|
| id | UUID (PK) | ジョブID（`jobId`としてクライアントに返す） |
| user_id | UUID (FK) | 実行ユーザー |
| status | VARCHAR | `PENDING`→`PROCESSING`→`DONE`または`FAILED` |
| input | TEXT | リクエスト内容をJSON化したもの |
| result | TEXT | 成功時の結果（JSON文字列） |

**`ai_references`・`ai_reference_links`**（生成済み参考資料）

| テーブル | 主なカラム |
|---|---|
| `ai_references` | `id`・`user_id`・`interest`（nullable）・`summary_html`・`tag_id`（既存タグ一致時のみ）・`suggested_tag_name`（不一致時のみAI提案名を保存） |
| `ai_reference_links` | `id`・`ai_reference_id`（FK, `ON DELETE CASCADE`）・`url`・`title` |

全DDLはFlywayマイグレーション（`backend/src/main/resources/db/migration/`）を参照。

</details>

---

## API設計

| 領域 | 主なエンドポイント | 認証 |
|---|---|---|
| 認証 | `POST /api/auth/signup` / `login` / `refresh` / `logout` | login/signupのみ不要 |
| ユーザー | `GET,PATCH /api/users/me`、`PUT /api/users/me/password` | 必須 |
| 学習記録 | `GET,POST /api/learning-records`、`GET,PUT,DELETE /api/learning-records/{id}` | 必須 |
| タグ | `GET,POST /api/tags`、`PUT,DELETE /api/tags/{id}` | 必須 |
| ファイル添付 | `GET,POST /api/learning-records/{id}/attachments`、`DELETE .../{attachmentId}` | 必須 |
| AI参考資料 | `POST,GET /api/ai/references` | 必須（生成は1日10回まで） |

<details>
<summary>リクエスト・レスポンス例</summary>

**`POST /api/auth/login`**

```json
// request
{ "email": "user@example.com", "password": "password123" }

// response
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "refreshToken": "d9f3...ランダムな文字列",
  "refreshExpiresIn": 1209600
}
```

**`POST /api/ai/references`**

```json
// request（interestは任意。未入力でも直近の学習記録から生成する）
{ "interest": "バックエンドを伸ばしたい" }

// response（202：非同期ジョブとして受付）
{ "jobId": "550e8400-e29b-41d4-a716-446655440000" }
```

**共通エラー形式**

```json
{ "error": "メッセージ", "status": 400 }
```

詳細は [docs/design.md](./docs/design.md) の「3. API設計」、機能別の詳細仕様は[ドキュメント一覧](#ドキュメント一覧)参照。

</details>

---

## 設計で意識したこと

- **JWT単体ではなくアクセストークン＋リフレッシュトークン方式にした**
  - 当初はステートレスなJWT1本のみで設計していたが、「発行済みトークンを有効期限まで失効できない（強制ログアウト・盗難時の無効化ができない）」弱点に気づき、アクセストークン（15分・ステートレス）＋リフレッシュトークン（14日・Redisで保存し失効管理可能）に変更した
  - ログアウト・強制失効は「該当リフレッシュトークンをRedisから削除する」だけで実現できる
- **AIが提案したタグ名は、非同期ジョブの中では新規作成しない**
  - 既存の可視タグ（defaultタグ＋自分のuserタグ）に大文字小文字無視で一致するものがあればそれを使い、無ければ作成せず提案名だけを保存する
  - タグ作成は常にユーザーの明示的な操作（学習記録フォームでの保存時）を起点にするという既存の一貫性を、バックグラウンドジョブがAIの判断だけで崩さないようにするため
- **RAG（Retrieval-Augmented Generation）でAIの回答を最新情報に合わせる**
  - OpenAI単体は学習時点までの知識しか持たないため、まずTavilyでWeb検索した結果を取得し、それをプロンプトに含めてから要約させる構成にした
- **OpenAIにはMarkdownを生成させ、サーバー側でHTMLに変換する**
  - 複雑なHTML（閉じタグの対応等）を直接生成させるより、LLMはMarkdownの方が構文を崩さずに出力できるため生成失敗率が下がる
  - 生成したHTMLはフロントで`iframe`の`srcDoc`に渡して表示する（`dangerouslySetInnerHTML`は使わず、OpenAIが生成した内容を親ページから隔離する）
- **プロンプトインジェクション対策として、システムプロンプトとユーザー入力を必ず分離する**
  - 学習記録・interest・Tavilyの検索結果（信頼できない外部サイトの文章）は全てユーザー側プロンプトにのみ入れる
- **AI呼び出しのレート制限・重複防止**
  - レート制限（1日10回）はRedisの`INCR`＋`EXPIRE`で管理
  - 同一ユーザーの未完了ジョブが既にある場合は429を返し、二重送信を防ぐ

---

## ディレクトリ構成

```
career-support-app/
  backend/   # Spring Boot（レイヤードアーキテクチャ：Controller/Service/Repository/Entity）
  frontend/  # Next.js App Router（components/hooks/lib/store）
  infra/     # Terraform（ECS・IAM/OIDC）
  docs/      # 要件定義・設計ドキュメント
```

---

## 今後試してみたいこと

現在はECS Fargate上でSpring BootとNext.jsを動かし、AIによる参考資料生成のみSQSで非同期化する構成にしている。次は「作って動く」の先にある運用面に取り組む予定。

- **本番環境の公開とCD**
  停止中のECSタスクを起動し、GitHub ActionsからECRへのpush・ECSへのデプロイまで自動化する。

---

## ドキュメント一覧

| ドキュメント | 内容 |
|---|---|
| [docs/requirements.md](./docs/requirements.md) | 要件定義書 |
| [docs/design.md](./docs/design.md) | 設計書（アーキテクチャ・DB・API・認証・インフラ・セキュリティ） |
| [docs/frontend/design.md](./docs/frontend/design.md) | フロントエンド設計 |
| [docs/auth/](./docs/auth/) | 認証APIの詳細仕様 |
| [docs/learning-records/](./docs/learning-records/) | 学習記録APIの詳細仕様 |
| [docs/tags/](./docs/tags/) | タグAPIの詳細仕様 |
| [docs/attachments/](./docs/attachments/) | ファイル添付APIの詳細仕様 |
| [docs/settings/](./docs/settings/) | 設定画面APIの詳細仕様 |
| [docs/ai/](./docs/ai/) | AI参考資料機能の詳細仕様・実装設計・ガードレール設計 |

---

## ローカルでの動かし方

事前準備：Docker Desktop / JDK 21 / Node.js

```bash
# 1. DB・Redisを起動
cd backend
docker compose up -d db                              # PostgreSQL: career-postgres (:5432)
docker run -d --name career-redis -p 6379:6379 redis:7

# 2. バックエンド（Spring Boot）
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# jwt.secretを32文字以上の文字列に設定（openai/tavily api-keyはダミーでも起動は通る）
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun        # http://localhost:8080

# 3. フロントエンド（Next.js）
cd ../frontend
npm install
npm run dev                                           # http://localhost:3000
```

- AI機能（参考資料生成）を試す場合は`application-local.yaml`に実際の`OPENAI_API_KEY`・`TAVILY_API_KEY`を設定する
- 動作確認例：`curl -sX POST localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"test@example.com","password":"password123"}'`
