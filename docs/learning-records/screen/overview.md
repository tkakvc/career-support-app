# 学習記録画面 設計メモ

- 対応ユースケース：[docs/learning-records/api/usecase.md](../api/usecase.md) UC-01〜05、[docs/attachments/api/usecase.md](../../attachments/api/usecase.md) UC-01〜04
- 対象画面
  - ダッシュボード（学習記録の一覧・検索）：`/dashboard`
  - 学習記録 新規作成：`/records/new`
  - 学習記録 詳細・編集（ファイル添付を含む）：`/records/{id}`
- 一覧・検索は専用ページを設けず、ダッシュボードが担う

---

## ダッシュボード（学習記録一覧・検索）

- パス：`/dashboard`
- コンポーネント構成
  - ページ：`frontend/src/app/(main)/dashboard/page.tsx`
  - 部品：`frontend/src/components/features/dashboard/`（`SearchForm` / `RecordList` / `RecordCard`）
- 目的：ログインユーザー自身の学習記録を一覧表示し、キーワード・タグ・期間で絞り込めるようにする

### 画面構成

```
┌────────────────────────────────────────────┐
│ 学習記録                          [＋ 新規作成] │
├────────────────────────────────────────────┤
│ [キーワード] [タグ名] [開始日▾] [終了日▾] [検索][リセット] │
├────────────────────────────────────────────┤
│ ┌────────────────────────────────────────┐ │
│ │ 2026-05-27                    [API][Spring] │ │  ← RecordCard（クリックで詳細へ）
│ │ Spring Bootの基礎学習をした…                │ │
│ │                                     120分 │ │
│ └────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────┐ │
│ │ 2026-05-26                          [SQL] │ │
│ │ …                                         │ │
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

- ヘッダー右の「＋ 新規作成」は `/records/new` へのリンク

### 表示項目（`RecordCard`）

- 1件を1枚のカードで表示する。カード全体をクリック可能にし、押すと `/records/{id}` へクライアントサイド遷移する（`<a>` ではなく `useRouter().push()` を使い、フルリロードを避ける）
- 上段：学習日（`date`。`2026-05-27` の文字列をそのまま表示し、整形しない）／右側にタグを `Badge` で横並び（`tags` が空なら何も表示しない）
- 中段：内容（`content`）を最大2行で表示し、3行目以降は CSS の `line-clamp-2` で「…」省略する
- 下段：学習時間を右寄せで「120分」形式で表示する（`duration` は分単位の数値。[docs/learning-records/api/model.md](../api/model.md) 参照）

### 検索フォーム（`SearchForm`）

- 入力欄：キーワード／タグ名／開始日／終了日
- 入力値はこのコンポーネントの `useState` で保持する（画面を離れたら破棄してよい一時的な値なので、グローバル状態には入れない）
- 「検索」を押した時だけ、空でない項目だけを集めた検索条件オブジェクトを親（ダッシュボードページ）へ渡す
  - 例：入力が `{ keyword: "Java", tag: "", from: "", to: "" }` のとき、渡すのは `{ keyword: "Java" }`
  - 空文字の項目を送らない理由：`?keyword=&tag=` のような空クエリはバックエンドの解釈が実装依存になるため、「条件なし」を明示する
- 「リセット」：フォームを空に戻し、検索条件も `{}`（全件）に戻す
- 検索条件はダッシュボードページの `useState` が保持し、`useLearningRecords(criteria)` に渡す。`criteria` が変わると TanStack Query のキャッシュキーが変わり、自動で `GET /api/learning-records` を再取得する

### 検索条件とクエリパラメータの対応

| フォーム項目 | クエリ | 突き合わせ先 |
|---|---|---|
| キーワード | `keyword` | `content` の部分一致（[docs/learning-records/api/request-response.md](../api/request-response.md)） |
| タグ名 | `tag` | タグ名で絞り込み |
| 開始日 | `from` | 学習日 >= from（片方だけの指定も可） |
| 終了日 | `to` | 学習日 <= to |

### 状態パターン（`RecordList` が上から順に判定する）

- エラー（`isError`）：「データの取得に失敗しました。ページを再読み込みしてください」
- ローディング中（`isLoading`）：カードと同じ高さのスケルトンを3枚並べる
- 0件かつ検索条件なし：「まだ記録がありません」
- 0件かつ検索条件あり：「記録が見つかりませんでした」
- データあり：`RecordCard` を縦に並べる

### 使用API

| 操作 | API |
|---|---|
| 一覧・検索 | `GET /api/learning-records`（クエリ `keyword` / `tag` / `from` / `to`） |
| タグ候補（検索フォーム用） | `GET /api/tags` |

---

## 学習記録 新規作成

- パス：`/records/new`
- コンポーネント：`frontend/src/app/(main)/records/new/page.tsx`
- 目的：新しい学習記録を1件作成する

```
┌──────────────────────────────────────┐
│ ← 戻る                                 │
│ 学習記録を追加                          │
├──────────────────────────────────────┤
│ 日付        [____-__-__]               │
│ 内容        [                        ] │
│             [                        ] │
│ 学習時間(分) [   90   ]                │
│ タグ(任意)  □API  □Spring  □SQL       │
│                                        │
│                  [キャンセル] [保存する] │
└──────────────────────────────────────┘
```

- 入力項目：日付／内容／学習時間（分）／タグ（チェックボックス。`GET /api/tags` の全件を表示）
- バリデーションは詳細画面の編集モードと同一ルール（後述の表）
- 「保存する」：`POST /api/learning-records` → 成功で `/dashboard` へリダイレクトする。作成フックの `onSuccess` で `invalidateQueries({ queryKey: ["learning-records"] })` を呼び、一覧キャッシュを最新化してから遷移する
- 「キャンセル」：`/dashboard` へのリンク

### 使用API

| 操作 | API |
|---|---|
| 作成 | `POST /api/learning-records` |
| タグ候補 | `GET /api/tags` |

---

## 学習記録 詳細・編集

- パス：`/records/{id}`（`{id}` は URL の動的セグメント。`useParams().id` で取り出す）
- コンポーネント：`frontend/src/app/(main)/records/[id]/page.tsx`
- 目的：1件の学習記録を全文表示し、同じ画面内で編集・削除できるようにする。ファイル添付もこの画面で管理する

### モードの持ち方

- 「表示モード」と「編集モード」を1画面で切り替える。切り替えフラグ `isEditing` はこの画面内だけの `useState` で持つ
- 詳細データ（`useLearningRecord(id)`）は API から非同期で届くため、届いた時点で `useEffect` 内の `form.reset()` でフォームへ値を流し込む（`useForm` の `defaultValues` は初回レンダリング時に1回だけ効くため、後から届くデータには使えない）
- `duration` は数値（`90`）で届くが、フォームの入力欄は文字列で扱うため `String(90)` → `"90"` に変換して入れ、送信時に `parseInt` で数値へ戻す

### 表示モード

```
┌──────────────────────────────────────┐
│ ← 戻る                    [編集] [削除] │
├──────────────────────────────────────┤
│ 2026-05-27                             │
│ [API] [Spring]                         │
│                                        │
│ Spring Bootの基礎学習をした            │
│ （改行はそのまま表示：whitespace-pre-wrap） │
│                                        │
│ 120分                                  │
│                                        │
│ ── 添付ファイル ──────────────────     │
│  spring-boot-memo.pdf   100 KB  [DL][削除] │
│  [ファイルを選択]                       │
└──────────────────────────────────────┘
```

- 「戻る」：`/dashboard` へ
- 「編集」：`isEditing` を true にして編集モードへ（ページ遷移しない）
- 「削除」：確認ダイアログ（`Dialog`）を開き、「この操作は取り消せません。」を添える。実行すると `DELETE` → 成功で `/dashboard` へ遷移する
- 学習時間の下に「添付ファイルセクション」を置く（後述）

### 編集モード

- 入力項目：日付／内容／学習時間（分）／タグ（チェックボックス。`GET /api/tags` の全件を表示し、`record.tags` に含まれる id を初期チェックする）
- 「キャンセル」：`isEditing` を false に戻す（ページ遷移なし、エラー表示もクリア）
- 「保存する」：`PUT` → 成功で表示モードに戻る。送信中はボタンを「保存中...」にして disabled にし、二重送信を防ぐ

### 入力・バリデーション（新規作成と共通。[docs/learning-records/api/error.md](../api/error.md) と対応）

| 項目 | クライアント側チェック（Zod） | サーバーエラー→表示 |
|---|---|---|
| 日付 | 空なら「日付を入力してください」。API 側は過去日・当日のみ受け付ける | 400 → フォーム直下に汎用エラー |
| 内容 | 空／2000文字超で入力時にエラー | 400 |
| 学習時間 | 1〜1440 の整数以外を弾く（`parseInt` して `Number.isInteger` と範囲を判定） | 400 |
| タグ | 任意。未選択は空配列で送る | 存在しないタグID → 400 |
| 保存全般 | ― | 失敗時は「保存に失敗しました。しばらく経ってからお試しください」を保存ボタンの上に表示 |

### 状態パターン

- ローディング中：「読み込み中...」
- エラー or データなし：「データの取得に失敗しました。」＋「一覧に戻る」リンク
- 保存中・削除中：対象ボタンを disabled にし、ラベルを「保存中...」「削除中...」に変える

### 使用API

| 操作 | API |
|---|---|
| 詳細取得 | `GET /api/learning-records/{id}` |
| 更新 | `PUT /api/learning-records/{id}` |
| 削除 | `DELETE /api/learning-records/{id}` |
| タグ候補 | `GET /api/tags` |
| 添付 | `GET/POST/DELETE /api/learning-records/{id}/attachments`、`GET .../{attachmentId}/download` |

---

## 添付ファイルセクション（詳細画面に組み込み）

- 位置づけ：独立画面は設けず、詳細画面（`/records/{id}` 表示モード）の学習時間の下にセクションとして配置する
- バックエンドAPI：[docs/attachments/api/](../../attachments/api/)（`overview.md` / `endpoints.md` / `model.md` / `request-response.md` / `error.md`）、ストレージ設計は [docs/attachments/storage.md](../../attachments/storage.md)
- 対応ユースケース：[docs/attachments/api/usecase.md](../../attachments/api/usecase.md) UC-01〜04

### コンポーネント設計

- コンポーネント：`frontend/src/components/features/records/AttachmentSection.tsx`
- Props：`{ learningRecordId: string }`
- データ取得：`GET /api/learning-records/{id}/attachments` を TanStack Query で取得する。キャッシュキーは `["attachments", learningRecordId]`
- アップロード・削除の成功時に `invalidateQueries({ queryKey: ["attachments", learningRecordId] })` で一覧を最新化する

### 画面構成

```
── 添付ファイル ──────────────────────
 spring-boot-memo.pdf   100 KB   [DL] [削除]
 screenshot.png         200 KB   [DL] [削除]
