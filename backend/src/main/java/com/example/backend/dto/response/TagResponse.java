package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】レスポンス DTO のフィールド定義・Lombok の @Getter の書き方
//   → Entity（Tag）を受け取りフィールドをコピーするだけのシンプルな変換クラス
// ============================================================
import com.example.backend.entity.Tag;
import lombok.Getter;

import java.util.UUID;

@Getter
public class TagResponse {

    private final UUID id;
    private final String name;
    private final String type;
    private final UUID createdBy;

    public TagResponse(Tag tag) {
        this.id = tag.getId();
        this.name = tag.getName();
        this.type = tag.getType();
        this.createdBy = tag.getCreatedBy();
    }
}
