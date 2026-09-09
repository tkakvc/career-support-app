# エンドポイント一覧・詳細

| メソッド | パス | 概要 |
|---|---|---|
| GET | /api/learning-records | 学習記録一覧取得（クエリ: tag, date, user などで絞込可）|
| POST | /api/learning-records | 学習記録新規作成 |
| GET | /api/learning-records/{id} | 学習記録詳細取得 |
| PUT | /api/learning-records/{id} | 学習記録更新 |
| DELETE | /api/learning-records/{id} | 学習記録削除 |
| GET | /api/tags | タグ一覧取得（デフォルト＋ユーザー作成）|
| POST | /api/tags | タグ新規作成（ユーザー作成）|
| PUT | /api/tags/{id} | タグ名編集（ユーザー作成タグのみ）|
| DELETE | /api/tags/{id} | タグ削除（ユーザー作成タグのみ）|

## クエリパラメータ例
- /api/learning-records?tag=API&from=2026-05-01&to=2026-05-31

## 詳細はrequest-response.mdを参照
