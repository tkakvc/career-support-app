package com.example.backend.repository;

import com.example.backend.entity.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// ============================================================
// これまでのMockitoテストは「Repositoryを丸ごと偽物にする」やり方だった。
// このテストは逆に、Repositoryの中身（findVisibleTagsが書いているJPQL文字列）が
// 本当に正しいSQLとして動くかを見たいので、偽物には出来ない。
//
// @DataJpaTest：JPA関連のBean（Repository・EntityManager等）だけを起動し、
// テスト用の埋め込みDB（H2。build.gradleに既にテスト依存として入っている）に対して
// 実際にデータを入れて実際にクエリを実行する、軽量な統合テスト。
// 各テストメソッドの終わりに自動でロールバックされるので、テスト間でデータが混ざらない。
//
// TestEntityManager：テストデータをDBに保存するための道具。
// tagRepository.save(...)でも保存はできるが、TestEntityManagerの方が
// 「保存してすぐDBに反映させる（flush）」を明示的に書けるので、テストの準備コードで使う
// ============================================================
@DataJpaTest
class TagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Tag buildTag(String name, String type, UUID createdBy) {
        return Tag.builder().name(name).type(type).createdBy(createdBy).build();
    }

    // ── findVisibleTags ──────────────────────────────────────────
    @Nested
    class FindVisibleTags {

        @Test
        void defaultタグと自分のuserタグは見え他人のuserタグは見えない() {
            // given：3種類のタグをDBに用意する
            UUID myUserId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            // defaultタグ：createdByが無い（システム提供）＝誰から見ても見える想定
            Tag defaultTag = buildTag("Java", "default", null);
            // 自分が作ったuserタグ
            Tag myTag = buildTag("自主学習", "user", myUserId);
            // 他人が作ったuserタグ
            Tag otherTag = buildTag("他人の秘密タグ", "user", otherUserId);
            entityManager.persistAndFlush(defaultTag);
            entityManager.persistAndFlush(myTag);
            entityManager.persistAndFlush(otherTag);

            // when：自分（myUserId）から見えるタグ一覧を取得する
            List<Tag> result = tagRepository.findVisibleTags(myUserId);

            // then：defaultタグと自分のタグは含まれるが、他人のタグは含まれない
            //        （OR条件の書き間違いで「他人のタグまで見える」になっていないかを確認する）
            assertThat(result).extracting(Tag::getName)
                    .contains("Java", "自主学習")
                    .doesNotContain("他人の秘密タグ");
        }

        @Test
        void defaultタグ優先userタグは名前順で並ぶ() {
            // given：type・nameの並び順（ORDER BY t.type ASC, t.name ASC）が
            //         意図通りかを確認するため、わざと逆順で登録する
            UUID myUserId = UUID.randomUUID();
            entityManager.persistAndFlush(buildTag("Zタグ", "user", myUserId));
            entityManager.persistAndFlush(buildTag("Bタグ", "default", null));
            entityManager.persistAndFlush(buildTag("Aタグ", "default", null));

            // when
            List<Tag> result = tagRepository.findVisibleTags(myUserId);

            // then："default"は文字列として"user"より小さい（アルファベット順）ので先に来て、
            //        default同士はname昇順（Aタグ→Bタグ）、その後にuserタグ（Zタグ）が続く
            assertThat(result).extracting(Tag::getName)
                    .containsExactly("Aタグ", "Bタグ", "Zタグ");
        }
    }
}
