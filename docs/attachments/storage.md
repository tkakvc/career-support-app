# ストレージ設計

## ストレージの選択

| 環境 | ストレージ | 理由 |
|---|---|---|
| ローカル開発 | ローカルディスク | S3 不要で即起動できる |
| 本番 | AWS S3 | 耐久性・スケーラビリティ |

Spring Boot の `StorageService` インターフェースを用意し、ローカル用・S3 用それぞれの実装クラスを切り替える。切り替えは `application.properties` の `storage.type=local` / `storage.type=s3` で行う。

---

## storageKey の命名規則

DB の `storage_key` カラムには、ストレージ上のパスを保存する。

```
attachments/{userId}/{attachmentId}/{fileName}
```

**例：**
```
attachments/550e8400-e29b-41d4-a716-446655440000/7c9e6679-7425-40de-944b-e07fc1f90ae7/spring-boot-memo.pdf
```

- `userId` を含めることで、ユーザーごとにフォルダが分かれる（管理しやすい）
- `attachmentId`（UUID）を含めることで、同名ファイルを重複なく保存できる
- `storageKey` は API レスポンスに含めない（内部情報）

---

## 処理フロー

### アップロード

```
1. Controller がリクエストからファイルを受け取る（MultipartFile）
2. Service がバリデーション（サイズ・添付数）
3. Attachment エンティティを DB に保存（この時点で attachmentId が確定）
4. storageKey を組み立てる（attachments/{userId}/{attachmentId}/{fileName}）
5. StorageService.upload(storageKey, file) でストレージに保存
6. storageKey を DB の attachment レコードに更新
7. レスポンスを返す
```

### ダウンロード

```
1. Controller が attachmentId を受け取る
2. Service が DB から Attachment を取得（所有者チェック含む）
3. StorageService.download(storageKey) でバイト列を取得
4. Content-Type・Content-Disposition ヘッダーを付与してレスポンスに返す
```

### 削除

```
1. Controller が attachmentId を受け取る
2. Service が DB から Attachment を取得（所有者チェック含む）
3. StorageService.delete(storageKey) でストレージから削除
4. DB の Attachment レコードを削除
```

ストレージ削除 → DB 削除の順で行う。DB を先に消すとストレージに孤立ファイルが残るリスクがある。

---

## StorageService インターフェース

```java
public interface StorageService {
    void upload(String storageKey, MultipartFile file) throws IOException;
    byte[] download(String storageKey) throws IOException;
    void delete(String storageKey) throws IOException;
}
```

実装クラス：

| クラス名 | 使用環境 |
|---|---|
| `LocalStorageService` | ローカル開発（`storage.type=local`） |
| `S3StorageService` | 本番（`storage.type=s3`） |

---

## ローカルストレージの保存先

```
{project-root}/uploads/attachments/{userId}/{attachmentId}/{fileName}
```

`uploads/` ディレクトリは `.gitignore` に追加する。
