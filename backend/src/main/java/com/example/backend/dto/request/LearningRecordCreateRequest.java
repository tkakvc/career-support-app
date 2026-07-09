package com.example.backend.dto.request;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】DTO クラスのフィールド定義と @NotNull / @NotBlank / @Min / @Max / @Size などの書き方
//   → DTO は「リクエストのJSONをJavaオブジェクトに変換するだけ」のクラス。ロジックは書かない。
// @Valid とは何か:
// @Valid は jakarta.validation というライブラリが提供するアノテーション。
// Spring は @Valid が付いた引数を見つけると、その引数の型（このDTO）に付いた
// @NotNull・@Size などのアノテーションを1つずつ自動でチェックする。
//
// 処理の流れ（LearningRecordController.java:52 を例にする）:
//   1. クライアントがJSON形式のリクエストをPOSTで送る
//   2. Spring がそのJSONを LearningRecordCreateRequest のインスタンスに変換する
//   3. 引数に @Valid が付いているので、Spring がこのDTOの date・content・duration・tagIds
//      それぞれに付いた @NotNull・@Size などのルールを順にチェックする
//   4a. 全部のルールに合格した場合 → Controller のメソッド本体が実行される
//   4b. 1つでもルール違反があった場合 → Controller のメソッド本体は実行されず、
//       Spring が自動で400エラー（MethodArgumentNotValidException）を返す
//
// 【面接で説明できるようにする】検証ルールをどこに書くか
//   → @Valid という1語自体は LearningRecordController.java:52 に書く（これは合っている）。
//     Controllerに書かないのは検証の「ルール」の方。「必須か」「何文字までか」を
//     Controllerにif文で書く代わりに、このDTOの @NotNull / @Size に書く。
//     LearningRecordController.java:52（作成用）と59行目（更新用）は同じDTOを受け取るので、
//     ルールをDTOに1回書けば、52行目・59行目どちらも @Valid を付けるだけで再利用できる。
// ============================================================
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class LearningRecordCreateRequest {

    @NotNull(message = "学習日は必須です")
    @PastOrPresent(message = "学習日は過去日または当日を指定してください")
    private LocalDate date;

    @NotBlank(message = "学習内容は必須です")
    @Size(max = 2000, message = "学習内容は2000文字以内で入力してください")
    private String content;

    @NotNull(message = "学習時間は必須です")
    @Min(value = 1, message = "学習時間は1分以上を指定してください")
    @Max(value = 1440, message = "学習時間は1440分以内を指定してください")
    private Integer duration;

    // タグは任意。null の場合はタグなしとして扱う。最大10件。
    @Size(max = 10, message = "タグは10件以内で指定してください")
    private List<UUID> tagIds;
}
