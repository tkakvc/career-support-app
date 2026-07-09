package com.example.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ @Configuration クラスに @Bean を書くか
//   → ChatClient や CacheManager は1つのインスタンスをアプリ全体で使い回す（シングルトン）。
//     new で都度作ると設定の重複・無駄なコストが発生する。
//     @Bean として登録すれば Spring が管理し、依存するクラスに自動注入（DI）してくれる。
// 【AI任せでOK】@EnableCaching の書き方・CaffeineCacheManager の設定方法
// ============================================================
// ============================================================
// AiConfig とは
// ============================================================
//
// このクラスは「AI機能に必要なオブジェクトをSpringに登録する」設定クラス。
//
// @Configuration を付けると「このクラスはSpringの設定クラスだ」とSpringに伝えられる。
// @Bean を付けたメソッドの戻り値は、Springが管理するオブジェクト（Bean）として登録される。
// 登録されたBeanは、他のクラスで @Autowired や @RequiredArgsConstructor で自動注入できる。
//
// このクラスでは2つのBeanを登録している：
//   1. ChatClient  → OpenAI APIを呼び出すためのオブジェクト
//   2. CacheManager → キャッシュを管理するオブジェクト
//
// ============================================================

// @EnableCaching: アプリ全体でキャッシュ機能を有効にするアノテーション。
// これを付けないと @Cacheable アノテーションが動かない。
@Configuration
@EnableCaching
public class AiConfig {

    // ============================================================
    // ChatClient Bean
    // ============================================================
    //
    // 【ChatClient とは】
    //   Spring AI が提供する「OpenAI APIを呼び出すためのクライアント」。
    //   RestTemplate で手書きしていたヘッダー組み立て・URL指定・レスポンスパースを
    //   全部やってくれる。
    //
    // 【ChatClient.Builder とは】
    //   ChatClient を作るための「設計図」。
    //   Spring AI がこの Builder を自動で用意してくれるので、
    //   @Bean メソッドの引数に書くだけで Spring が注入してくれる。
    //   builder.build() を呼ぶと ChatClient が完成する。
    //
    // 【なぜ @Bean として登録するのか】
    //   AiService で ChatClient を使いたいとき、毎回 new で作るのではなく、
    //   Spring に管理してもらうことで1つのインスタンスを使い回せる。
    //
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    // ============================================================
    // CacheManager Bean（Caffeine キャッシュ）
    // ============================================================
    //
    // 【キャッシュとは】
    //   一度計算した結果を保存しておいて、同じリクエストが来たとき
    //   再計算せずに保存済みの結果を返す仕組み。
    //   → OpenAI APIを毎回呼ばずに済むので、コストと時間を節約できる。
    //
    // 【Caffeine とは】
    //   Java で最もよく使われるインメモリキャッシュライブラリ。
    //   「インメモリ」= サーバーのメモリ上に保存する（DBやRedisには保存しない）。
    //   アプリを再起動するとキャッシュは消える。
    //   ※ 覚える必要はない。「キャッシュの実装ライブラリ」くらいの認識でOK。
    //
    // 【expireAfterWrite(24, TimeUnit.HOURS) とは】
    //   「書き込みから24時間後にキャッシュを削除する」設定。
    //   TimeUnit.HOURS は「単位：時間」という意味。
    //   → 24時間以内に同じユーザーが叩いたら、OpenAIを呼ばずにキャッシュを返す。
    //
    // 【"suggestions" とは】
    //   キャッシュに付ける名前。
    //   AiService の @Cacheable(value = "suggestions") と名前が一致することで
    //   「学習提案のキャッシュはここで設定した条件で管理する」と紐付けられる。
    //
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("suggestions");
        // 学習提案の結果を24時間キャッシュする
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS));
        return manager;
    }
}
