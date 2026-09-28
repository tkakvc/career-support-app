package com.example.backend.service;

import com.example.backend.dto.SearchResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

// 【学習ポイント：押さえておく】OpenAI単体の知識に頼らず、実際のWeb検索結果を取得してから
// 要約させる（RAG＝Retrieval-Augmented Generationの考え方）。検索APIはTavily
// （LLM向けに作られた検索API。要約しやすい本文抜粋=contentを直接返してくれる）を使う。
@Component
public class WebSearchClient {

    private final RestClient restClient;
    private final String apiKey;

    public WebSearchClient(@Value("${app.websearch.api-key}") String apiKey,
                            @Value("${app.websearch.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    // 【学習ポイント：押さえておく】ここでは2種類の異なる失敗を区別して扱っている。
    // ①HTTP自体が失敗した場合（タイムアウト・5xx等）：retrieve()が自動で例外を投げる。
    //   ここでtry/catchせず、そのままAiJobWorker側のcatch(Exception)まで伝播させてジョブをFAILEDにする
    //   （docs/ai/api/usecase.md「Web検索APIがエラーまたはタイムアウトになった → ジョブがFAILEDになる」）。
    // ②HTTP自体は200で成功したが、レスポンスの中身が空だった場合（下のnullチェック）：
    //   これはエラーではなく「検索結果が0件だった」として扱い、空リストを返す
    //   （generateResult側は空リストを「（検索結果なし）」として扱いOpenAIに渡すだけで、ジョブは失敗させない）。
    public List<SearchResult> search(String query, int maxResults) {
        SearchRequestBody body = new SearchRequestBody(apiKey, query, maxResults, "basic");
        SearchApiResponse response = restClient.post()
                .uri("/search")
                .body(body)
                .retrieve()
                .body(SearchApiResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .map(r -> new SearchResult(r.title(), r.url(), r.content()))
                .toList();
    }

    private record SearchRequestBody(
            @JsonProperty("api_key") String apiKey,
            String query,
            @JsonProperty("max_results") int maxResults,
            @JsonProperty("search_depth") String searchDepth) {
    }

    // 【学習ポイント：押さえておく】JSONを「全体」と「その中の必要な項目」の2階層で受ける型に分けている。
    // SearchApiResponse＝レスポンス全体（resultsだけ拾う。query・response_time等の他フィールドは無視）
    // SearchResultItem＝results配列の1件（title・url・contentだけ拾う。score・published_date等は無視）
    // ignoreUnknown=trueは各階層それぞれに必要（全体側の余分なキー／項目側の余分なキーは別物なので、
    // 片方に付けてももう片方には効かない）。Tavily以外の検索APIに差し替える場合は、
    // このSearchApiResponse/SearchResultItemだけを差し替え先API用に作り直せばよく、
    // アプリ内部で使うSearchResult（dto/SearchResult.java）やAiService側は変更不要になる。
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchApiResponse(List<SearchResultItem> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResultItem(String title, String url, String content) {
    }
}
