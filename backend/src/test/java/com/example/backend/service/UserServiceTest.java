package com.example.backend.service;

import com.example.backend.dto.request.UpdatePasswordRequest;
import com.example.backend.dto.request.UpdateProfileRequest;
import com.example.backend.dto.response.PasswordUpdateResponse;
import com.example.backend.dto.response.UserProfileResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.RefreshTokenService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

// ============================================================
// TagServiceTest と同じ方針：@SpringBootTest（本物のDBやSpringを丸ごと起動する重いテスト）は
// 使わず、UserServiceが依存している3つ（UserRepository・BCryptPasswordEncoder・
// RefreshTokenService）を全部Mock（偽物）に差し替えて、UserServiceのロジックだけを検証する。
//
// @InjectMocks：上の3つの@Mockを、UserServiceのコンストラクタに自動で詰めてインスタンスを
// 作ってくれるアノテーション。RefreshTokenServiceTestで手動でnewしていたのは
// 「@Valueで注入される値（Mockに出来ないただのlong）」がコンストラクタ引数にあったから。
// UserServiceのコンストラクタ引数は全部Mock化できる型なので、ここでは@InjectMocksで足りる
// ============================================================
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    private final UUID userId = UUID.randomUUID();

    // 各テストで「DBに保存されている想定のユーザー」を作るための共通処理
    private User buildUser(String displayName, String passwordHash) {
        return User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash(passwordHash)
                .displayName(displayName)
                .build();
    }

    // ── getProfile ───────────────────────────────────────────────
    @Nested
    class GetProfile {

        @Test
        void ユーザー情報をUserProfileResponseに変換して返す() {
            // given：userRepository.findById(userId) を呼んだら、
            //         「表示名がテストユーザーのUser」が見つかったことにする
            given(userRepository.findById(userId)).willReturn(Optional.of(buildUser("テストユーザー", "hash")));

            // when：本物のgetProfile()を実行する
            UserProfileResponse result = userService.getProfile(userId);

            // then：DBから取ってきたUser（Entity）が、正しくUserProfileResponse（画面に返すDTO）に
            //        変換されているか。パスワードハッシュのような画面に出してはいけない情報が
            //        紛れ込んでいないかも、ここでフィールドを1つずつ見ることで確認できる
            assertThat(result.getEmail()).isEqualTo("user@example.com");
            assertThat(result.getDisplayName()).isEqualTo("テストユーザー");
        }

        @Test
        void 存在しないユーザーは404を返す() {
            // given：findById()が「見つからなかった」を表すOptional.empty()を返すようにする
            //         （本物のDBで、そのIDのユーザーが存在しない場合と同じ状況）
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when / then：assertThatThrownBy(...) は「この処理を実行すると例外が飛ぶこと」を
            //               確認するAssertJの書き方。.isInstanceOf(...)でその例外の種類を確認する
            assertThatThrownBy(() -> userService.getProfile(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── updateProfile ────────────────────────────────────────────
    @Nested
    class UpdateProfile {

        @Test
        void 表示名を更新して保存する() {
            // given
            User existing = buildUser("旧名前", "hash");
            given(userRepository.findById(userId)).willReturn(Optional.of(existing));
            // userRepository.save(...)を呼んだら、渡された引数（保存しようとしたUser）を
            // そのまま返すようにする。willAnswer(inv -> inv.getArgument(0))は
            // 「呼ばれた時の1番目の引数をそのまま戻り値にする」という意味
            // （本物のDBのsave()も、保存したエンティティをそのまま返す挙動をするため、それに合わせている）
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            // UpdateProfileRequestはリクエストのJSON（{"displayName": "新しい名前"}）を
            // 表すDTO。コンストラクタや値を直接セットする手段が無い（Lombokの@Getterのみ）ため、
            // mock(...)でDTOごと偽物にして、getDisplayName()が呼ばれたら固定値を返すよう仕込む
            UpdateProfileRequest request = mock(UpdateProfileRequest.class);
            given(request.getDisplayName()).willReturn("新しい名前");

            // when：表示名の更新を実行する
            UserProfileResponse result = userService.updateProfile(userId, request);

            // then：返ってきたレスポンスの表示名が更新後の値になっているか
            assertThat(result.getDisplayName()).isEqualTo("新しい名前");
            // 「このexisting（DBから取ってきたUser）そのものが、ちょうど1回save()に渡されたか」を
            // 確認する。これで「更新は正しく永続化される呼び出しになっている」と分かる
            then(userRepository).should(times(1)).save(existing);
        }
    }

    // ── updatePassword ───────────────────────────────────────────
    @Nested
    class UpdatePassword {

        @Test
        void 正しい現在パスワードと異なる新パスワードなら更新しトークンを全失効させる() {
            // given：DB上のパスワードハッシュは"hashed-old"だったことにする
            User existing = buildUser("テストユーザー", "hashed-old");
            given(userRepository.findById(userId)).willReturn(Optional.of(existing));
            // 「入力されたoldpassと、DBのhashed-oldは一致する（＝現在パスワードは正しい）」と仕込む
            given(passwordEncoder.matches("oldpass", "hashed-old")).willReturn(true);
            // 新パスワードnewpass123をハッシュ化すると"hashed-new"になる、と仕込む
            given(passwordEncoder.encode("newpass123")).willReturn("hashed-new");

            UpdatePasswordRequest request = mock(UpdatePasswordRequest.class);
            given(request.getCurrentPassword()).willReturn("oldpass");
            given(request.getNewPassword()).willReturn("newpass123");

            // when：パスワード変更を実行する
            PasswordUpdateResponse result = userService.updatePassword(userId, request);

            // then
            assertThat(result.getResult()).isEqualTo("updated"); // ①レスポンスが成功を表しているか
            assertThat(existing.getPasswordHash()).isEqualTo("hashed-new"); // ②Userのハッシュ値が実際に書き換わったか
            // ③他端末を締め出すため、このユーザーの全リフレッシュトークンが失効させられること
            //   （このメソッドの中身自体はRefreshTokenServiceTestの revokeAllForUser で
            //    検証済みなので、ここでは「正しいuserIdでちゃんと呼ばれたか」だけを確認すればよい）
            then(refreshTokenService).should(times(1)).revokeAllForUser(userId);
        }

        @Test
        void 現在のパスワードが一致しない場合は403を返しトークンは失効させない() {
            // given：入力されたwrongと、DBのhashed-oldは一致しない（＝現在パスワードが間違っている）
            User existing = buildUser("テストユーザー", "hashed-old");
            given(userRepository.findById(userId)).willReturn(Optional.of(existing));
            given(passwordEncoder.matches("wrong", "hashed-old")).willReturn(false);

            UpdatePasswordRequest request = mock(UpdatePasswordRequest.class);
            given(request.getCurrentPassword()).willReturn("wrong");
            // getNewPassword() は現在パスワードの照合より後にしか読まれないため、ここではスタブしない
            // （スタブしても使われず、Mockitoの厳格モードで UnnecessaryStubbingException になる。
            //  「使われないはずのgiven()を書いてしまった」ことをMockitoが検出して教えてくれる仕組み）

            // when / then：403(Forbidden)が返ることを確認する
            //   .satisfies(...) は、例外オブジェクトの中身（ここではHTTPステータスコード）を
            //   さらに細かく検証したいときに使う。ResponseStatusExceptionにキャストして
            //   getStatusCode().value()（実際の数値403）を取り出している
            assertThatThrownBy(() -> userService.updatePassword(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

            // 現在パスワードが間違っている以上、DBへの保存もトークンの失効も
            // 絶対に起きてはいけない。should(never())は「一度も呼ばれていないこと」を確認する
            then(userRepository).should(never()).save(any());
            then(refreshTokenService).should(never()).revokeAllForUser(any());
        }

        @Test
        void 新しいパスワードが現在のパスワードと同一なら400を返す() {
            // given：現在パスワードの照合自体はOK（samepassとhashed-oldが一致する）が、
            //         新パスワードも同じsamepassを指定してしまったケース
            User existing = buildUser("テストユーザー", "hashed-old");
            given(userRepository.findById(userId)).willReturn(Optional.of(existing));
            given(passwordEncoder.matches("samepass", "hashed-old")).willReturn(true);

            UpdatePasswordRequest request = mock(UpdatePasswordRequest.class);
            given(request.getCurrentPassword()).willReturn("samepass");
            given(request.getNewPassword()).willReturn("samepass");

            // when / then：400(Bad Request)が返ることを確認する
            assertThatThrownBy(() -> userService.updatePassword(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex ->
                            assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

            then(userRepository).should(never()).save(any());
            then(refreshTokenService).should(never()).revokeAllForUser(any());
        }

        @Test
        void 存在しないユーザーは404を返す() {
            // given：そもそもそのuserIdのユーザーがDBに居ない状況
            given(userRepository.findById(userId)).willReturn(Optional.empty());
            UpdatePasswordRequest request = mock(UpdatePasswordRequest.class);

            // when / then：現在パスワードの照合をする前に、まずユーザーの存在確認で弾かれる
            assertThatThrownBy(() -> userService.updatePassword(userId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
