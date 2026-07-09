package com.example.backend.dto.request;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】DTO クラスのフィールド定義・@NotBlank / @Size などのバリデーションアノテーションの書き方
//   → Lombok の @Getter / @NoArgsConstructor は覚えなくていい。
// ============================================================
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TagCreateRequest {

    @NotBlank(message = "タグ名は必須です")
    @Size(max = 50, message = "タグ名は50文字以内で入力してください")
    private String name;
}
