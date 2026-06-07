package com.example.backend.service;

import com.example.backend.dto.request.TagCreateRequest;
import com.example.backend.dto.request.TagUpdateRequest;
import com.example.backend.dto.response.DeleteResponse;
import com.example.backend.dto.response.TagResponse;
import com.example.backend.entity.Tag;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.TagRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 【テストの基本構造について】
//
// ① @SpringBootTest は「使わない」
//    @SpringBootTest を付けると Spring がすべての Bean を起動し DB 接続も行う。
//    Service のロジックだけをテストするなら Repository を「偽物（Mock）」で
//    差し替えれば十分で、DB は不要。@ExtendWith(MockitoExtension.class) のみで
//    軽量・高速に動く。
//
// ② @Mock ＝ 偽物の Repository
//    実際の SQL を一切発行しない。given() でテスト内から戻り値を自由に設定できる。
//
// ③ @InjectMocks ＝ テスト対象の「本物」
//    コンストラクタ引数に @Mock を自動注入して生成する。
//
// ④ given / when / then の3段階で書く（BDD スタイル）
//    given  : Mock の振る舞いを設定し、テストデータを用意する
//    when   : テスト対象のメソッドを1つだけ呼ぶ
//    then   : 戻り値・例外・Mock の呼び出し回数を検証する
//
// ⑤ @Nested でメソッドごとにテストをグループ化する
//    テスト結果の出力が「クラス名 > メソッド名」と階層表示されて読みやすくなる。
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    // ── 共通テストデータ ──────────────────────────────────────────
    // フィールドに固定しておくことでテスト間でIDが一致し、検証しやすくなる
    private final UUID userId = UUID.randomUUID();
    private final UUID tagId  = UUID.randomUUID();

    // Tag エンティティを組み立てるヘルパー
    // @Builder は DB の @GeneratedValue を使わないので、テスト内で id を自由にセットできる
    private Tag buildUserTag(UUID id, String name, UUID owner) {
        return Tag.builder().id(id).name(name).type("user").createdBy(owner).build();
    }

    private Tag buildDefaultTag(UUID id, String name) {
        return Tag.builder().id(id).name(name).type("default").createdBy(null).build();
    }

    // ── getVisibleTags ───────────────────────────────────────────
    @Nested
    class GetVisibleTags {

        @Test
        void リポジトリが返したタグをTagResponseに変換して返す() {
            // given
            List<Tag> tags = List.of(
                    buildDefaultTag(UUID.randomUUID(), "Java"),
                    buildUserTag(UUID.randomUUID(), "MyTag", userId)
            );
            given(tagRepository.findVisibleTags(userId)).willReturn(tags);

            // when
            List<TagResponse> result = tagService.getVisibleTags(userId);

            // then
            // assertThat(...).hasSize(n) でリストの件数を検証する
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Java");
            assertThat(result.get(1).getName()).isEqualTo("MyTag");
        }
    }

    // ── createTag ─────────────────────────────────────────────────
    @Nested
    class CreateTag {

        @Test
        void タグを正常に作成できる() {
            // given
            // TagCreateRequest はフィールドが private でセッターがないため
            // Mockito の mock() で生成し getName() の戻り値だけ設定する
            TagCreateRequest request = mock(TagCreateRequest.class);
            given(request.getName()).willReturn("NewTag");
            given(tagRepository.findByNameAndCreatedBy("NewTag", userId)).willReturn(Optional.empty());
            Tag saved = buildUserTag(tagId, "NewTag", userId);
            given(tagRepository.save(any(Tag.class))).willReturn(saved);

            // when
            TagResponse result = tagService.createTag(userId, request);

            // then
            assertThat(result.getName()).isEqualTo("NewTag");
            assertThat(result.getType()).isEqualTo("user");
            assertThat(result.getCreatedBy()).isEqualTo(userId);
            // save が正確に1回呼ばれたことを確認する
            // 「処理が実行されたこと」をコードで保証できるのが Mock 検証の利点
            then(tagRepository).should(times(1)).save(any(Tag.class));
        }

        @Test
        void 同名タグが既に存在する場合は409を返す() {
            // given
            TagCreateRequest request = mock(TagCreateRequest.class);
            given(request.getName()).willReturn("Dup");
            given(tagRepository.findByNameAndCreatedBy("Dup", userId))
                    .willReturn(Optional.of(buildUserTag(UUID.randomUUID(), "Dup", userId)));

            // when / then
            // assertThatThrownBy: ラムダを実行して例外が throw されることを検証する
            // isInstanceOf: 例外の型を確認する
            // satisfies: 例外オブジェクトを取り出してさらに詳しく検証する
            assertThatThrownBy(() -> tagService.createTag(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

            // 重複検出後に save が呼ばれていないことを確認（二重保存の防止）
            then(tagRepository).should(never()).save(any());
        }
    }

    // ── updateTag ─────────────────────────────────────────────────
    @Nested
    class UpdateTag {

        @Test
        void タグ名を正常に更新できる() {
            // given
            Tag existing = buildUserTag(tagId, "OldName", userId);
            given(tagRepository.findById(tagId)).willReturn(Optional.of(existing));
            given(tagRepository.findByNameAndCreatedBy("NewName", userId)).willReturn(Optional.empty());
            Tag saved = buildUserTag(tagId, "NewName", userId);
            given(tagRepository.save(any(Tag.class))).willReturn(saved);

            TagUpdateRequest request = mock(TagUpdateRequest.class);
            given(request.getName()).willReturn("NewName");

            // when
            TagResponse result = tagService.updateTag(userId, tagId, request);

            // then
            assertThat(result.getName()).isEqualTo("NewName");
        }

        @Test
        void 存在しないIDは404を返す() {
            // given
            given(tagRepository.findById(tagId)).willReturn(Optional.empty());
            TagUpdateRequest request = mock(TagUpdateRequest.class);

            // when / then
            // hasMessage: 例外メッセージを検証する
            assertThatThrownBy(() -> tagService.updateTag(userId, tagId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("タグが見つかりません");
        }

        @Test
        void defaultタグは403を返す() {
            // given
            given(tagRepository.findById(tagId))
                    .willReturn(Optional.of(buildDefaultTag(tagId, "Java")));
            TagUpdateRequest request = mock(TagUpdateRequest.class);

            // when / then
            assertThatThrownBy(() -> tagService.updateTag(userId, tagId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        }

        @Test
        void 他ユーザーのタグは403を返す() {
            // given
            UUID otherUserId = UUID.randomUUID();
            given(tagRepository.findById(tagId))
                    .willReturn(Optional.of(buildUserTag(tagId, "Tag", otherUserId)));
            TagUpdateRequest request = mock(TagUpdateRequest.class);

            // when / then
            assertThatThrownBy(() -> tagService.updateTag(userId, tagId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        }

        @Test
        void 別IDで同名タグが存在する場合は409を返す() {
            // given
            Tag existing = buildUserTag(tagId, "OldName", userId);
            given(tagRepository.findById(tagId)).willReturn(Optional.of(existing));
            // 別の ID を持つタグが同名で存在 → 409
            UUID conflictId = UUID.randomUUID();
            given(tagRepository.findByNameAndCreatedBy("Conflict", userId))
                    .willReturn(Optional.of(buildUserTag(conflictId, "Conflict", userId)));

            TagUpdateRequest request = mock(TagUpdateRequest.class);
            given(request.getName()).willReturn("Conflict");

            // when / then
            assertThatThrownBy(() -> tagService.updateTag(userId, tagId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
        }

        @Test
        void 同じ名前への更新は許可される() {
            // given
            // findByNameAndCreatedBy が自分自身のタグを返す（ID が同一）
            // Service の filter(t -> !t.getId().equals(id)) で除外されるため 409 にならない
            Tag existing = buildUserTag(tagId, "SameName", userId);
            given(tagRepository.findById(tagId)).willReturn(Optional.of(existing));
            given(tagRepository.findByNameAndCreatedBy("SameName", userId)).willReturn(Optional.of(existing));
            given(tagRepository.save(any(Tag.class))).willReturn(existing);

            TagUpdateRequest request = mock(TagUpdateRequest.class);
            given(request.getName()).willReturn("SameName");

            // when / then
            // assertThatCode: 例外が投げられないことを検証する（正常系の補完）
            assertThatCode(() -> tagService.updateTag(userId, tagId, request))
                    .doesNotThrowAnyException();
        }
    }

    // ── deleteTag ─────────────────────────────────────────────────
    @Nested
    class DeleteTag {

        @Test
        void タグを正常に削除できる() {
            // given
            Tag tag = buildUserTag(tagId, "MyTag", userId);
            given(tagRepository.findById(tagId)).willReturn(Optional.of(tag));

            // when
            DeleteResponse result = tagService.deleteTag(userId, tagId);

            // then
            assertThat(result.getResult()).isEqualTo("deleted");
            // delete(tag) がそのタグを引数にして1回呼ばれたことを確認
            then(tagRepository).should(times(1)).delete(tag);
        }

        @Test
        void 存在しないIDは404を返す() {
            // given
            given(tagRepository.findById(tagId)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> tagService.deleteTag(userId, tagId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("タグが見つかりません");

            then(tagRepository).should(never()).delete(any());
        }

        @Test
        void defaultタグは403を返す() {
            // given
            given(tagRepository.findById(tagId))
                    .willReturn(Optional.of(buildDefaultTag(tagId, "Java")));

            // when / then
            assertThatThrownBy(() -> tagService.deleteTag(userId, tagId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

            then(tagRepository).should(never()).delete(any());
        }

        @Test
        void 他ユーザーのタグは403を返す() {
            // given
            UUID otherUserId = UUID.randomUUID();
            given(tagRepository.findById(tagId))
                    .willReturn(Optional.of(buildUserTag(tagId, "Tag", otherUserId)));

            // when / then
            assertThatThrownBy(() -> tagService.deleteTag(userId, tagId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

            then(tagRepository).should(never()).delete(any());
        }
    }
}
