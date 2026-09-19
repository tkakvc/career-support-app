package com.example.backend.controller;

import com.example.backend.config.SecurityConfig;
import com.example.backend.dto.response.PasswordUpdateResponse;
import com.example.backend.dto.response.UserProfileResponse;
import com.example.backend.entity.User;
import com.example.backend.security.JwtAuthenticationFilter;
import com.example.backend.security.JwtTokenProvider;
import com.example.backend.service.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ============================================================
// これまでの UserServiceTest 等は「Service層のロジック」だけを見るテストだった
// （UserServiceを直接new/Mockで呼ぶだけで、HTTPリクエストというものは一切登場しない）。
//
// このテストは「Controller層」を見る＝MockMvc（本物のHTTPサーバーを立てずに、
// HTTPリクエストが来た体でControllerを動かしてくれる道具）を使って実際にリクエストを投げ、
//   ① バリデーション（@Valid）が本当に400を返すか
//   ② JwtAuthenticationFilter が本当にトークン無しリクエストを弾くか
//   ③ GlobalExceptionHandler が Service の例外を正しいHTTPステータスに変換するか
// をまとめて確認する。この3つはService層のMockitoテストでは検証できない
// （@Validも認証フィルターも、Controllerに実際のHTTPリクエストが届いて初めて動く仕組みのため）。
//
// @WebMvcTest(UserController.class)：UserController「だけ」をMockMvc上で動かす。
// DBやRedisには繋がない。UserControllerが依存しているUserServiceは@MockitoBeanで
// 偽物に差し替え、その戻り値をテストごとに自由に設定して動作を確認する。
//
// @Import(SecurityConfig.class)：本物のSecurity設定（JWT必須・CORS等）をこのテストにも
// 適用する。SecurityConfig・JwtAuthenticationFilterを明示的にimportせず@WebMvcTestの
// 自動検出だけに任せると、実際に試した結果、本番と異なる挙動（未認証時に401になり、
// 有効なトークンを渡しても弾かれる＝JwtAuthenticationFilterが実質効いていない状態）に
// なった。理由の詳細までは追えていないが、明示的に@Importした方が本番のSecurityConfigと
// 一致した挙動になることを確認済みなのでこちらを採用する
// ============================================================
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // @MockitoBean：Spring全体のBean（DIコンテナに登録されるインスタンス）をMockに
    // 差し替えるアノテーション。UserServiceTestの@Mockと似ているが、@Mockは
    // 「自分でnewしたテスト用オブジェクトの中だけ」で使う偽物なのに対し、
    // @MockitoBeanは「Spring起動時のDIコンテナに登録される本物のBeanを偽物に差し替える」
    // という違いがある。MockMvc経由のHTTPリクエストはSpringのDIコンテナを通ってControllerに
    // 届くので、こちらの仕組みが必要になる
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private final UUID userId = UUID.randomUUID();

    // Authorizationヘッダーに付ける値。JwtTokenProvider自体はMockなので中身の文字列に意味は無い
    // （本物のJWT文字列である必要が無い。「dummy-token」という文字列が来たら有効/無効と
    //  Mockitoに教え込むだけなので、見た目がJWTっぽいかどうかは関係ない）
    private static final String DUMMY_TOKEN = "Bearer dummy-token";

    // 「有効なトークンが送られてきた」状態を作る共通処理。
    // JwtAuthenticationFilterの中身（本物）は validateToken() が true を返したときだけ
    // extractUserId() で取り出したuserIdをSecurityContextにセットする。その2つの呼び出しを
    // ここでまとめて仕込んでいる
    private void givenValidToken() {
        given(jwtTokenProvider.validateToken("dummy-token")).willReturn(true);
        given(jwtTokenProvider.extractUserId("dummy-token")).willReturn(userId);
    }

    // UserServiceが返すUserProfileResponseは中でUser（Entity）を受け取って変換するので、
    // テスト用のUserをここで組み立てる
    private User buildUser(String displayName) {
        return User.builder()
                .id(userId)
                .email("user@example.com")
                .displayName(displayName)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    // ── GET /api/users/me ────────────────────────────────────────
    @Nested
    class GetProfile {

        @Test
        void トークン無しなら403を返しUserServiceは呼ばれない() throws Exception {
            // when / then：Authorizationヘッダーを付けずにリクエストする。
            //   mockMvc.perform(get(...)) が「実際にこのURLへGETリクエストを送る」操作、
            //   .andExpect(...) がその結果への確認、という役割分担
            //
            // 注意：docs/auth/api/security.md や各error.mdは「JWT不正・未送信→401」と書いているが、
            // 実際に動かすとSpring Securityの既定動作により403が返る。SecurityConfigに
            // httpBasic()等のAuthenticationEntryPointを何も設定していないと、Spring Securityは
            // 未認証アクセスの通知方法（401＋WWW-Authenticateヘッダーを出す手段）を持たないため
            // 既定のHttp403ForbiddenEntryPointにフォールバックする。これはドキュメントと実装が
            // 食い違っている箇所で、このテストを書いて初めて判明した。
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isForbidden());

            // JwtAuthenticationFilterの時点で弾かれているはずなので、
            // その先のUserController・UserServiceには処理が届いていないことも確認する
            then(userService).should(never()).getProfile(any());
        }

        @Test
        void 有効なトークンならプロフィールを返す() throws Exception {
            // given：トークンは有効。userServiceにgetProfile(userId)を呼んだら
            //         テスト用のUserProfileResponseを返すよう仕込む
            givenValidToken();
            given(userService.getProfile(userId)).willReturn(new UserProfileResponse(buildUser("テストユーザー")));

            // when / then：Authorizationヘッダーを付けてリクエストする。
            //   jsonPath("$.email") は「レスポンスのJSONボディの中のemailというフィールド」を指す
            //   書き方（$がJSON全体のルートを表す）。.value(...)でその中身を確認する
            mockMvc.perform(get("/api/users/me").header("Authorization", DUMMY_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("user@example.com"))
                    .andExpect(jsonPath("$.displayName").value("テストユーザー"));
        }
    }

    // ── PATCH /api/users/me ──────────────────────────────────────
    @Nested
    class UpdateProfile {

        @Test
        void displayNameが空文字なら400を返す() throws Exception {
            // given：トークンは有効（このテストで見たいのはトークンの有無ではなく
            //         リクエストボディの中身なので、認証自体は通す）
            givenValidToken();

            // when / then：@NotBlankに違反する。
            //   .content("{...}")に直接JSON文字列を書いているのは、UpdateProfileRequestに
            //   コンストラクタや値を直接セットする手段が無い（Lombokの@Getterのみ）ため、
            //   Javaのオブジェクトとして組み立てる代わりに送信されるJSON文字列をそのまま書いている
            mockMvc.perform(patch("/api/users/me")
                            .header("Authorization", DUMMY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"\"}"))
                    .andExpect(status().isBadRequest());

            // バリデーションで弾かれた以上、UserServiceには処理が渡っていないはず
            then(userService).should(never()).updateProfile(any(), any());
        }

        @Test
        void displayNameが101文字なら400を返す() throws Exception {
            // given
            givenValidToken();
            // "あ"を101回繰り返した文字列＝100文字ちょうどの上限を1文字だけ超えるテストデータ
            String tooLong = "あ".repeat(101);

            // when / then：@Size(max = 100)に違反する
            mockMvc.perform(patch("/api/users/me")
                            .header("Authorization", DUMMY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"" + tooLong + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 正しいdisplayNameなら200で更新後の値を返す() throws Exception {
            // given：eq(userId)は「ちょうどこのuserIdが渡された時だけ」、any()は
            //         「UpdateProfileRequestはどんな中身でもいい（今回は見ていない）」という意味。
            //         引数を1つでもMockitoの matcher（eq/anyなど）で書いたら、
            //         同じ呼び出しの他の引数も全部matcherで揃える必要がある、というMockitoのルール
            givenValidToken();
            given(userService.updateProfile(eq(userId), any())).willReturn(new UserProfileResponse(buildUser("新しい名前")));

            // when / then
            mockMvc.perform(patch("/api/users/me")
                            .header("Authorization", DUMMY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"新しい名前\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("新しい名前"));
        }
    }

    // ── PUT /api/users/me/password ───────────────────────────────
    @Nested
    class UpdatePassword {

        @Test
        void currentPasswordが空なら400を返す() throws Exception {
            // given
            givenValidToken();

            // when / then
            mockMvc.perform(put("/api/users/me/password")
                            .header("Authorization", DUMMY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"\",\"newPassword\":\"newpass123\"}"))
                    .andExpect(status().isBadRequest());

            then(userService).should(never()).updatePassword(any(), any());
        }

        @Test
        void newPasswordが7文字なら400を返す() throws Exception {
            // given
            givenValidToken();

            // when / then：@Size(min = 8)に違反する（7文字なので1文字だけ足りない）
            mockMvc.perform(put("/api/users/me/password")
                            .header("Authorization", DUMMY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"oldpass\",\"newPassword\":\"1234567\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 現在パスワードが一致しない場合はServiceが投げた403がそのまま返る() throws Exception {
            // given：UserServiceの中身（パスワード照合のロジックそのもの）は
            //         Service層のUserServiceTestで既に検証済み。ここではその先、
            //         「Serviceが403を投げたら、Controller〜GlobalExceptionHandlerという
            //         Controller層の仕組みを通って、実際のHTTPレスポンスとして403になるか」
            //         という“繋ぎこみ”だけを確認する。willThrow(...)で、Serviceを呼んだら
            //         例外を投げるMockに仕込んでいる
            givenValidToken();
            given(userService.updatePassword(eq(userId), any()))
                    .willThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "現在のパスワードが正しくありません"));

            // when / then：ステータスコードだけでなく、GlobalExceptionHandlerが組み立てる
            //   JSONボディのmessageフィールドまで正しく伝わっているかも確認する
            mockMvc.perform(put("/api/users/me/password")
                            .header("Authorization", DUMMY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"newpass123\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("現在のパスワードが正しくありません"));
        }

        @Test
        void 正しいリクエストなら200を返す() throws Exception {
            // given
            givenValidToken();
            given(userService.updatePassword(eq(userId), any())).willReturn(new PasswordUpdateResponse());

            // when / then
            mockMvc.perform(put("/api/users/me/password")
                            .header("Authorization", DUMMY_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"oldpass\",\"newPassword\":\"newpass123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("updated"));
        }
    }
}
