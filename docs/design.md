# 設計書

**プロジェクト名：** 学習・キャリア統合管理アプリ  
**バージョン：** 1.0  
**作成日：** 2026-05-23

---

## 1. アーキテクチャ設計

### 1.1 全体構成図

```
[ブラウザ]
    │
    │ HTTPS
    ▼
[Nuxt (Vue3 + Vuetify)]  ← フロントエンド (EC2 or Vercel)
    │
    │ REST API (JSON / JWT)
    ▼
[Spring Boot]             ← バックエンド (EC2)
    │
    ├─ PostgreSQL (RDS)
    ├─ GitHub API
    └─ OpenAI API
```

### 1.2 バックエンド：レイヤードアーキテクチャ

```
Controller       HTTPリクエスト受付・レスポンス返却
    │
Service          ビジネスロジック
    │
Repository       DB アクセス（Spring Data JPA）
    │
Entity           DB テーブルマッピング

DTO              Controller ↔ Service 間のデータ受け渡し
```

### 1.3 フロントエンド：Nuxt ディレクトリ構成

```
pages/           ルーティング対応ページ
components/      再利用可能 UI コンポーネント
composables/     状態管理・ロジック（useState / useFetch 等）
services/        API 呼び出し処理
middleware/      認証ガード等
assets/          静的ファイル
```

---

## 2. DB 設計

### 2.1 ER 図（概要）

```
users
  └─ learning_records (1:N)
  │    └─ learning_record_attachments (1:N)
  └─ tasks (1:N)
  └─ career_notes (1:1)
  └─ tech_skills (1:N)
  └─ learning_resources (1:N)
```

### 2.2 テーブル定義

#### users

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | ユーザー ID |
| email | VARCHAR(255) | UNIQUE, NOT NULL | メールアドレス |
| password_hash | VARCHAR(255) | NOT NULL | ハッシュ済みパスワード |
| display_name | VARCHAR(100) | NOT NULL | 表示名 |
| github_username | VARCHAR(100) | - | GitHub ユーザー名 |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

#### タグ設計方針（study_records用）

学習記録には「デフォルトタグ（例：API, Linux, Javaなど）」と「ユーザーが自由に作成できるタグ」の両方を付与できるハイブリッド方式を採用する。

- デフォルトタグはシステム側で用意し、全ユーザーが利用可能
- ユーザーは独自のタグを新規作成・編集・削除できる
- 学習記録には複数のタグを付与可能
- タグは検索や集計にも利用する

タグの詳細なAPI仕様・データモデルは docs/learning-records/api/ 配下を参照

#### learning_records

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | レコード ID |
| user_id | UUID | FK(users.id), NOT NULL | ユーザー ID |
| date | DATE | NOT NULL | 学習日 |
| content | TEXT | NOT NULL | 学習内容（最大 2000 文字） |
| duration | INTEGER | NOT NULL | 学習時間（分） |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |

#### tags

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | タグ ID |
| name | VARCHAR(50) | NOT NULL | タグ名 |
| type | VARCHAR(10) | NOT NULL | "default" or "user" |
| created_by | UUID | FK(users.id), nullable | 作成者 ID（user タグのみ） |

#### learning_record_tags（中間テーブル）

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| learning_record_id | UUID | FK(learning_records.id) | 学習記録 ID |
| tag_id | UUID | FK(tags.id) | タグ ID |

#### learning_record_attachments

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | 添付ファイル ID |
| learning_record_id | UUID | FK(learning_records.id), NOT NULL | 学習記録 ID |
| file_name | VARCHAR(255) | NOT NULL | 元のファイル名（ユーザーがアップロードした名前） |
| storage_key | VARCHAR(500) | NOT NULL | S3 上の保存パス（例: attachments/{userId}/{uuid}/file.pdf） |
| content_type | VARCHAR(100) | NOT NULL | ファイルの種類（例: image/png, application/pdf） |
| file_size | BIGINT | NOT NULL | ファイルサイズ（バイト） |
| created_at | TIMESTAMP | NOT NULL | アップロード日時 |

#### tasks

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | タスク ID |
| user_id | UUID | FK(users.id), NOT NULL | ユーザー ID |
| title | VARCHAR(255) | NOT NULL | タスク名 |
| content | TEXT | - | 詳細説明 |
| priority | VARCHAR(10) | NOT NULL | HIGH / MEDIUM / LOW |
| status | VARCHAR(20) | NOT NULL | TODO / IN_PROGRESS / DONE |
| due_date | DATE | - | 期限日 |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

