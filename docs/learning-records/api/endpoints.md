# エンドポイント一覧・詳細（学習記録）

タグのエンドポイントは [../../tags/api/endpoints.md](../../tags/api/endpoints.md) を参照。

| メソッド | パス | 概要 |
|---|---|---|
| GET | /api/learning-records | 学習記録一覧取得（クエリ: tag, date, user などで絞込可）|
| POST | /api/learning-records | 学習記録新規作成 |
| GET | /api/learning-records/{id} | 学習記録詳細取得 |
| PUT | /api/learning-records/{id} | 学習記録更新 |
| DELETE | /api/learning-records/{id} | 学習記録削除 |

## クエリパラメータ例
- /api/learning-records?tag=API&from=2026-05-01&to=2026-05-31

## 詳細は request-response.md を参照
