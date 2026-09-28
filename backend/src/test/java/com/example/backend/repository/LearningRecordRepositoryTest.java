package com.example.backend.repository;

import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// TagRepositoryTestと同じ方針：@DataJpaTestで実際にDB（H2）へデータを入れて、
// findTop30WithTagsByUserId()の@Query（JPQL文字列）が意図通りに動くかを確認する
@DataJpaTest
class LearningRecordRepositoryTest {

    @Autowired
    private LearningRecordRepository learningRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    private LearningRecord buildRecord(UUID userId, LocalDate date) {
        return LearningRecord.builder()
                .userId(userId)
                .date(date)
                .content("学習内容")
                .duration(30)
                .build();
    }

    // ── findTop30WithTagsByUserId ────────────────────────────────
    @Nested
    class FindTop30WithTagsByUserId {

        @Test
        void 対象ユーザーの記録だけを日付の新しい順で最大30件返す() {
            // given：対象ユーザーの記録を35件（LIMIT 30が本当に効いているか確認するため
            //         上限より多く用意する）と、別ユーザーの記録を1件用意する
            UUID targetUserId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            LocalDate base = LocalDate.of(2026, 1, 1);
            for (int i = 0; i < 35; i++) {
                // 日付を1日ずつずらして、後で「新しい順に並んでいるか」を確認できるようにする
                entityManager.persist(buildRecord(targetUserId, base.plusDays(i)));
            }
            entityManager.persist(buildRecord(otherUserId, base.plusDays(100)));
            entityManager.flush();

            // when
            List<LearningRecord> result = learningRecordRepository.findTop30WithTagsByUserId(targetUserId);

            // then
            assertThat(result).hasSize(30); // ①LIMIT 30が効いて、35件のうち30件だけ返る
            assertThat(result).allMatch(r -> r.getUserId().equals(targetUserId)); // ②他ユーザーの記録が混ざっていない
            // ③日付が新しい順（DESC）に並んでいる。一番新しいのはbase+34日目のはず
            assertThat(result.get(0).getDate()).isEqualTo(base.plusDays(34));
            assertThat(result.get(29).getDate()).isEqualTo(base.plusDays(5)); // 30件目は+5日目（34→5まで降順で30件）
        }

        @Test
        void 紐づくタグも一緒に取得できる() {
            // given：タグを2つ付けた記録を1件用意する
            UUID userId = UUID.randomUUID();
            Tag tag1 = entityManager.persistAndFlush(Tag.builder().name("Java").type("default").build());
            Tag tag2 = entityManager.persistAndFlush(Tag.builder().name("Spring").type("default").build());
            LearningRecord record = buildRecord(userId, LocalDate.of(2026, 1, 1));
            record.getTags().add(tag1);
            record.getTags().add(tag2);
            entityManager.persistAndFlush(record);
            // 1次キャッシュに乗った状態で読み直すと本当にDBを見ているのか怪しくなるので、
            // 一度キャッシュをクリアしてから検索する
            entityManager.clear();

            // when
            List<LearningRecord> result = learningRecordRepository.findTop30WithTagsByUserId(userId);

            // then：tagsフィールドは@ManyToMany(LAZY)なので、本来はgetTags()を呼んだ瞬間に
            //        追加でSQLが必要になる（N+1の原因）。@Queryの中でLEFT JOIN FETCHしている
            //        おかげで、ここで追加のSQLを発行せずにタグ名まで取得できているはず
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTags()).extracting(Tag::getName)
                    .containsExactlyInAnyOrder("Java", "Spring");
        }
    }
}
