package com.example.backend.service;

import com.example.backend.dto.response.DecomposeResponse;
import com.example.backend.dto.response.SuggestResponse;
import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import com.example.backend.repository.LearningRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final int MAX_REQUESTS_PER_DAY = 10;
    private static final String NO_RECORDS_MESSAGE = "学習記録がまだありません。記録を追加すると提案が表示されます。";

    private final ChatClient chatClient;
    private final LearningRecordRepository learningRecordRepository;
    private final ObjectMapper objectMapper;

    // 処理中ユーザーIDを管理（重複リクエスト防止）
    private final Set<UUID> processingUsers = ConcurrentHashMap.newKeySet();
    // レート制限：ユーザーIDごとのリクエスト日時リスト
    private final Map<UUID, List<Instant>> requestLog = new ConcurrentHashMap<>();

    @Cacheable(value = "suggestions", key = "#userId")
    @Transactional(readOnly = true)
    public SuggestResponse suggest(UUID userId) {
        List<LearningRecord> records = learningRecordRepository.findTop30WithTagsByUserId(userId);

        // 学習記録が0件なら OpenAI を呼ばずに固定メッセージを返す（コスト節約）
        if (records.isEmpty()) {
            return new SuggestResponse(List.of(), NO_RECORDS_MESSAGE);
        }

        checkDuplicate(userId);
        checkRateLimit(userId);

        try {
            String userPrompt = buildSuggestPrompt(records);

            // system と user を分離することでプロンプトインジェクションを防ぐ
            String raw = chatClient.prompt()
                    .system("あなたはエンジニアのキャリア支援AIです。必ず以下のJSON形式のみで返してください。説明文は不要です。" +
                            "{\"suggestions\":[{\"title\":\"技術名\",\"reason\":\"提案理由\"}]}")
                    .user(userPrompt)
                    .call()
                    .content();

            return parseJson(raw, SuggestResponse.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIサービスとの通信に失敗しました");
        } finally {
            processingUsers.remove(userId);
        }
    }

    public DecomposeResponse decompose(UUID userId, String goal) {
        checkDuplicate(userId);
        checkRateLimit(userId);

        try {
            String raw = chatClient.prompt()
                    .system("あなたはエンジニアのキャリア支援AIです。必ず以下のJSON形式のみで返してください。説明文は不要です。" +
                            "{\"tasks\":[\"タスク1\",\"タスク2\"]}")
                    .user("以下の目標を、エンジニアが実装できる具体的なタスクに実装順で分解してください。\n目標: " + goal)
                    .call()
                    .content();

            return parseJson(raw, DecomposeResponse.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIサービスとの通信に失敗しました");
        } finally {
            processingUsers.remove(userId);
        }
    }

    private void checkDuplicate(UUID userId) {
        if (!processingUsers.add(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "処理中です。しばらく待ってから再試行してください");
        }
    }

    private void checkRateLimit(UUID userId) {
        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Instant> timestamps = requestLog.computeIfAbsent(userId, k -> new ArrayList<>());
        synchronized (timestamps) {
            timestamps.removeIf(t -> t.isBefore(oneDayAgo));
            if (timestamps.size() >= MAX_REQUESTS_PER_DAY) {
                processingUsers.remove(userId);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "1日のリクエスト上限（10回）に達しました");
            }
            timestamps.add(Instant.now());
        }
    }

    private String buildSuggestPrompt(List<LearningRecord> records) {
        String recordsText = records.stream()
                .map(r -> {
                    String tags = r.getTags().stream()
                            .map(Tag::getName)
                            .collect(Collectors.joining(", "));
                    String tagPart = tags.isEmpty() ? "" : "（タグ: " + tags + "）";
                    return r.getDate() + ": " + r.getContent() + tagPart;
                })
                .collect(Collectors.joining("\n"));

        return "以下の学習記録をもとに、このエンジニアが次に学ぶべき技術を3つ提案してください。\n\n【直近の学習記録】\n" + recordsText;
    }

    // OpenAI がJSONの前後に余計なテキストを付ける場合があるため正規表現で抽出する
    private <T> T parseJson(String raw, Class<T> type) {
        Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(raw);
        if (matcher.find()) {
            try {
                return objectMapper.readValue(matcher.group(), type);
            } catch (JsonProcessingException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIのレスポンスが解析できませんでした");
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIのレスポンスが解析できませんでした");
    }
}
