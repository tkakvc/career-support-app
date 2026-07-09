package com.example.backend.service;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ StorageService インターフェースを実装するか（インターフェースを使う理由）
//   → AttachmentService は StorageService 型のフィールドに依存している。
//     開発環境では LocalStorageService（ローカルのファイルシステム）を使い、
//     本番環境では S3StorageService（Amazon S3）を使いたいとする。
//     インターフェースを挟むことで AttachmentService を一切変更せずに実装を切り替えられる（依存性逆転の原則）。
//     @ConditionalOnProperty で設定ファイルの値によって自動で実装が切り替わる。
// 【AI任せでOK】@ConditionalOnProperty / @Value の書き方・Files.write() / Files.readAllBytes() の使い方
// ============================================================
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${storage.local.base-path:uploads}")
    private String basePath;

    @Override
    public void upload(String storageKey, MultipartFile file) throws IOException {
        Path path = Path.of(basePath, storageKey);
        Files.createDirectories(path.getParent());
        Files.write(path, file.getBytes());
    }

    @Override
    public byte[] download(String storageKey) throws IOException {
        Path path = Path.of(basePath, storageKey);
        if (!Files.exists(path)) {
            throw new IOException("ファイルが見つかりません: " + storageKey);
        }
        return Files.readAllBytes(path);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(Path.of(basePath, storageKey));
    }
}
