-- GitHub連携は構想段階で終わり実装しなかったため、未使用のまま残っていたカラムを削除する。
ALTER TABLE users DROP COLUMN github_username;