────────────────────────────────────
 [ファイルを選択]  ※ 10MB以下・残り8ファイルまで
```

### 表示項目（一覧）

- ファイル名（`fileName`）
- サイズ（`fileSize` はバイト。`102400` → 「100 KB」のように整形して表示する）
- 種別アイコン（`contentType` から PDF／画像／その他を判定する。必須ではない）
- アップロード日時（`createdAt`。表示スペースが狭ければ省略可）

### 操作

- **アップロード**：`<input type="file">` で1ファイル選択 → `multipart/form-data` の `file` フィールドで `POST` する
  - `contentType` はサーバーが自動判定するため、フロントからは送らない
  - 送信中は選択ボタンを disabled にし「アップロード中...」を表示する
- **ダウンロード**：`GET /api/learning-records/{id}/attachments/{attachmentId}/download`
  - このエンドポイントは JWT 必須のため、単純な `<a href>` では認証ヘッダーを付けられずダウンロードできない
  - axios で `responseType: "blob"` で取得 → `URL.createObjectURL(blob)` で一時URLを作り、`<a download>` を JS で生成してクリック → `URL.revokeObjectURL` で解放、という手順を踏む
  - 保存ファイル名はサーバーの `Content-Disposition` に入っているが、blob 経由では自前で指定する必要があるため、一覧の `fileName` を使う
- **削除**：確認ダイアログを挟んで `DELETE` する。成功でストレージ実体・DBレコードの両方が削除される（[docs/attachments/storage.md](../../attachments/storage.md)）

### 入力・バリデーション（[docs/attachments/api/error.md](../../attachments/api/error.md) と対応）

| 条件 | クライアント側チェック | サーバーエラー→表示 |
|---|---|---|
| ファイル未選択 | アップロードボタンを disabled | 400 |
| 10MB 超 | 選択時にサイズを見て弾く（`file.size > 10 * 1024 * 1024`） | 400 →「ファイルサイズは10MBまでです」 |
| 添付が10件に達している | 「ファイルを選択」を非表示にし「上限（10件）に達しています」を表示 | 400 |
| ストレージ保存失敗 | ― | 500 →「アップロードに失敗しました。時間をおいて再試行してください」 |

### 状態パターン

- ローディング中：セクション内にスケルトンを1〜2行
- 0件：「添付ファイルはありません」
- アップロード中・削除中：対象の操作ボタンを disabled にする

### 使用API

| 操作 | API |
|---|---|
| 一覧 | `GET /api/learning-records/{id}/attachments` |
| アップロード | `POST /api/learning-records/{id}/attachments`（`multipart/form-data`、フィールド名 `file`） |
| ダウンロード | `GET /api/learning-records/{id}/attachments/{attachmentId}/download`（blob 取得） |
| 削除 | `DELETE /api/learning-records/{id}/attachments/{attachmentId}` |

---

## 未決定・要検討

- 一覧の規模が大きくなった場合、ダッシュボードは「最近の記録5件＋AI提案サマリ」に絞り、全件表示・検索を専用ページ `/records` へ分離する
- 検索フォームのタグ入力を、フリーテキストから `GET /api/tags` を使ったセレクトに変えるか
- 添付のダウンロードを、将来 S3 の署名付きURL（一定時間だけ有効なダウンロード用URL）に切り替えるか。切り替えるとフロントは blob 処理をやめ、URL を直接開くだけで済む
- 画像添付のサムネイル表示は今回のスコープ外