#### career_notes

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | ID |
| user_id | UUID | FK(users.id), UNIQUE, NOT NULL | ユーザー ID（1:1） |
| goal | TEXT | - | 数年後の目標 |
| desired_skills | TEXT | - | やりたい技術 |
| retrospective | TEXT | - | 振り返り |
| work_experience | TEXT | - | 仕事でやったこと |
| strengths | TEXT | - | 強み |
| weaknesses | TEXT | - | 弱み |
| job_change_reason | TEXT | - | 転職理由 |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

#### tech_skills

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | ID |
| user_id | UUID | FK(users.id), NOT NULL | ユーザー ID |
| skill_name | VARCHAR(100) | NOT NULL | 技術名 |
| experience | VARCHAR(100) | NOT NULL | 経験年数・期間 |
| level | VARCHAR(20) | - | BEGINNER / INTERMEDIATE / ADVANCED |
| note | TEXT | - | 備考 |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

#### learning_resources

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| id | UUID | PK | ID |
| user_id | UUID | FK(users.id), NOT NULL | ユーザー ID |
| name | VARCHAR(100) | NOT NULL | サービス名 |
| url | VARCHAR(500) | - | URL |
| category | VARCHAR(50) | - | 動画 / 記事 / 資格 等 |
| memo | TEXT | - | 利用用途メモ |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

---

## 3. API 設計

### 3.1 共通仕様

| 項目 | 内容 |
|---|---|
| プロトコル | REST API |
| フォーマット | JSON |
| 認証 | Authorization: Bearer {JWT} |
| ベース URL | /api/v1 |
| エラーレスポンス | `{ "error": "メッセージ", "status": 400 }` |

### 3.2 認証 API

| メソッド | パス | 説明 | 認証不要 |
|---|---|---|---|
| POST | /api/v1/auth/signup | ユーザー登録 | ○ |
| POST | /api/v1/auth/login | ログイン・JWT 発行 | ○ |
| POST | /api/v1/auth/logout | ログアウト | - |

**POST /api/v1/auth/login リクエスト例：**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**レスポンス例：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600
}
```

### 3.3 ユーザー API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/users/me | ログインユーザー情報取得 |
| PUT | /api/v1/users/me | ユーザー情報更新 |

### 3.4 学習記録 API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/study-records | 学習記録一覧取得 |
| POST | /api/v1/study-records | 学習記録作成 |
| GET | /api/v1/study-records/{id} | 学習記録取得 |
| PUT | /api/v1/study-records/{id} | 学習記録更新 |
| DELETE | /api/v1/study-records/{id} | 学習記録削除 |

**クエリパラメータ：**

| パラメータ | 説明 |
|---|---|
| category | カテゴリ絞り込み |
| from | 開始日（YYYY-MM-DD） |
| to | 終了日（YYYY-MM-DD） |

### 3.5 タスク API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/tasks | タスク一覧取得 |
| POST | /api/v1/tasks | タスク作成 |
| GET | /api/v1/tasks/{id} | タスク取得 |
| PUT | /api/v1/tasks/{id} | タスク更新 |
| DELETE | /api/v1/tasks/{id} | タスク削除 |
| PATCH | /api/v1/tasks/{id}/status | ステータス更新 |

### 3.6 キャリアシート API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/career-notes | キャリア情報取得 |
| PUT | /api/v1/career-notes | キャリア情報更新（upsert） |

### 3.7 技術棚卸し API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/tech-skills | 技術一覧取得 |
| POST | /api/v1/tech-skills | 技術追加 |
| PUT | /api/v1/tech-skills/{id} | 技術更新 |
| DELETE | /api/v1/tech-skills/{id} | 技術削除 |

### 3.8 情報収集媒体 API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/learning-resources | 媒体一覧取得 |
| POST | /api/v1/learning-resources | 媒体追加 |
| PUT | /api/v1/learning-resources/{id} | 媒体更新 |
| DELETE | /api/v1/learning-resources/{id} | 媒体削除 |

### 3.9 ファイル添付 API

学習記録に紐づくファイルを管理する。

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/study-records/{id}/attachments | 添付ファイル一覧取得 |
| POST | /api/v1/study-records/{id}/attachments | ファイルアップロード |
| GET | /api/v1/study-records/{id}/attachments/{attachmentId}/download | ファイルダウンロード |
| DELETE | /api/v1/study-records/{id}/attachments/{attachmentId} | 添付ファイル削除 |

**POST /api/v1/study-records/{id}/attachments リクエスト：**

`Content-Type: multipart/form-data` でファイルを送信する。

**レスポンス例：**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "spring-boot-memo.pdf",
  "contentType": "application/pdf",
  "fileSize": 102400,
  "createdAt": "2026-06-06T10:00:00"
}
```

**GET /api/v1/study-records/{id}/attachments/{attachmentId}/download：**

ファイルのバイト列をレスポンスボディに含めて返す。  
`Content-Disposition: attachment; filename="spring-boot-memo.pdf"` ヘッダーを付与することで、ブラウザに「保存ダイアログ」を表示させる。

