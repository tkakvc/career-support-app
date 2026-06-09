package com.example.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// OpenAI のレスポンスをパースするためのクラス。
// @Setter が必要な理由は SuggestResponse と同じ（Jackson がセッターを使って値をセットするため）。
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DecomposeResponse {

    // タスクのリスト。例）["Entityを作る", "Repositoryを作る", ...]
    private List<String> tasks;
}
