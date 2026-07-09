package com.example.backend.service;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜインターフェースを定義するか（依存性逆転の原則）
//   → AttachmentService が StorageService インターフェースに依存することで、
//     具体的な実装（LocalStorageService / S3StorageService）を知らなくてよくなる。
//     設定ファイルの storage.type を変えるだけで実装を切り替えられる。
//     これを「依存性逆転の原則（Dependency Inversion Principle）」という。
// 【AI任せでOK】interface の書き方・throws IOException の宣言方法
// ============================================================
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {

    void upload(String storageKey, MultipartFile file) throws IOException;

    byte[] download(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
