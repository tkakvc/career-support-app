# フロントエンド設計書

---

## 実装済みAPI一覧

| メソッド | エンドポイント | 説明 |
|---|---|---|
| POST | /api/auth/signup | サインアップ |
| POST | /api/auth/login | ログイン |
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

---

## 画面一覧と使用するAPI

| パス | 画面名 | 使用するAPI |
|---|---|---|
| /login | ログイン | POST /api/auth/login |
| /signup | サインアップ | POST /api/auth/signup |
| /records | 学習記録一覧 | GET /api/learning-records, GET /api/tags |
| /records/new | 学習記録作成 | POST /api/learning-records, GET /api/tags |
| /records/[id] | 学習記録詳細・編集 | GET/PUT/DELETE /api/learning-records/{id}, GET /api/tags, GET/POST/DELETE /api/learning-records/{id}/attachments, GET .../download |
| /tags | タグ管理 | GET/POST/PUT/DELETE /api/tags |
| /ai | AI機能 | POST /api/ai/suggest, POST /api/ai/decompose |

---

## ルーティング構成

```
app/
  layout.tsx                 ← 全画面共通レイアウト（Providers）
  page.tsx                   ← / → /records にリダイレクト（ログイン済み）or /login にリダイレクト
  (auth)/
    layout.tsx               ← 認証ページ共通レイアウト（ログイン済みなら /records へ）
    login/
      page.tsx               ← ログインページ
    signup/
      page.tsx               ← サインアップページ
  (main)/
    layout.tsx               ← メインページ共通レイアウト（未認証なら /login へ）
    records/
      page.tsx               ← 学習記録一覧
      new/
        page.tsx             ← 学習記録作成
      [id]/
        page.tsx             ← 学習記録詳細・編集
    tags/
      page.tsx               ← タグ管理
    ai/
      page.tsx               ← AI機能
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
