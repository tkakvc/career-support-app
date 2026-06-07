package com.example.backend.entity;

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
    // GenerationType.UUID は Spring Boot 3.x / Hibernate 6 から標準サポート。追加ライブラリ不要。
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    // "default"（システム提供）または "user"（ユーザー作成）の区別
    @Column(nullable = false, length = 10)
    private String type;

    // type が "user" の場合のみ値が入る。作成者のユーザーID（users テーブルの id を参照）。
    // UUID 型にする理由: users.id も UUID のため型を統一する。
    // Long ではなく UUID（ラッパークラス）を使う理由: default タグは作成者が存在しないため null を許容する必要があるから。
    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;
}