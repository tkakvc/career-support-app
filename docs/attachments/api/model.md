# データモデル定義

---

## 添付ファイル（Attachment）

| フィールド | 型 | 必須 | 制約 | 説明 |
|---|---|---|---|---|
| id | UUID | - | 自動採番 | 添付ファイルID |
| learningRecordId | UUID | ○ | URLパスから取得 | 紐づく学習記録のID |
| fileName | string | ○ | 最大255文字 | ユーザーがアップロードしたときの元のファイル名 |
| contentType | string | ○ | 自動判定 | ファイルの種類（例: `image/png`, `application/pdf`） |
| fileSize | number | ○ | 1以上 10485760（10MB）以下 | ファイルサイズ（バイト単位） |
| createdAt | string (ISO 8601) | - | 自動設定 | アップロード日時 |

---

## ER図

```
LearningRecord 1 --- N Attachment
```

1つの学習記録に対して、複数の添付ファイルを紐付けられる。

---

## 備考

- `contentType` は Spring Boot がアップロードされたファイルから自動的に判定してDBに保存する。フロントエンドから指定する必要はない。
- `fileSize` の単位はバイト。例えば 102400 は 100KB を表す。
- ファイルの実体（バイナリデータ）はDBには保存しない。DBには `storageKey`（保存先のパス）だけを持ち、実体はストレージに保存する。`storageKey` はAPIレスポンスには含めない（内部情報のため）。
