package com.example.backend.dto.request;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】DTO（Data Transfer Object）クラスのフィールド定義・@NotBlank / @Size などのアノテーション
//   → DTOは「リクエストのJSONをJavaオブジェクトに変換するだけ」のクラス。ロジックは書かない。
//   → バリデーションアノテーション（@NotBlank, @Size）の書き方はドキュメントを見ればわかる。
// ============================================================
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class DecomposeRequest {

    @NotBlank(message = "目標を入力してください")
    @Size(max = 200, message = "目標は200文字以内で入力してください")
    private String goal;
}
