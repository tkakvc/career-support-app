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

// OpenAI単体の知識に頼らず、実際のWeb検索結果を取得してから要約させる（RAG）。
// 検索APIはTavily（LLM向けに作られた検索API。要約しやすい本文抜粋=contentを直接返す）を使う。
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

    // HTTP自体の失敗（タイムアウト・5xx等）はretrieve()が例外を投げ、AiJobWorker側まで伝播して
    // ジョブをFAILEDにする（docs/ai/api/usecase.md参照）。HTTPは200だがレスポンスが空の場合は
    // エラーではなく「検索結果0件」として空リストを返す（generateResult側はジョブを失敗させない）。
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

    // JSONを「全体」と「その中の必要な項目」の2階層で受ける型に分けている。
    // SearchApiResponse＝レスポンス全体（resultsだけ拾う）、SearchResultItem＝results配列の1件
    // （title・url・contentだけ拾う）。ignoreUnknown=trueは各階層それぞれに必要。Tavily以外の
    // 検索APIに差し替える場合、この2つの型だけ作り直せばよくアプリ内部のSearchResultは変更不要。
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchApiResponse(List<SearchResultItem> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResultItem(String title, String url, String content) {
    }
}
