package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】Java record の構文（public record クラス名(フィールド列挙)）は覚えなくていい
//   → record は Java 16 から使える「フィールド・コンストラクタ・getter を1行で定義できるクラス」
// 【面接で説明できるようにする】なぜダウンロードに専用クラスを作るか
//   → ダウンロード時は byte[]（ファイル中身）・contentType・fileName・fileSize を
//     一緒に Controller に返す必要があるが、これらをまとめて返せる既存クラスがない。
//     Service が複数の値をまとめて返すために record を使っている。
// ============================================================
public record AttachmentDownload(byte[] bytes, String contentType, String fileName, long fileSize) {
}
