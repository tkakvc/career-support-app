package com.example.backend.repository;

import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// ============================================================
// LearningRecordSpecificationは「検索条件（Specification）を組み立てるだけのクラス」で、
// Mockitoでテストしようとしても意味が無い（組み立てたSQLの断片が正しいかはMockでは分からない）。
// なので実際にH2にデータを入れて、learningRecordRepository.findAll(spec, sort)を通して
// 「本当に意図通りの条件で絞り込めるか」を確認する
// ============================================================
@DataJpaTest
class LearningRecordSpecificationTest {

    @Autowired
    private LearningRecordRepository learningRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    // このRepositoryのfindAllは(Specification, Sort)の2引数版しか無い（@EntityGraphを
    // 付けるためにオーバーライドされている）ので、並び順を指定しないテストでも
    // Sort.unsorted()を渡す必要がある
    private List<LearningRecord> findAll(Specification<LearningRecord> spec) {
        return learningRecordRepository.findAll(spec, Sort.unsorted());
    }

    private LearningRecord buildRecord(UUID userId, LocalDate date, String content) {
        return LearningRecord.builder().userId(userId).date(date).content(content).duration(30).build();
    }

    // ── hasUserId ────────────────────────────────────────────────
    @Nested
    class HasUserId {

        @Test
        void 指定したuserIdの記録だけを返す() {
            // given
            UUID targetUserId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            entityManager.persistAndFlush(buildRecord(targetUserId, LocalDate.of(2026, 1, 1), "自分の記録"));
            entityManager.persistAndFlush(buildRecord(otherUserId, LocalDate.of(2026, 1, 1), "他人の記録"));

            // when
            List<LearningRecord> result = findAll(LearningRecordSpecification.hasUserId(targetUserId));

            // then
            assertThat(result).extracting(LearningRecord::getContent).containsExactly("自分の記録");
        }
    }

    // ── hasTagName ───────────────────────────────────────────────
    @Nested
    class HasTagName {

        @Test
        void 指定した名前のタグが付いた記録だけを返し重複しない() {
            // given：Javaタグの付いた記録（Spring タグも付けて、複数タグを持つ記録でも
            //         重複して返らないことを合わせて確認する）と、タグ無しの記録を用意する
            UUID userId = UUID.randomUUID();
            Tag javaTag = entityManager.persistAndFlush(Tag.builder().name("Java").type("default").build());
            Tag springTag = entityManager.persistAndFlush(Tag.builder().name("Spring").type("default").build());

            LearningRecord withBothTags = buildRecord(userId, LocalDate.of(2026, 1, 1), "Javaの記録");
            withBothTags.getTags().add(javaTag);
            withBothTags.getTags().add(springTag);
            entityManager.persistAndFlush(withBothTags);

            LearningRecord withoutJavaTag = buildRecord(userId, LocalDate.of(2026, 1, 2), "関係ない記録");
            entityManager.persistAndFlush(withoutJavaTag);

            // when
            List<LearningRecord> result = findAll(LearningRecordSpecification.hasTagName("Java"));

            // then：JOINしているため、タグを複数持つ記録が同じ行として重複して
            //        返ってきていないか（distinct(true)が効いているか）をhasSize(1)で確認する
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getContent()).isEqualTo("Javaの記録");
        }
    }

    // ── fromDate / toDate ────────────────────────────────────────
    @Nested
    class DateRange {

        @Test
        void from以上to以下の記録を境界値含めて返す() {
            // given：境界値ちょうどの記録が含まれるか（>=・<=か、>・<かの間違いがないか）を確認する
            UUID userId = UUID.randomUUID();
            LocalDate from = LocalDate.of(2026, 1, 10);
            LocalDate to = LocalDate.of(2026, 1, 20);
            entityManager.persistAndFlush(buildRecord(userId, from.minusDays(1), "範囲外(前日)"));
            entityManager.persistAndFlush(buildRecord(userId, from, "境界値(from当日)"));
            entityManager.persistAndFlush(buildRecord(userId, to, "境界値(to当日)"));
            entityManager.persistAndFlush(buildRecord(userId, to.plusDays(1), "範囲外(翌日)"));

            // when：fromDate と toDate を両方andで組み合わせる
            List<LearningRecord> result = findAll(
                    LearningRecordSpecification.fromDate(from).and(LearningRecordSpecification.toDate(to)));

            // then：境界値ちょうどの2件だけが含まれ、範囲外の2件は含まれない
            assertThat(result).extracting(LearningRecord::getContent)
                    .containsExactlyInAnyOrder("境界値(from当日)", "境界値(to当日)");
        }
    }

    // ── contentContains ──────────────────────────────────────────
    @Nested
    class ContentContains {

        @Test
        void キーワードを含む記録だけを部分一致で返す() {
            // given
            UUID userId = UUID.randomUUID();
            entityManager.persistAndFlush(buildRecord(userId, LocalDate.of(2026, 1, 1), "Spring Bootを学んだ"));
            entityManager.persistAndFlush(buildRecord(userId, LocalDate.of(2026, 1, 2), "Reactを学んだ"));

            // when
            List<LearningRecord> result = findAll(LearningRecordSpecification.contentContains("Spring"));

            // then
            assertThat(result).extracting(LearningRecord::getContent).containsExactly("Spring Bootを学んだ");
        }
    }

    // ── 複数条件の組み合わせ ────────────────────────────────────
    @Nested
    class CombineSpecifications {

        @Test
        void userIdとタグ名の両方の条件をandで組み合わせて絞り込める() {
            // given：同じJavaタグでも、userIdが違えば絞り込みから外れることを確認する
            UUID targetUserId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            Tag javaTag = entityManager.persistAndFlush(Tag.builder().name("Java").type("default").build());

            LearningRecord targetRecord = buildRecord(targetUserId, LocalDate.of(2026, 1, 1), "自分のJava記録");
            targetRecord.getTags().add(javaTag);
            entityManager.persistAndFlush(targetRecord);

            LearningRecord otherRecord = buildRecord(otherUserId, LocalDate.of(2026, 1, 1), "他人のJava記録");
            otherRecord.getTags().add(javaTag);
            entityManager.persistAndFlush(otherRecord);

            // when：「自分の記録」かつ「Javaタグ」の両方を満たすものだけを探す
            List<LearningRecord> result = findAll(
                    LearningRecordSpecification.hasUserId(targetUserId)
                            .and(LearningRecordSpecification.hasTagName("Java")));

            // then：同じJavaタグが付いていても、他人の記録は結果に含まれない
            assertThat(result).extracting(LearningRecord::getContent).containsExactly("自分のJava記録");
        }
    }
}
