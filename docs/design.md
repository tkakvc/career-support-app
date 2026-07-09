# 設計書

**プロジェクト名：** 学習記録管理アプリ  
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
[CloudFront]                    ← CDN・HTTPS 終端（証明書は ACM）
    │
    ▼
[ALB]                           ← リクエストをフロント/バックエンドへ振り分け
    │
    ├─▶ [Next.js (React + shadcn/ui)]  ← フロントエンド (ECS Fargate)
    │
    └─▶ [Spring Boot]                  ← バックエンド (ECS Fargate)
             │
             ├─ PostgreSQL (RDS)
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

### 1.3 フロントエンド：Next.js ディレクトリ構成

```
app/             App Router によるルーティング（ページ = page.tsx）
components/      再利用可能 UI コンポーネント
  ui/            shadcn/ui から生成したベースコンポーネント
  common/        AppHeader / AppSidebar 等
  records/       学習記録機能のコンポーネント
hooks/           カスタムフック（TanStack Query ラッパー、axios 呼び出しを内包）
lib/             axios インスタンス・Zod スキーマ・型定義・ユーティリティ
store/           Zustand ストア
middleware.ts    認証ガード（Next.js Middleware）
```

---

## 2. DB 設計

### 2.1 ER 図（概要）

```
users
  └─ learning_records (1:N)
       ├─ learning_record_attachments (1:N)
       └─ tags (M:N、中間テーブル: learning_record_tags)
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

#### タグ設計方針（learning_records用）

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

---

## 3. API 設計

### 3.1 共通仕様

| 項目 | 内容 |
|---|---|
| プロトコル | REST API |
| フォーマット | JSON |
| 認証 | Authorization: Bearer {JWT} |
| ベース URL | /api（`/api/v1`のようなバージョンプレフィックスは付けない。個人開発規模で複数バージョンを並行運用する予定がなく、必要になった時点で `/api/v2` を切る方が単純だと判断した） |
| エラーレスポンス | `{ "error": "メッセージ", "status": 400 }` |

### 3.2 認証 API

| メソッド | パス | 説明 | 認証不要 |
|---|---|---|---|
| POST | /api/auth/signup | ユーザー登録 | ○ |
| POST | /api/auth/login | ログイン・JWT 発行 | ○ |

ログアウトは専用エンドポイントを持たない。JWT はステートレス（サーバー側にトークンを保持しない）なので、フロントエンドが保存している JWT を破棄するだけでログアウトが完了する。Redis 等でトークンの無効化リストを持つ方式も検討したが、個人開発規模でその管理コストを持つ必要性が薄いため、シンプルなステートレス構成を採用した。

**POST /api/auth/login リクエスト例：**
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
| GET | /api/users/me | ログインユーザー情報取得 |
| PUT | /api/users/me | ユーザー情報更新 |

### 3.4 学習記録・タグ API

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/learning-records | 学習記録一覧取得（タグ・日付範囲・キーワードで絞り込み可） |
| POST | /api/learning-records | 学習記録作成 |
| GET | /api/learning-records/{id} | 学習記録取得 |
| PUT | /api/learning-records/{id} | 学習記録更新 |
| DELETE | /api/learning-records/{id} | 学習記録削除 |
| GET | /api/tags | タグ一覧取得 |
| POST | /api/tags | タグ作成 |
| PUT | /api/tags/{id} | タグ更新 |
| DELETE | /api/tags/{id} | タグ削除 |

**クエリパラメータ（GET /api/learning-records）：**

| パラメータ | 説明 |
|---|---|
| tag | タグ名で絞り込み |
| from | 開始日（YYYY-MM-DD） |
| to | 終了日（YYYY-MM-DD） |
| keyword | 学習内容の部分一致検索 |

### 3.5 ファイル添付 API

学習記録に紐づくファイルを管理する。

| メソッド | パス | 説明 |
|---|---|---|
| GET | /api/learning-records/{id}/attachments | 添付ファイル一覧取得 |
| POST | /api/learning-records/{id}/attachments | ファイルアップロード |
| GET | /api/learning-records/{id}/attachments/{attachmentId}/download | ファイルダウンロード |
| DELETE | /api/learning-records/{id}/attachments/{attachmentId} | 添付ファイル削除 |

**POST /api/learning-records/{id}/attachments リクエスト：**

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

**GET /api/learning-records/{id}/attachments/{attachmentId}/download：**

ファイルのバイト列をレスポンスボディに含めて返す。  
`Content-Disposition: attachment; filename="spring-boot-memo.pdf"` ヘッダーを付与することで、ブラウザに「保存ダイアログ」を表示させる。

**バリデーション：**

| 項目 | ルール |
|---|---|
| ファイルサイズ | 1ファイルあたり 10MB 以下 |
| 添付数 | 学習記録1件あたり 10ファイル以下 |
| ファイル形式 | 制限なし（MIMEタイプを DB に記録） |

### 3.6 AI API

| メソッド | パス | 説明 |
|---|---|---|
| POST | /api/ai/suggest | 学習提案生成 |

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
    │── GET /learning-records ▶│
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

- `/api/auth/**` は認証不要（permit all）
- それ以外の `/api/**` は JWT 必須
- CORS 設定：フロントエンドオリジンのみ許可

---

## 5. 画面設計

### 5.1 画面一覧

| # | 画面名 | パス | 説明 |
|---|---|---|---|
| P01 | ログイン | /login | JWT ログイン |
| P02 | サインアップ | /signup | 新規登録 |
| P03 | ダッシュボード | / | 各情報サマリ |
| P04 | 学習記録一覧 | /records | 学習記録リスト |
| P05 | 学習記録詳細・編集 | /records/{id} | 詳細・編集 |
| P06 | タグ管理 | /tags | タグ一覧・作成・編集・削除 |
| P07 | AI 提案 | /ai | AI 学習提案 |
| P08 | 設定 | /settings | ユーザー設定 |

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
│ - タグ管理         │
│ - AI               │
│          │                          │
└──────────┴──────────────────────────┘
```

---

## 6. インフラ設計

### 6.1 AWS 構成

| サービス | 用途 | 備考 |
|---|---|---|
| ECS Fargate | フロントエンド・バックエンドのコンテナ実行 | サーバーレスコンテナ（EC2インスタンスの管理不要） |
| ALB | リクエストの振り分け・HTTPS終端 | パスに応じてフロント/バックエンドのコンテナへルーティング |
| CloudFront | CDN・静的アセット配信 | エッジキャッシュにより表示を高速化 |
| ACM | HTTPS 証明書の発行・管理 | CloudFront / ALB に無料で証明書を紐付け |
| RDS | PostgreSQL | db.t3.micro（無料枠） |
| S3 | ファイル添付の保存先（画像・PDF 等） | ファイル添付機能で必須 |
| Security Group | アクセス制御 | ALB は 443 のみ、ECS タスクは ALB からの通信のみ許可 |

### 6.2 環境一覧

| 環境 | 用途 | 構成 |
|---|---|---|
| ローカル | 開発 | Docker Compose |
| 本番 | 公開 | ECS Fargate + RDS + ALB + CloudFront |

### 6.3 Docker Compose 構成

```yaml
services:
  frontend:     # Next.js (port 3000)
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

現状は OpenAI API のみを連携先とする。

### 8.1 OpenAI API

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

JSON 形式で返答してください。
```

---

## 9. ディレクトリ構成

レイヤードアーキテクチャ（1.2）・Next.js構成（1.3）の方針を、実際のディレクトリにどう対応させるかを示す。ファイル単位までは列挙せず、ディレクトリの役割のみ定義する。

### 9.1 バックエンド（Spring Boot）

パッケージルート：`com.example.backend`

| ディレクトリ | 役割 |
|---|---|
| controller/ | HTTPリクエストの受付・レスポンス返却 |
| service/ | ビジネスロジック |
| repository/ | DB アクセス（Spring Data JPA） |
| entity/ | DB テーブルマッピング |
| dto/request, dto/response/ | Controller ↔ Service 間のデータ受け渡し |
| security/ | JWT 発行・検証・認証フィルター |
| config/ | Spring Security・CORS・AI クライアント等の設定 |
| exception/ | 共通例外・エラーハンドリング |

### 9.2 フロントエンド（Next.js）

| ディレクトリ | 役割 |
|---|---|
| app/ | App Router によるルーティング・ページコンポーネント |
| components/ui/ | shadcn/ui から生成したベースコンポーネント |
| components/common/ | ヘッダー・サイドナビ等の共通コンポーネント |
| hooks/ | TanStack Query ラッパー（API 呼び出しを内包するカスタムフック） |
| lib/ | axios インスタンス・型定義・Zod スキーマ・ユーティリティ |
| store/ | Zustand ストア（ログイン状態管理） |
| middleware.ts | 未認証ユーザーを /login にリダイレクトする認証ガード |

