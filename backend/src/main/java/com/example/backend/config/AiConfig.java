package com.example.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ @Configuration クラスに @Bean を書くか
//   → ChatClient は1つのインスタンスをアプリ全体で使い回す（シングルトン）。
//     new で都度作ると設定の重複・無駄なコストが発生する。
//     @Bean として登録すれば Spring が管理し、依存するクラスに自動注入（DI）してくれる。
// ============================================================
//
// このクラスは「AI機能に必要なオブジェクトをSpringに登録する」設定クラス。
// 【2026-09-XX追記】旧・学習提案機能のCacheManager Bean（Caffeine、24時間キャッシュ）は、
// 参考資料生成への作り替えで廃止した。代わりにOpenAI呼び出し用HTTPクライアントの
// タイムアウトを設定するRestClientCustomizer Beanを追加した（下記参照）
//
// ============================================================
@Configuration
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

    // 【学習ポイント：押さえておく】connectTimeoutとreadTimeoutは別区間を測っている。
    // connectTimeout＝相手サーバーとのTCP接続が確立するまでの時間（相手が生きていれば速い→短くてよい）。
    // readTimeout＝接続後、リクエストを送ってレスポンスが返るまでの時間（OpenAIの生成待ちはここに乗る→長めに取る）。
    // 1つの値で「合計30秒」のように設定すると、繋がらない障害でも無駄に長く待つことになる。
    // 詳しくは memo/java/tcp-connect-timeout.md 参照。
    //
    // 【学習ポイント：使い方だけでよい】ClientHttpRequestFactoryBuilder.detect().build(settings) という
    // 呼び出し方自体はSpring Bootのバージョンで変わりうる実装の都合（Spring Boot 3.4で
    // ClientHttpRequestFactorySettings.toRequestFactory() が廃止され、Builder経由になった）。
    // 「RestClientCustomizerというBeanを1つ足せば、Spring AIが使うHTTPクライアントの設定を横から差し込める」
    // という考え方だけ覚えておけば十分で、正確な書き方は使うたびに公式ドキュメントを見ればよい。
    @Bean
    public RestClientCustomizer aiRestClientCustomizer() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(30));
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}