**バリデーション：**

| 項目 | ルール |
|---|---|
| ファイルサイズ | 1ファイルあたり 10MB 以下 |
| 添付数 | 学習記録1件あたり 10ファイル以下 |
| ファイル形式 | 制限なし（MIMEタイプを DB に記録） |

### 3.10 GitHub 連携 API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/v1/github/repos | Repository 一覧取得 |
| GET | /api/v1/github/commits | Commit 履歴取得 |
| GET | /api/v1/github/contributions | Contributions 取得 |
| GET | /api/v1/github/issues | Issue 一覧取得 |

### 3.11 AI API

| メソッド | パス | 説明 |
|---|---|---|
| POST | /api/v1/ai/suggest | 学習提案生成 |
| POST | /api/v1/ai/decompose | タスク分解 |

**POST /api/v1/ai/decompose リクエスト例：**
```json
{
  "taskTitle": "JWT ログインを実装したい"
}
```

**レスポンス例：**
```json
{
  "subtasks": [
    "JWT 発行 API の実装",
    "Spring Security の設定",
    "Login 画面の作成",
    "Token 保存処理の実装"
  ]
}
```

---

## 4. 認証設計

### 4.1 JWT 認証フロー

```
[フロント]          [バックエンド]
    │
    │── POST /auth/login ──▶│
    │                       │ パスワード検証
    │                       │ JWT 生成
    │◀── JWT トークン ───────│
    │
    │ localStorage に保存
    │
    │── GET /tasks ─────────▶│
    │  Authorization: Bearer  │ JWT 検証
    │                        │ ユーザー特定
    │◀── レスポンス ──────────│
```

### 4.2 JWT 設定

| 項目 | 設定値 |
|---|---|
| アルゴリズム | HS256 |
| 有効期限 | 1 時間（アクセストークン） |
| ペイロード | userId, email, issuedAt, expiresAt |
| シークレット | 環境変数で管理 |

### 4.3 Spring Security 設定方針

- `/api/v1/auth/**` は認証不要（permit all）
- それ以外の `/api/v1/**` は JWT 必須
- CORS 設定：フロントエンドオリジンのみ許可

---

## 5. 画面設計

### 5.1 画面一覧

| # | 画面名 | パス | 説明 |
|---|---|---|---|
| P01 | ログイン | /login | JWT ログイン |
| P02 | サインアップ | /signup | 新規登録 |
| P03 | ダッシュボード | / | 各情報サマリ |
| P04 | 学習記録一覧 | /study | 学習記録リスト |
| P05 | 学習記録詳細・編集 | /study/{id} | 詳細・編集 |
| P06 | タスク一覧 | /tasks | タスクリスト |
| P07 | キャリアシート | /career | キャリア情報 |
| P08 | 技術棚卸し | /skills | 技術スタック |
| P09 | 情報収集媒体 | /resources | 学習媒体管理 |
| P10 | GitHub 連携 | /github | GitHub 活動 |
| P11 | AI 提案 | /ai | AI 学習提案・タスク分解 |
| P12 | 設定 | /settings | ユーザー設定 |

### 5.2 共通レイアウト

```
┌─────────────────────────────────────┐
│ ヘッダー（アプリ名 / ユーザー情報）  │
├──────────┬──────────────────────────┤
│          │                          │
│ サイド   │  メインコンテンツ        │
│ ナビ     │                          │
│          │                          │
│ - ダッシュボード    │
│ - 学習記録         │
│ - タスク           │
│ - キャリア         │
│ - 技術棚卸し       │
│ - 媒体管理         │
│ - GitHub           │
│ - AI               │
│          │                          │
└──────────┴──────────────────────────┘
```

---

## 6. インフラ設計

### 6.1 AWS 構成

| サービス | 用途 | 備考 |
|---|---|---|
| EC2 | Spring Boot アプリ実行 | t2.micro（無料枠） |
| RDS | PostgreSQL | t2.micro（無料枠） |
| S3 | ファイル添付の保存先（画像・PDF 等） | ファイル添付機能で必須 |
| Security Group | アクセス制御 | 80/443/8080 のみ開放 |

### 6.2 環境一覧

| 環境 | 用途 | 構成 |
|---|---|---|
| ローカル | 開発 | Docker Compose |
| 本番 | 公開 | AWS EC2 + RDS |

### 6.3 Docker Compose 構成

```yaml
services:
  frontend:     # Nuxt (port 3000)
  backend:      # Spring Boot (port 8080)
  db:           # PostgreSQL (port 5432)
```

---

## 7. セキュリティ設計

