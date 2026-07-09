package com.example.backend.dto.response;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】レスポンス DTO のフィールド定義・@Getter / @Setter / @NoArgsConstructor の Lombok 構文
//   → @Setter が必要な理由は既存コメントに書いてある通り（Jackson がセッターを使って値をセットするため）
// ============================================================
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// OpenAI のレスポンスをパースするためのクラス。
// @Getter だけでなく @Setter も必要な理由：
//   Jackson が JSON → Java オブジェクトに変換するとき、デフォルトでセッターを使う。
//   @Getter だけだとセッターがなくてフィールドに値をセットできず、全フィールドが null になる。
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuggestResponse {

    private List<Suggestion> suggestions;

    // 学習記録が0件のときだけ使う。null のときは JSON キーごと省略する。
    // @JsonInclude(NON_NULL): null のフィールドをレスポンスJSONに含めない設定
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;

    // Suggestion を SuggestResponse の内部に書いている理由：
    //   Suggestion は SuggestResponse の中でしか使わないので、同じファイル内にまとめている。
    //
    // static を付ける理由：
    //   static なしの内部クラスは「外側のクラスのオブジェクトが先に存在しないと作れない」というJavaの仕様がある。
    //   つまり static なしだと、Suggestion を作るには先に SuggestResponse のオブジェクトが必要になる。
    //   Jackson は JSON をパースするとき Suggestion を単独で作ろうとするため、この制約があると失敗する。
    //   static を付けると「SuggestResponse のオブジェクトなしで Suggestion だけを作れる」ようになるので、
    //   Jackson が問題なく Suggestion を作れる。
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        private String title;
        private String reason;
    }
}
