package com.example.backend.service;

// ============================================================
// 【このファイル全体の方針】
// 【面接で説明できるようにする】なぜ Controller には処理を書かず Service に書くか（レイヤードアーキテクチャ）
//   →「OpenAI に送るプロンプトの組み立て」「レート制限チェック」「JSONのパース」は
//     ビジネスロジックであり、Controller の責務（HTTP の入出力）ではない。
//     Service に書くことで Controller はシンプルに保ち、Service 単体でテストできる。
// 【2026-09-XX追記】旧・学習提案機能の@Cacheable（Caffeine、24時間キャッシュ）は、参考資料生成への
// 作り替えで廃止した（interest次第で結果が変わるためキャッシュ自体が成立しない）。生成自体は
// SQS経由の非同期ジョブ（AiJobWorker）に移した
// 【AI任せでOK】@Transactional の書き方・ConcurrentHashMap のスレッドセーフな書き方
// 【AI任せでOK】正規表現（Pattern.compile）の書き方・objectMapper.readValue の使い方
// ============================================================
import com.example.backend.dto.AiJobMessage;
import com.example.backend.dto.SearchResult;
import com.example.backend.dto.response.AiJobAcceptedResponse;
import com.example.backend.dto.response.AiJobResponse;
import com.example.backend.dto.response.AiReferenceResponse;
import com.example.backend.entity.AiJob;
import com.example.backend.entity.AiJobStatus;
import com.example.backend.entity.AiJobType;
import com.example.backend.entity.AiReference;
import com.example.backend.entity.AiReferenceLink;
import com.example.backend.entity.LearningRecord;
import com.example.backend.entity.Tag;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.AiJobRepository;
import com.example.backend.repository.AiReferenceRepository;
import com.example.backend.repository.LearningRecordRepository;
import com.example.backend.repository.TagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AiService {

    // ============================================================
    // 定数
    // ============================================================
    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    // 【学習ポイント：押さえておく】この文字列は実際のSQSキュー名（infra/sqs.tfのaws_sqs_queue.ai_jobs.name）
    // と一致させる必要がある。Spring Cloud AWSはキュー名を指定すると、実行時にSQSのGetQueueUrl APIで
    // URLを自動的に解決してくれる（ARN/URLを自分でapplication.yamlに書く必要は無い）。
    private static final String QUEUE_NAME = "career-support-ai-jobs";

    private static final int MAX_REQUESTS_PER_DAY = 10;
    private static final int SEARCH_MAX_RESULTS = 5;
    private static final int SEARCH_QUERY_SEED_LENGTH = 100;
    private static final String NO_RECORDS_MESSAGE = "学習記録がまだありません。記録を追加すると参考資料が生成できるようになります。";
    private static final String MODERATION_REJECT_MESSAGE = "不適切な内容が含まれています";
    // Spring AI 1.0.0-M6のOpenAiModerationModel自動設定は、application.yamlの
    // spring.ai.openai.moderation.options.modelをdefaultOptionsに反映しないバグがあり、
    // 指定しないとライブラリ内蔵の固定デフォルト（text-moderation-latest、OpenAI側で廃止済み）が
    // 使われ400エラーになる（実際に動かして確認した）。呼び出し側でOpenAiModerationOptionsを
    // 明示的に渡すことで回避する
    private static final String MODERATION_MODEL = "omni-moderation-latest";
    private static final String AI_FAILURE_MESSAGE = "現在AIサービスが利用できません。しばらく経ってから再度お試しください";

    private static final int MAX_RETRY = 1;
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);

    private static final String REFERENCE_SYSTEM_PROMPT =
            "あなたはエンジニアの技術学習を支援するAIです。必ず以下のJSON形式のみで返してください。説明文は不要です。" +
                    "{\"summaryMarkdown\":\"Markdown形式の要約本文（5分程度で読める分量）\",\"tagName\":\"分類先のタグ名\"}";

    // 代表的なプロンプトインジェクション文言を弾く簡易フィルタ（docs/ai/guardrail-design.md の7）
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "ignore previous", "ignore all previous", "これまでの指示を無視",
            "以前の指示を無視", "システムプロンプト", "system prompt"
    );

    // ============================================================
    // フィールド
    // ============================================================

    private final ChatClient chatClient;
    private final ModerationModel moderationModel;
    private final SqsTemplate sqsTemplate;
    private final LearningRecordRepository learningRecordRepository;
    private final AiJobRepository aiJobRepository;
    private final AiReferenceRepository aiReferenceRepository;
    private final TagRepository tagRepository;
    private final TagService tagService;
    private final WebSearchClient webSearchClient;
    private final MarkdownHtmlConverter markdownHtmlConverter;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ジョブに保存する入力データ。Web検索・OpenAI呼び出しは非同期側（generateResult）で行うため、
    // enqueue時点ではまだ検索していない。検索・プロンプト組み立てに必要な材料だけをここに詰めて保存する。
    // focusRecordText：学習記録の詳細画面から「この記録について参考資料を作る」で生成した場合のみ値が入る。
    // その記録の内容を最優先の材料として扱う（詳細は buildReferencePrompt 参照）
    // searchSeedText：検索クエリの主材料にする実際の学習内容（本文）。focusRecordがあればそのcontent、
    // 無ければ直近の学習記録のうち最新の1件のcontent。タグ名（例："AWS"）だけを主材料にすると、
    // 「未経験からインフラエンジニアを目指す」「フロントエンド中心だが軽く押さえたい」「資格を取りたい」
    // のように文脈が全然違うケースを区別できないため、本文をメインにする。ただしタグ自体は
    // 参考にする材料から除外するわけではなく、本文を補足する扱いでtagNamesとして残す
    private record ReferenceJobInput(String interest, String recordsText, String focusRecordText,
                                      String searchSeedText, List<String> tagNames) {
    }

    // OpenAIの生の出力の受け皿（内部でのみ使う。公開APIのレスポンス形はAiReferenceResponse）
    private record LlmOutput(String summaryMarkdown, String tagName) {
    }

    // 【学習ポイント：押さえておく】ガードレール（重複チェック・レート制限・入力フィルタ・Moderation）は
    // すべてキューに積む前に同期的に行う。理由は、これらは400/429で即座にエラーを返すべき性質のもので、
    // 非同期のジョブとして扱うとエラー確認のためにわざわざポーリングさせることになり、
    // ユーザー体験がかえって悪くなるため。Web検索・OpenAI呼び出しは、失敗してもジョブをFAILEDにする
    // だけでよい性質（ユーザーがもう一度ボタンを押せばよい）なので、こちらは非同期側（generateResult）で行う。
    // recordIdは、学習記録の詳細画面から「この記録について参考資料を作る」を押した場合だけ値が入る
    // （/ai画面からの生成ではnull）。他人の記録IDを推測して覗き見できないよう、存在しない場合と
    // 他人の記録である場合を区別せず同じ404にする（他のuserId所有チェックと同じ考え方）
    public AiJobAcceptedResponse enqueueReference(UUID userId, String interest, UUID recordId) {
        LearningRecord focusRecord = null;
        if (recordId != null) {
            focusRecord = learningRecordRepository.findById(recordId)
                    .filter(r -> r.getUserId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("学習記録が見つかりません"));
        }

        List<LearningRecord> records = learningRecordRepository.findTop30WithTagsByUserId(userId);

        // 学習記録が0件、かつinterestも未入力、かつ特定の記録も指定されていなければ、
        // Web検索・OpenAIを呼ばずに固定メッセージを返す（ジョブも作らない）
        if (records.isEmpty() && (interest == null || interest.isBlank()) && focusRecord == null) {
            return AiJobAcceptedResponse.immediateMessage(NO_RECORDS_MESSAGE);
        }

        // 重複リクエストチェック（同じユーザーが処理中に連打した場合に 429 を返す）
        checkDuplicate(userId);

        // レート制限チェック（1日10回を超えたら 429 を返す）
        checkRateLimit(userId);

        if (interest != null && !interest.isBlank()) {
            validateGoalContent(interest);
        }

        String recordsText = buildRecordsText(records);

        // 特定の記録から生成する場合、その記録の内容は検索クエリ・プロンプトの両方で最優先の材料にする
        String focusRecordText = focusRecord == null ? null : formatRecord(focusRecord);

        // 検索クエリの主役はinterest。本文（content）はそれを補足する材料で、interestが
        // 未入力のときだけ本文が主役の代わりを務める（buildSearchQuery参照）。
        // focusRecordがあればその本文、無ければ直近の学習記録のうち最新の1件（records.get(0)）の本文
        String searchSeedText = focusRecord != null
                ? focusRecord.getContent()
                : (records.isEmpty() ? null : records.get(0).getContent());

        // タグも同じく補足材料（除外はしない）。focusRecordがあればそのタグを優先し、
        // 直近の学習記録から集めたタグと合わせて重複除去する
        List<String> tagNames = extractTagNames(records);
        if (focusRecord != null) {
            List<String> focusTagNames = focusRecord.getTags().stream().map(Tag::getName).toList();
            tagNames = Stream.concat(focusTagNames.stream(), tagNames.stream()).distinct().limit(10).toList();
        }

        String moderationTarget = recordsText
                + (interest == null ? "" : "\n" + interest)
                + (focusRecordText == null ? "" : "\n" + focusRecordText);
        checkModeration(moderationTarget);

        String input = toJson(new ReferenceJobInput(interest, recordsText, focusRecordText, searchSeedText, tagNames));
        AiJob job = aiJobRepository.save(AiJob.pending(userId, AiJobType.REFERENCE, input));
        sqsTemplate.send(QUEUE_NAME, new AiJobMessage(job.getId()));
        return AiJobAcceptedResponse.accepted(job.getId());
    }

    public AiJobResponse getJob(UUID userId, UUID jobId) {
        AiJob job = aiJobRepository.findById(jobId)
                .filter(j -> j.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return switch (job.getStatus()) {
            case PENDING, PROCESSING -> AiJobResponse.pending(job.getStatus());
            case DONE -> AiJobResponse.done(parseResult(job));
            case FAILED -> AiJobResponse.failed(job.getErrorMessage());
        };
    }

    // AiJobWorker から呼ばれる。ガードレール（interest・学習記録内容のチェック）はenqueue側で済んでいるので、
    // ここではWeb検索→OpenAI呼び出し→パース→出力バリデーション→保存だけを行う。戻り値はDBに保存するJSON文字列
    String generateResult(AiJob job) {
        ReferenceJobInput input = readJson(job.getInput(), ReferenceJobInput.class);

        String searchQuery = buildSearchQuery(input);
        List<SearchResult> searchResults = webSearchClient.search(searchQuery, SEARCH_MAX_RESULTS);

        String visibleTagNames = tagRepository.findVisibleTags(job.getUserId()).stream()
                .map(Tag::getName)
                .distinct()
                .collect(Collectors.joining(", "));

        String userPrompt = buildReferencePrompt(input, searchResults, visibleTagNames);

        // system と user を分離することでプロンプトインジェクションを防ぐ
        String raw = callOpenAi(job.getUserId(), "reference", () -> chatClient.prompt()
                .system(REFERENCE_SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content());

        LlmOutput llmOutput = parseJson(raw, LlmOutput.class);
        validateLlmOutput(llmOutput);

        String summaryHtml = markdownHtmlConverter.toHtml(llmOutput.summaryMarkdown());
        // 既存タグに一致した場合だけtagをセットする。一致しない場合はここでタグを作らず、
        // 提案名だけをsuggestedTagNameに残す（実際の作成はユーザー操作を起点に行う。TagService参照）
        Optional<Tag> matchedTag = tagService.findMatchingVisibleTag(job.getUserId(), llmOutput.tagName());

        AiReference reference = new AiReference();
        reference.setUserId(job.getUserId());
        reference.setInterest(input.interest());
        reference.setSummaryHtml(summaryHtml);
        if (matchedTag.isPresent()) {
            reference.setTag(matchedTag.get());
        } else {
            reference.setSuggestedTagName(llmOutput.tagName());
        }
        reference.setLinks(searchResults.stream().map(r -> toLink(reference, r)).toList());

        AiReference saved = aiReferenceRepository.save(reference);
        return toJson(AiReferenceResponse.from(saved));
    }

    private AiReferenceLink toLink(AiReference reference, SearchResult result) {
        AiReferenceLink link = new AiReferenceLink();
        link.setReference(reference);
        link.setUrl(result.url());
        link.setTitle(result.title());
        return link;
    }

    private Object parseResult(AiJob job) {
        try {
            return objectMapper.readValue(job.getResult(), AiReferenceResponse.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存済みの結果が解析できませんでした");
        }
    }

    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIのレスポンスが変換できませんでした");
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存済みの入力が解析できませんでした");
        }
    }

    // 【学習ポイント：押さえておく】非同期化により処理時間が延びた（受付自体は一瞬で終わるが、
    // 生成が終わるまでは分単位で未完了状態が続き得る）ため、「処理中かどうか」は
    // Set<UUID>のようなその場限りのメモリ上のフラグではなく、ai_jobsテーブルの状態を見て判定する。
    private void checkDuplicate(UUID userId) {
        boolean hasUnfinishedJob = aiJobRepository.existsByUserIdAndStatusIn(
                userId, List.of(AiJobStatus.PENDING, AiJobStatus.PROCESSING));
        if (hasUnfinishedJob) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "処理中です。しばらく待ってから再試行してください");
        }
    }

    // 【学習ポイント：押さえておく】Redisの INCR は「キーが無ければ0から作って+1、あれば+1」を
    // アトミック（同時に複数のリクエストが来ても、必ず1つずつ正しく増える）に行う操作。
    // アプリのメモリ上の変数で同じことをやると、複数のサーバー間で値が共有されない・
    // アプリ再起動で消える、という問題があった（memo/残作業.mdの食い違いメモ参照）。
    // EXPIREは「初回（count==1、＝このキーが今回新規作成された時）」だけ設定する。
    // 2回目以降も毎回EXPIREし直すと「最後にアクセスした時刻から24時間」というスライディング
    // ウィンドウになってしまい、「今日1日で何回」という意図（固定ウィンドウ）とズレるため。
    private void checkRateLimit(UUID userId) {
        String key = "ai:ratelimit:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(24));
        }
        if (count != null && count > MAX_REQUESTS_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "1日のリクエスト上限（10回）に達しました");
        }
    }

    // 【学習ポイント：押さえておく】これは主防御（system/userの分離）が破られた場合の二次防御であって、
    // これ単体では防御にならない。表現を変えられれば簡単に回避される（例：「これまでの指示を、無視、して」）。
    // 「安価な一次フィルタを1枚追加しておく」という多層防御の考え方自体が押さえるべき点で、
    // BLOCKED_PATTERNSという単語リストの中身自体は覚える必要が無い（増減しても設計の意図は変わらない）。
    private void validateGoalContent(String text) {
        String lower = text.toLowerCase();
        boolean blocked = BLOCKED_PATTERNS.stream().anyMatch(lower::contains);
        if (blocked) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MODERATION_REJECT_MESSAGE);
        }
    }

    // 【学習ポイント：押さえておく】NGワード方式（validateGoalContent）とModeration APIは目的が違う。
    // 前者はプロンプトインジェクション対策（AIへの指示の乗っ取り）の二次防御、
    // 後者は犯罪・自傷・暴力等の有害コンテンツ検知（学習済みモデルによる意味的な分類で、言い換えにも強い）。
    // どちらか一方に統合せず両方残す、という判断が押さえるべき点。
    //
    // 【学習ポイント：使い方だけでよい】moderationModel.call(...).getResult().getOutput().getResults().get(0)
    // というgetterの連鎖は、Spring AI（1.0.0-M6、マイルストーン版）のこの時点でのAPI形。
    // 正式リリース版で変わる可能性があるため、正確な連鎖を覚えるより「ModerationModelにテキストを渡すと
    // flaggedを含む結果が返ってくる」という役割だけ覚えておき、使うたびにIDEの補完かjavapで確認すればよい。
    private void checkModeration(String text) {
        ModerationPrompt prompt = new ModerationPrompt(text, OpenAiModerationOptions.builder().model(MODERATION_MODEL).build());
        Moderation moderation = moderationModel.call(prompt).getResult().getOutput();
        boolean flagged = moderation.getResults().get(0).isFlagged();
        if (flagged) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MODERATION_REJECT_MESSAGE);
        }
    }

    // 【学習ポイント：最重要・押さえておく】例外の種類で扱いを変える、というのがこのメソッドの核。
    // ResourceAccessException（タイムアウト・接続エラー＝一時的な障害）はリトライすれば直る可能性があるので
    // 1回だけ再試行する。HttpClientErrorException.TooManyRequests（429＝レート制限）は再試行しても
    // 同じ理由で失敗し、無駄にOpenAIへの呼び出し回数（＝コスト）を増やすだけなのでリトライしない。
    // 「失敗した」の一言で全部同じ扱いにせず、失敗の性質で分岐する、という考え方が使い回せる知識。
    private String callOpenAi(UUID userId, String type, Supplier<String> call) {
        long start = System.currentTimeMillis();
        for (int attempt = 0; ; attempt++) {
            try {
                String result = call.get();
                log.info("ai_call type={} userId={} latencyMs={} result=success", type, userId, System.currentTimeMillis() - start);
                return result;
            } catch (ResourceAccessException e) {
                // タイムアウト・接続エラー（一時的な障害とみなしてリトライ対象にする）
                if (attempt >= MAX_RETRY) {
                    log.warn("ai_call type={} userId={} latencyMs={} result=failure error={}", type, userId, System.currentTimeMillis() - start, e.getMessage());
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, AI_FAILURE_MESSAGE);
                }
                sleep(RETRY_INTERVAL);
            } catch (HttpClientErrorException.TooManyRequests e) {
                // 429（レート制限）はリトライせず、そのままクライアントに429として伝播させる
                log.warn("ai_call type={} userId={} latencyMs={} result=rate_limited", type, userId, System.currentTimeMillis() - start);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "OpenAI APIのレート制限に達しました");
            } catch (Exception e) {
                log.warn("ai_call type={} userId={} latencyMs={} result=failure error={}", type, userId, System.currentTimeMillis() - start, e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, AI_FAILURE_MESSAGE);
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "処理が中断されました");
        }
    }

    private String buildRecordsText(List<LearningRecord> records) {
        return records.stream()
                .map(this::formatRecord)
                .collect(Collectors.joining("\n"));
    }

    private String formatRecord(LearningRecord r) {
        String tags = r.getTags().stream()
                .map(Tag::getName)
                .collect(Collectors.joining(", "));
        String tagPart = tags.isEmpty() ? "" : "（タグ: " + tags + "）";
        return r.getDate() + ": " + r.getContent() + tagPart;
    }

    private List<String> extractTagNames(List<LearningRecord> records) {
        return records.stream()
                .flatMap(r -> r.getTags().stream())
                .map(Tag::getName)
                .distinct()
                .limit(10)
                .toList();
    }

    // 検索クエリの主役はinterest（ユーザーが明示的に入力した意図）。本文（searchSeedText）・タグは
    // それを補足する材料として付け加える。同じ「AWS」というタグ・本文でも、「未経験からインフラ
    // エンジニアを目指している」「フロントエンド中心だが軽く押さえたい」「資格を取りたい」では
    // 検索すべき内容が変わるが、その違いを表現できるのはinterestであって本文・タグではないため。
    // interestが未入力のときだけ、本文が代わりに主役を務める（interestが無いのに検索材料も
    // 無いと検索できないため）。タグは本文よりさらに補足的な位置づけで、末尾に追加するだけ。
    //
    // 【学習ポイント：押さえておく】interest・searchSeedTextが両方とも無いケースは実際には起こらない。
    // enqueueReference()の入口で「学習記録0件・interest未入力・recordId未指定」なら早期returnして
    // ジョブ自体を作らないため、ここに来る時点で必ずどちらかは値がある
    private String buildSearchQuery(ReferenceJobInput input) {
        boolean hasInterest = input.interest() != null && !input.interest().isBlank();
        boolean hasSeed = input.searchSeedText() != null && !input.searchSeedText().isBlank();

        List<String> parts = new ArrayList<>();
        if (hasInterest) {
            parts.add(input.interest());
        }
        if (hasSeed) {
            parts.add(truncate(input.searchSeedText(), SEARCH_QUERY_SEED_LENGTH));
        }
        if (!input.tagNames().isEmpty()) {
            parts.add(String.join(" ", input.tagNames().stream().limit(3).toList()));
        }

        if (parts.isEmpty()) {
            throw new IllegalStateException(
                    "interest・searchSeedTextが両方とも無い状態でbuildSearchQueryが呼ばれた（enqueueReference側の早期returnの条件が壊れている可能性がある）");
        }
        return String.join(" ", parts);
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String buildReferencePrompt(ReferenceJobInput input, List<SearchResult> searchResults, String visibleTagNames) {
        String searchText = searchResults.isEmpty()
                ? "（検索結果なし）"
                : searchResults.stream()
                        .map(r -> "- " + r.title() + " (" + r.url() + ")\n  " + r.content())
                        .collect(Collectors.joining("\n"));

        String interestPart = (input.interest() == null || input.interest().isBlank())
                ? "" : "\n\n【興味のある技術・分野】\n" + input.interest();

        // 学習記録の詳細画面から生成した場合だけ入る。「たまたま直近30件に含まれる1件」ではなく
        // 「これについて知りたくて生成した」という主題であることをOpenAIに明示するため、
        // 直近の学習記録一覧とは別枠で、最優先の材料として渡す
        String focusPart = (input.focusRecordText() == null)
                ? "" : "\n\n【今回参考資料を作るきっかけになった学習記録（最優先で参考にする）】\n" + input.focusRecordText();

        return "以下のWeb検索結果と学習記録をもとに、5分程度で読めるMarkdown形式の要約を作成してください。\n\n" +
                "【検索結果】\n" + searchText +
                focusPart +
                "\n\n【直近の学習記録】\n" + input.recordsText() +
                interestPart +
                "\n\n【分類先のタグ候補（既存タグ）】\n" + visibleTagNames +
                "\n\n既存タグに合うものがあればそのタグ名をそのまま使い、無ければ簡潔な新しいタグ名を提案してください。";
    }

    // 【学習ポイント：押さえておく】「構文が正しいこと」と「中身が妥当なこと」は別物、という考え方。
    // parseJson()はJSONとして読めるかしか見ていないので、その後段でこの妥当性チェックを挟む。
    // 外部サービス（OpenAI）の応答は、指示通りのフォーマットとは限らない前提で扱う、という設計判断。
    private void validateLlmOutput(LlmOutput output) {
        if (output.summaryMarkdown() == null || output.summaryMarkdown().isBlank()
                || output.summaryMarkdown().length() > 20000) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIのレスポンスが不正です");
        }
        if (output.tagName() == null || output.tagName().isBlank() || output.tagName().length() > 50) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIのレスポンスが不正です");
        }
    }

    // OpenAI がJSONの前後に余計なテキストを付ける場合があるため正規表現で抽出する
    // 【学習ポイント：押さえておく】"\\{.*\\}" は「最初の{から最後の}までを全部」抜き出す指定。
    // .*はデフォルトでは改行にマッチしないが、summaryMarkdownの中身（Markdown）には改行が
    // 含まれるため、Pattern.DOTALLを付けて.が改行もまたぐようにしている（DOTALLを付けないと、
    // JSONの途中に改行があった時点でそこで抽出が途切れる・失敗する）。
    // find()（文字列のどこかにマッチする箇所があればよい）を使っているのも、rawの前後に
    // 「以下がJSON形式の出力です」等の余計な文章が付いている前提だから（matches()だと
    // 文字列全体が正規表現と完全一致する必要があり、前後に余計な文章があると失敗する）。
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