| 観点 | 対策 |
|---|---|
| 認証 | JWT 認証（Spring Security） |
| パスワード | BCrypt ハッシュ化 |
| 入力検証 | Bean Validation（@Valid） |
| CORS | 許可オリジンのみ設定 |
| API 認可 | ログインユーザーのデータのみアクセス可能 |
| 秘密情報 | 環境変数（application.properties に直書き禁止） |
| HTTPS | 本番環境では HTTPS 化（AWS 証明書 or Let's Encrypt） |

---

## 8. 外部 API 連携設計

### 8.1 GitHub API

| 項目 | 内容 |
|---|---|
| 認証 | GitHub Personal Access Token |
| ライブラリ | RestTemplate / WebClient |
| レート制限 | 認証済み 5,000 req/h |
| 取得タイミング | ユーザー操作時（キャッシュ検討） |

主要エンドポイント：

| GitHub API | 用途 |
|---|---|
| GET /users/{username}/repos | Repository 一覧 |
| GET /repos/{owner}/{repo}/commits | Commit 履歴 |
| GET /users/{username}/events | イベント（Contributions） |
| GET /issues | Issue 一覧 |

### 8.2 OpenAI API

| 項目 | 内容 |
|---|---|
| モデル | gpt-4o-mini（コスト最適化） |
| 認証 | API Key（環境変数） |
| ライブラリ | RestTemplate / OkHttp |
| プロンプト設計 | システムプロンプトでコンテキスト付与 |

**学習提案プロンプト例：**
```
あなたはエンジニアのキャリア支援 AI です。
以下の情報をもとに、次に学ぶべき技術を3つ提案してください。

学習記録: {学習記録データ}
技術スタック: {技術棚卸しデータ}

JSON 形式で返答してください。
```

---

## 9. ディレクトリ構成

### 9.1 バックエンド（Spring Boot）

```
src/main/java/com/example/careersupport/
├── controller/
│   ├── AuthController.java
│   ├── StudyRecordController.java
│   ├── AttachmentController.java
│   ├── TaskController.java
│   ├── CareerNoteController.java
│   ├── TechSkillController.java
│   ├── LearningResourceController.java
│   ├── GitHubController.java
│   └── AiController.java
├── service/
│   ├── AuthService.java
│   ├── StudyRecordService.java
│   ├── AttachmentService.java
│   ├── StorageService.java
│   ├── TaskService.java
│   ├── CareerNoteService.java
│   ├── TechSkillService.java
│   ├── LearningResourceService.java
│   ├── GitHubService.java
│   └── AiService.java
├── repository/
│   ├── UserRepository.java
│   ├── StudyRecordRepository.java
│   ├── AttachmentRepository.java
│   ├── TaskRepository.java
│   ├── CareerNoteRepository.java
│   ├── TechSkillRepository.java
│   └── LearningResourceRepository.java
├── entity/
│   ├── User.java
│   ├── StudyRecord.java
│   ├── Attachment.java
│   ├── Task.java
│   ├── CareerNote.java
│   ├── TechSkill.java
│   └── LearningResource.java
├── dto/
│   ├── request/
│   └── response/
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── SecurityConfig.java
└── config/
    └── CorsConfig.java
```

### 9.2 フロントエンド（Nuxt）

```
pages/
├── index.vue          # ダッシュボード
├── login.vue
├── signup.vue
├── study/
│   ├── index.vue      # 一覧
│   └── [id].vue       # 詳細・編集
├── tasks/
│   └── index.vue
├── career.vue
├── skills.vue
├── resources.vue
├── github.vue
├── ai.vue
└── settings.vue
components/
├── common/
│   ├── AppHeader.vue
│   ├── AppSidebar.vue
│   └── AppSnackbar.vue
├── study/
├── tasks/
├── career/
└── ai/
composables/
├── useAuth.ts
├── useStudyRecords.ts
├── useTasks.ts
└── useAi.ts
services/
├── api.ts             # Axios インスタンス
├── studyService.ts
├── taskService.ts
└── aiService.ts
```

---

## 10. 実装フェーズ計画

| フェーズ | 内容 | 工数 |
|---|---|---|
| Phase 1 | 環境構築（Docker / DB / Spring Boot 起動確認） | 6h |
| Phase 2 | 認証機能（JWT ログイン / サインアップ） | 8h |
| Phase 3 | ユーザー管理 | 4h |
| Phase 4 | 学習記録 CRUD | 10h |
| Phase 5 | タスク管理 CRUD | 10h |
| Phase 6 | キャリアシート・技術棚卸し・媒体管理 | 14h |
| Phase 7 | ダッシュボード | 10h |
| Phase 8 | GitHub 連携 | 10h |
| Phase 9 | AI 機能（学習提案・タスク分解） | 20h |
| Phase 10 | AWS デプロイ | 10h |
| **合計** | | **約 102h** |
