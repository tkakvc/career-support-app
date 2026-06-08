package com.example.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// クエリパラメータをまとめて受け取るクラス
// Springがリクエストのクエリパラメータを自動でフィールドにマッピングしてくれる
@Getter
@Setter
public class LearningRecordSearchCriteria {
    private String tag;
    private LocalDate from;
    private LocalDate to;
    private String keyword;
}
