package com.example.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// @Setter が必要な理由：Jackson が JSON → Java オブジェクト変換時にセッターを使うため
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuggestResponse {

    private List<Suggestion> suggestions;

    // 学習記録が0件のときだけ使う。null のときは JSON キーごと省略する
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        private String title;
        private String reason;
    }
}
