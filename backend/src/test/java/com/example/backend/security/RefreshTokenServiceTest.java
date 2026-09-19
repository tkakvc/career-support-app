package com.example.backend.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

// ============================================================
// なぜ @Mock が3つもあるのか（StringRedisTemplate・ValueOperations・SetOperations）
// ============================================================
//
// UserServiceTestはUserRepositoryを1個Mockにするだけで済んでいた。
//   userRepository.findById(...)  ← これは直接呼べるメソッド
//
// 一方、本物のRefreshTokenServiceはこう書かれている。
//   redisTemplate.opsForValue().set(キー, 値, TTL);
//   //              ↑まずここでメソッドを呼んで、その「戻り値」に対してさらに.set()を呼んでいる
//
// redisTemplate.opsForValue() は「値を1個だけ保存・取得するための道具（ValueOperations）」を
// 返すだけのメソッド。実際に保存するのはその道具が持つ .set()。
// もしredisTemplateだけをMockにすると、opsForValue()を呼んだときの戻り値が決まらず
// （Mockitoは何も教えなければnullを返す）、その後の.set()を呼ぶ前にNullPointerExceptionで落ちる。
// なので「opsForValue()を呼んだらvalueOperationsという別のMockを返す」と教える必要がある。
//   given(redisTemplate.opsForValue()).willReturn(valueOperations);
// opsForSet()（Setを扱う道具）も同じ理由でもう1つ別Mockが要る。
// ============================================================
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private final long refreshExpirationSeconds = 1209600; // 14日

    private RefreshTokenService refreshTokenService;

    private final UUID userId = UUID.randomUUID();

    // @InjectMocks（Mockitoが自動でコンストラクタに全Mockを詰めてインスタンスを作る機能）を
    // 使わず、自分でnewしている理由：
    // RefreshTokenServiceのコンストラクタ第2引数 refreshExpirationSeconds は
    // @Value("${app.jwt.refresh-expiration}") でapplication.yamlから注入される「ただのlong値」。
    // @InjectMocksはMockしか用意できないので、この値は渡せない。だから自分でnewする
    private RefreshTokenService newService() {
        return new RefreshTokenService(redisTemplate, refreshExpirationSeconds);
    }

    // ── issue ─────────────────────────────────────────────────────
    @Nested
    class Issue {

        @Test
        void トークンを発行しTOKEN_TO_USERとUSER_TO_TOKENSの両方にTTL付きで保存する() {
            // given：準備。「opsForValue()を呼んだらvalueOperationsを返せ」
            //         「opsForSet()を呼んだらsetOperationsを返せ」と教えた上で、
            //         その設定を反映させた状態のServiceを作る（newServiceはgiven()の後で呼ぶ）
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            refreshTokenService = newService();

            // when：実際にテストしたい処理を1回実行する
            String token = refreshTokenService.issue(userId);

            // then：実行した結果、何が起きたはずかを確認する
            assertThat(token).isNotBlank(); // ①ちゃんと空でない文字列が返ってきたか

            then(valueOperations).should(times(1)).set(   // ②valueOperationsの.set()が、
                    eq("refresh:" + token),                //    このキーで
                    eq(userId.toString()),                 //    この値を
                    eq(Duration.ofSeconds(refreshExpirationSeconds))); // このTTLで、ちょうど1回呼ばれたか

            String userTokensKey = "refresh:user:" + userId;
            then(setOperations).should(times(1)).add(userTokensKey, token); // ③逆引きSetにもトークンが追加されたか

            then(redisTemplate).should(times(1))
                    .expire(userTokensKey, Duration.ofSeconds(refreshExpirationSeconds)); // ④SetのTTLも延長されたか
        }
    }

    // ── findUserId ───────────────────────────────────────────────
    @Nested
    class FindUserId {

        @Test
        void 存在するトークンならuserIdを返す() {
            // given：valueOperations.get("refresh:token-abc") を呼んだら、
            //         「このユーザーのuserId（を文字列化したもの）」を返すよう仕込む。
            //         本物のfindUserId()は内部でこの.get()を呼んでいる
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:token-abc")).willReturn(userId.toString());
            refreshTokenService = newService();

            // when：token-abc というトークンでuserIdを引く
            Optional<UUID> result = refreshTokenService.findUserId("token-abc");

            // then：Optional<UUID>の中に、さっき仕込んだuserIdが入っているか
            //        （Optionalは「値があるかもしれないし無いかもしれない」を表す箱。
            //         .contains()はその箱の中身がこの値と一致するかを確認する）
            assertThat(result).contains(userId);
        }

        @Test
        void 存在しないトークンなら空を返す() {
            // given：失効済み・期限切れ・でたらめな文字列、いずれもRedisにキーが無い＝nullが返るので区別しない
            //         （Redisは「そのキーが無い」場合、getの戻り値がnullになる。
            //          「失効した」も「そもそも存在しない」もRedis視点では同じ「null」でしか表現できない）
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get("refresh:unknown-token")).willReturn(null);
            refreshTokenService = newService();

            // when
            Optional<UUID> result = refreshTokenService.findUserId("unknown-token");

            // then：中身が無い空のOptionalが返ってくる（＝呼び出し元は「無効なトークンだ」と判断できる）
            assertThat(result).isEmpty();
        }
    }

    // ── revoke ───────────────────────────────────────────────────
    @Nested
    class Revoke {

        @Test
        void 指定したトークンのTOKEN_TO_USERキーだけを削除する() {
            // given：この処理は redisTemplate.delete(...) を直接呼ぶだけで
            //         opsForValue()/opsForSet()を経由しないので、追加のgiven()は不要
            refreshTokenService = newService();

            // when：ログアウト等でこのトークンを無効化する
            refreshTokenService.revoke("token-abc");

            // then：「refresh:token-abc」というキーがRedisから削除されたか
            //        （then().should(times(1)).delete(...) は「.delete()がこの引数でちょうど1回
            //         呼ばれたか」を確認するMockitoの書き方。given()が「準備」なのに対し、
            //         then()は「実行後の確認」）
            //
            // 注意：逆引きSet（USER_TO_TOKENS）側はここでは触らない。
            //        revoke()は「このトークン1個だけを消す」処理で、
            //        「このユーザーの全トークンを消す」revokeAllForUser()とは役割が違う
            then(redisTemplate).should(times(1)).delete("refresh:token-abc");
        }
    }

    // ── revokeAllForUser ─────────────────────────────────────────
    @Nested
    class RevokeAllForUser {

        @Test
        void 逆引きSetにある全トークンとSet自体を削除する() {
            // given：このユーザーはスマホ・PCの2台からログイン中（＝2個のトークンがSetに入っている）
            //         という状況を作る。setOperations.members(...)は「そのSetの中身を全部取り出す」
            //         メソッドで、本物のrevokeAllForUser()はこれで「消すべきトークンの一覧」を得ている
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            String userTokensKey = "refresh:user:" + userId;
            given(setOperations.members(userTokensKey)).willReturn(Set.of("tokenA", "tokenB"));
            refreshTokenService = newService();

            // when：パスワード変更時などに「このユーザーの全端末をログアウトさせる」
            refreshTokenService.revokeAllForUser(userId);

            // then：Setに入っていた2個のトークンそれぞれのTOKEN_TO_USERキーが、
            //        個別に削除されたか（1回のdelete(Set全体)ではなく、1トークンずつdeleteしている
            //        実装なので、確認も1件ずつ書く）
            then(redisTemplate).should(times(1)).delete("refresh:tokenA");
            then(redisTemplate).should(times(1)).delete("refresh:tokenB");
            // 逆引きSet自体（refresh:user:{userId}というキー）も、最後に削除する
            then(redisTemplate).should(times(1)).delete(userTokensKey);
        }

        @Test
        void 対象トークンが無くても例外にならずSet自体の削除だけ行う() {
            // given：ログイン中の端末が無い＝Setがそもそも存在しない状態を再現する。
            //         members()は「該当キーが無い」場合にnullを返す（Redisの実際の仕様に合わせている）
            given(redisTemplate.opsForSet()).willReturn(setOperations);
            String userTokensKey = "refresh:user:" + userId;
            given(setOperations.members(userTokensKey)).willReturn(null);
            refreshTokenService = newService();

            // when / then：assertThatCode(...).doesNotThrowAnyException() は
            //               「この処理を実行しても例外が飛ばないこと」を確認するAssertJの書き方。
            //               本物のrevokeAllForUser()にnullチェック（tokens != null の分岐）が無いと、
            //               ここでNullPointerExceptionになってテストが落ちる
            assertThatCode(() -> refreshTokenService.revokeAllForUser(userId)).doesNotThrowAnyException();
            // Setの中身が無くても、Set自体の削除（空振りしても害はない）は必ず実行される
            then(redisTemplate).should(times(1)).delete(userTokensKey);
        }
    }
}
