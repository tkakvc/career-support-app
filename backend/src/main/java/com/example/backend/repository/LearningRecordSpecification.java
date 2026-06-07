package com.example.backend.repository;

import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

// Specification パターン：検索条件をオブジェクトとして表現し、and() で自由に組み合わせられるようにする
// 条件が増えても if-else を増やさず、ここに1メソッド追加するだけで済む
public class LearningRecordSpecification {

    public static Specification<LearningRecord> hasUserId(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<LearningRecord> hasTagName(String tagName) {
        return (root, query, cb) -> {
            // タグ名で絞り込むため tags に INNER JOIN する
            // distinct(true) で JOIN による重複行を除去する
            query.distinct(true);
            Join<LearningRecord, Tag> tagJoin = root.join("tags", JoinType.INNER);
            return cb.equal(tagJoin.get("name"), tagName);
        };
    }

    public static Specification<LearningRecord> fromDate(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<LearningRecord> toDate(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to);
    }

    public static Specification<LearningRecord> contentContains(String keyword) {
        return (root, query, cb) -> cb.like(root.get("content"), "%" + keyword + "%");
    }
}
