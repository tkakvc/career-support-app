package com.example.backend.dto;

// Web検索1件分の結果（WebSearchClientが返す内部表現）
public record SearchResult(String title, String url, String content) {
}
