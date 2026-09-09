# フロントエンド設計書

このファイルは全体方針（API一覧・状態管理・フォルダ構成）を扱う。画面ごとの詳細設計は各機能の `screen/` に置く。

| 画面 | 詳細設計 |
|---|---|
| ダッシュボード（学習記録一覧・検索）／学習記録 詳細・編集／新規作成／ファイル添付 | [../learning-records/screen/overview.md](../learning-records/screen/overview.md) |
| タグ管理 | [../tags/screen/overview.md](../tags/screen/overview.md) |
| AI 提案 | [../ai/screen/overview.md](../ai/screen/overview.md) |
| 設定 | [../settings/api/overview.md](../settings/api/overview.md)（API設計） |

---

## 利用API一覧

| メソッド | エンドポイント | 説明 |
|---|---|---|
| POST | /api/auth/signup | サインアップ |
| POST | /api/auth/login | ログイン |
| POST | /api/auth/logout | ログアウト（リフレッシュトークン失効） |
| GET | /api/learning-records | 学習記録一覧（検索・フィルタ） |
| POST | /api/learning-records | 学習記録作成 |
| GET | /api/learning-records/{id} | 学習記録詳細 |
| PUT | /api/learning-records/{id} | 学習記録更新 |
| DELETE | /api/learning-records/{id} | 学習記録削除 |
| GET | /api/tags | タグ一覧 |
| POST | /api/tags | タグ作成 |
| PUT | /api/tags/{id} | タグ更新 |
| DELETE | /api/tags/{id} | タグ削除 |
| GET | /api/learning-records/{id}/attachments | 添付ファイル一覧 |
| POST | /api/learning-records/{id}/attachments | 添付ファイルアップロード |
| GET | /api/learning-records/{id}/attachments/{aid}/download | ファイルダウンロード |
| DELETE | /api/learning-records/{id}/attachments/{aid} | 添付ファイル削除 |
| POST | /api/ai/suggest | AI学習提案 |
| POST | /api/ai/decompose | AIタスク分解 |
| GET | /api/users/me | プロフィール取得 |
| PATCH | /api/users/me | 表示名の変更 |
| PUT | /api/users/me/password | パスワード変更 |

---

## 画面一覧と使用するAPI

| パス | 画面名 | 使用するAPI |
|---|---|---|
| /login | ログイン | POST /api/auth/login |
| /signup | サインアップ | POST /api/auth/signup |
| /dashboard | ダッシュボード（学習記録一覧・検索） | GET /api/learning-records, GET /api/tags |
| /records/new | 学習記録作成 | POST /api/learning-records, GET /api/tags |
| /records/[id] | 学習記録詳細・編集 | GET/PUT/DELETE /api/learning-records/{id}, GET /api/tags, GET/POST/DELETE /api/learning-records/{id}/attachments, GET .../download |
| /tags | タグ管理 | GET/POST/PUT/DELETE /api/tags |
| /ai | AI 提案 | POST /api/ai/suggest, POST /api/ai/decompose |
| /settings | 設定 | GET/PATCH /api/users/me, PUT /api/users/me/password |

---

## ルーティング構成

```
app/
  layout.tsx                 ← 全画面共通レイアウト（Providers）
  page.tsx                   ← / → ログイン済みなら /dashboard、未ログインなら /login にリダイレクト
  (auth)/
    layout.tsx               ← 認証ページ共通レイアウト（ログイン済みなら /dashboard へ）
    login/
      page.tsx               ← ログインページ
    signup/
      page.tsx               ← サインアップページ
  (main)/
    layout.tsx               ← メインページ共通レイアウト（未認証なら /login へ）・共通ヘッダーナビ
    dashboard/
      page.tsx               ← ダッシュボード（学習記録一覧・検索）
    records/
      new/
        page.tsx             ← 学習記録作成
      [id]/
        page.tsx             ← 学習記録詳細・編集
    tags/
      page.tsx               ← タグ管理
    ai/
      page.tsx               ← AI 提案
    settings/
      page.tsx               ← 設定
```

---

## 状態管理の方針

```
Zustand（グローバル状態）
  → ログイン情報（複数画面をまたいで使うため）

TanStack Query（サーバー状態）
  → APIから取得するデータ（学習記録・タグ・添付ファイル）

useState（ローカル状態）
  → その画面内だけで使う状態（モーダルの開閉など）
```

---

## フォルダ構成

```
frontend/
  app/                       ← ルーティング + ページコンポーネント
  components/
    ui/                      ← shadcn/ui が生成するコンポーネント
    features/                ← 機能ごとのコンポーネント
      records/               ← 学習記録関連
      tags/                  ← タグ関連
      ai/                    ← AI機能関連
  hooks/                     ← TanStack Query のカスタムフック
  store/                     ← Zustand の store
  lib/
    api.ts                   ← axios インスタンス
    types.ts                 ← TypeScript 型定義
    utils.ts                 ← shadcn/ui の cn ユーティリティ
```
