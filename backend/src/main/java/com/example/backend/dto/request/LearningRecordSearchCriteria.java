package com.example.backend.dto.request;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】クエリパラメータを受け取る DTO クラスのフィールド定義
//   → @Getter / @Setter の Lombok 構文は覚えなくていい
//   → Spring が GET リクエストのクエリパラメータをフィールド名で自動マッピングしてくれる
// ============================================================
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// このファイルは何のためのファイルか:
// このクラスは、学習記録の一覧検索(GET /learning-records)のURLに付くクエリパラメータを
// 1つのオブジェクトとしてまとめて受け取るためのDTO。
// Criteria は英語で「判断基準・条件」という意味の単語。検索条件をまとめたクラス、という命名。
//
// 具体例:
//   クライアントが GET /learning-records?tag=Java&from=2024-01-01&keyword=Spring というURLでリクエストする。
//   Spring がこのURLのクエリパラメータを読み取り、このクラスのフィールド名と同じ名前のものを
//   自動でマッピングして、以下のようなインスタンスを1つ作る。
//     criteria.tag     = "Java"
//     criteria.from    = 2024-01-01
//     criteria.to      = null（URLに to が無いのでnullのまま）
//     criteria.keyword = "Spring"
//
// このインスタンスは LearningRecordController.java:39 の getList() メソッドの引数として渡される。
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
