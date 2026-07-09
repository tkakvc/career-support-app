package com.example.backend.entity;

// ============================================================
// 【このファイル全体の方針】
// 【AI任せでOK】@Entity / @Table / @Column などの JPA アノテーションの書き方
//   → Lombok の @Builder / @Getter / @Setter / @NoArgsConstructor / @AllArgsConstructor は覚えなくていい
// 【面接で説明できるようにする】なぜ主キーに UUID を使うか（連番 ID との違い）
//   → 連番（1, 2, 3...）を使うとURLに `/tags/1` のように表示され、
//     外部から「タグが何件あるか」「最初のタグはID=1だ」といった情報が推測できてしまう（情報漏洩）。
//     UUID はランダムな値なので推測困難。また分散環境でもID重複が起きない。
// ============================================================
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tags")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    // UUID を主キーに使う理由: 連番（1,2,3...）だと外部からユーザー数などが推測できてしまうため。
    //
    // GenerationType.UUID の仕組み:
    // strategy は @GeneratedValue アノテーションが持つ属性（設定項目）の名前で、
    // 42行目の `strategy = GenerationType.UUID` は「@GeneratedValue の strategy という属性に
    // GenerationType.UUID という値を渡す」という意味。
    // つまり strategy = GenerationType.UUID は「IDの値をどうやって生成するか」の方式を指定している。
    // IDENTITY（DBのAUTO_INCREMENT任せ）や SEQUENCE（DBの連番オブジェクトに問い合わせ）と違い、
    // UUID は DB に問い合わせず Hibernate が Java 側でランダムな128ビットの値を生成し、
    // INSERT を実行する前に id フィールドにセットする。
    //
    // 例:
    //   Tag tag = Tag.builder().name("Java").type("default").build();
    //   tag.getId();       // → null（まだ生成されていない）
    //   tagRepository.save(tag);
    //   tag.getId();       // → "3fa85f64-5717-4562-b3fc-2c963f66afa6" のような値
    //
    // DB側に保存する前からJavaのメモリ上でIDが確定するのが IDENTITY 方式との違い。
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    // type は「このタグを誰が作ったか」を表す文字列。値は "default" か "user" の2種類しか入らない。
    // - "default": アプリ側があらかじめ用意したタグ（例: "Java", "Spring"）。全ユーザー共通で使える。
    // - "user"   : ユーザーが自分で作ったタグ（例: "副業用メモ"）。そのユーザーだけが使える。
    @Column(nullable = false, length = 10)
    private String type;

    // createdBy は「そのタグを作ったユーザーのID」。type の値によって入る/入らないが変わる。
    // 具体的なデータの例（tags テーブルの行）:
    //
    //   | id         | name         | type      | created_by |
    //   |------------|--------------|-----------|------------|
    //   | (UUID値1)  | "Java"       | "default" | null       |  ← 誰も作っていないので created_by は null
    //   | (UUID値2)  | "副業用メモ" | "user"    | (UUID値3)  |  ← 作った本人の users.id が入る
    //
    // つまり「type が "user" の場合のみ値が入る」は、上の表の2行目のように
    // type="user" の行だけ created_by に users テーブルの id（UUID値3）が入り、
    // type="default" の行（1行目）は created_by が null のまま、という意味。
    //
    // UUID 型にする理由: users.id も UUID のため型を統一する。
    // Long ではなく UUID（ラッパークラス）を使う理由: default タグは作成者が存在しないため null を許容する必要があるから。
    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;
}