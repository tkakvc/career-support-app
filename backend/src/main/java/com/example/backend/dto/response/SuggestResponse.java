package com.example.backend.dto.response;

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

    // static class にする理由：
    //   SuggestResponse の外から使う必要がないので、SuggestResponse の中に閉じ込める。
    //   static を付けることで SuggestResponse のインスタンスなしで使える内部クラスになる。
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        private String title;
        private String reason;
    }
}
