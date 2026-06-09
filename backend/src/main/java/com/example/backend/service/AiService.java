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

    // ============================================================
    // 定数
    // ============================================================
    //
    // 1日あたりの最大リクエスト数。超えたら 429 を返す。
    private static final int MAX_REQUESTS_PER_DAY = 10;

    // 学習記録が0件のときに返すメッセージ。OpenAIを呼ばずにこれを返す。
    private static final String NO_RECORDS_MESSAGE = "学習記録がまだありません。記録を追加すると提案が表示されます。";

    // ============================================================
    // フィールド
    // ============================================================

    private final ChatClient chatClient;
    private final LearningRecordRepository learningRecordRepository;

    // ObjectMapper: JSON の文字列 ↔ Java オブジェクトの変換を行うクラス。
    // Spring Boot が自動でBeanを用意してくれるので、引数に書くだけで注入される。
    // ※ 覚える必要はない。「JSONをパースするやつ」くらいの認識でOK。
    private final ObjectMapper objectMapper;

    // ============================================================
    // 重複リクエスト防止用 Set
    // ============================================================
    //
    // 【Set とは】
    //   重複を許さないリスト。同じ値を2回 add しても1つしか入らない。
    //   add() の戻り値が true → 追加成功（そのユーザーは処理中でなかった）
    //   add() の戻り値が false → 追加失敗（すでに入っている = 処理中）
    //
    // 【ConcurrentHashMap.newKeySet() とは】
    //   複数のリクエストが同時に来ても安全に動く Set。
    //   普通の HashSet は同時アクセスに弱く、データが壊れる可能性がある。
    //   ConcurrentHashMap ベースの Set はそれを防いでくれる。
    //   ※ 「スレッドセーフな Set」くらいの認識でOK。詳細は覚えなくていい。
    //
    private final Set<UUID> processingUsers = ConcurrentHashMap.newKeySet();

    // ============================================================
    // レート制限用 Map
    // ============================================================
    //
    // ユーザーIDをキー、そのユーザーのリクエスト日時リストを値として保持する。
    //
    // 【Map とは】
    //   キーと値のペアを格納するデータ構造。
    //   例）{userId1: [10:00, 10:30, 11:00], userId2: [14:00]}
    //
    // 【ConcurrentHashMap とは】
    //   複数のリクエストが同時に来ても安全に動く Map。
    //   普通の HashMap は同時アクセスに弱い。
    //
    private final Map<UUID, List<Instant>> requestLog = new ConcurrentHashMap<>();

    // ============================================================
    // 学習提案（suggest）
    // ============================================================
    //
    // 【@Cacheable とは】
    //   このメソッドの結果をキャッシュに保存するアノテーション。
    //   2回目以降は同じ userId で呼ばれたとき、メソッド本体を実行せずに
    //   キャッシュの値をそのまま返す。
    //   value = "suggestions" → AiConfig で定義したキャッシュ名
    //   key = "#userId"       → userId ごとに別々にキャッシュする
    //
    // 【@Transactional(readOnly = true) とは】
    //   DB の読み取りだけ行うメソッドにつける。
    //   これを付けることで Hibernate の最適化が効き、パフォーマンスが上がる。
    //   また、タグを取得するときの LAZY ローディングも正常に動作する。
    //   （LAZY ローディング = 最初は読み込まず、アクセスしたときに初めてSQLを発行する仕組み）
    //
    @Cacheable(value = "suggestions", key = "#userId")
    @Transactional(readOnly = true)
    public SuggestResponse suggest(UUID userId) {

        // ① 直近30件の学習記録をタグ込みで取得する
        //    findTop30WithTagsByUserId は LEFT JOIN FETCH でタグを一緒に取得するクエリ
        //    → タグアクセス時のN+1問題を防ぐ
        List<LearningRecord> records = learningRecordRepository.findTop30WithTagsByUserId(userId);

        // ② 学習記録が0件なら OpenAI を呼ばずに固定メッセージを返す
        //    データがないのに OpenAI を呼んでもコストだけかかって無意味なため
        if (records.isEmpty()) {
            return new SuggestResponse(List.of(), NO_RECORDS_MESSAGE);
        }

        // ③ 重複リクエストチェック（同じユーザーが処理中に連打した場合に 429 を返す）
        checkDuplicate(userId);

        // ④ レート制限チェック（1日10回を超えたら 429 を返す）
        checkRateLimit(userId);

        try {
            // ⑤ 学習記録をプロンプト用のテキストに整形する
            String userPrompt = buildSuggestPrompt(records);

            // ⑥ OpenAI に送信する
            //    .system() にシステムの指示を書く（プロンプトインジェクション対策として分離）
            //    .user() にユーザーデータを埋め込む
            //    .call().content() で文字列レスポンスを受け取る
            String raw = chatClient.prompt()
                    .system("あなたはエンジニアのキャリア支援AIです。必ず以下のJSON形式のみで返してください。説明文は不要です。" +
                            "{\"suggestions\":[{\"title\":\"技術名\",\"reason\":\"提案理由\"}]}")
                    .user(userPrompt)
                    .call()
                    .content();

            // ⑦ レスポンスをJSONとしてパースして SuggestResponse に変換する
            return parseJson(raw, SuggestResponse.class);

        } catch (ResponseStatusException e) {
            // parseJson が throw した ResponseStatusException はそのまま再スローする
            throw e;
        } catch (Exception e) {
            // OpenAI への通信失敗など予期しない例外は 500 に変換して返す
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AIサービスとの通信に失敗しました");
        } finally {
            // 成功・失敗どちらの場合も必ず処理中フラグを解除する
            // finally は例外が起きても必ず実行されるブロック
            processingUsers.remove(userId);
        }
    }

    // ============================================================
    // タスク分解（decompose）
    // ============================================================
    //
    // goal（目標文字列）を受け取り、OpenAI に具体的なタスクリストを生成させる。
    // suggest と違い、goal の内容は毎回異なるためキャッシュしない。
    //
    public DecomposeResponse decompose(UUID userId, String goal) {
        checkDuplicate(userId);
        checkRateLimit(userId);

        try {
            String raw = chatClient.prompt()
                    .system("あなたはエンジニアのキャリア支援AIです。必ず以下のJSON形式のみで返してください。説明文は不要です。" +
                            "{\"tasks\":[\"タスク1\",\"タスク2\"]}")
                    // goal をユーザー入力として渡す（システムプロンプトとは分離する）
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

    // ============================================================
    // 重複リクエストチェック（private）
    // ============================================================
    //
    // processingUsers.add(userId) の戻り値で処理中かどうかを判断する。
    //   true  → 追加成功 = 処理中でなかった → そのまま続ける
    //   false → 追加失敗 = すでに処理中   → 429 を返す
    //
    private void checkDuplicate(UUID userId) {
        if (!processingUsers.add(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "処理中です。しばらく待ってから再試行してください");
        }
    }

    // ============================================================
    // レート制限チェック（private）
    // ============================================================
    //
    // 過去24時間以内のリクエスト数が10回未満かチェックする。
    //
    // 【Instant とは】
    //   Java で「ある時点」を表すクラス。
    //   Instant.now() で現在時刻を取得できる。
    //   minus(24, ChronoUnit.HOURS) で「24時間前」の時刻を計算できる。
    //
    // 【synchronized とは】
    //   「このブロックは1スレッドずつ順番に実行する」という指定。
    //   複数リクエストが同時にカウントを更新すると数が狂うため、排他制御している。
    //   ※ 覚える必要はない。「同時アクセスを安全に処理するためのキーワード」くらいでOK。
    //
    private void checkRateLimit(UUID userId) {
        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Instant> timestamps = requestLog.computeIfAbsent(userId, k -> new ArrayList<>());
        synchronized (timestamps) {
            // 24時間より古いタイムスタンプを除去する
            timestamps.removeIf(t -> t.isBefore(oneDayAgo));
            if (timestamps.size() >= MAX_REQUESTS_PER_DAY) {
                // レート制限に引っかかった場合は processingUsers からも除去してから例外を投げる
                processingUsers.remove(userId);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "1日のリクエスト上限（10回）に達しました");
            }
            // 今回のリクエスト日時を記録する
            timestamps.add(Instant.now());
        }
    }

    // ============================================================
    // プロンプト組み立て（private）
    // ============================================================
    //
    // LearningRecord のリストを「2026/06/08: 内容（タグ: Java, Spring）」の形式に変換する。
    //
    // 【stream().map().collect() とは】
    //   リストの各要素を変換して新しいリストを作るJavaの書き方。
    //   JavaScriptの .map() と同じ発想。
    //   Collectors.joining("\n") は「\n（改行）で区切って1つの文字列にまとめる」という意味。
    //
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

    // ============================================================
    // JSONパース（private）
    // ============================================================
    //
    // OpenAI のレスポンスから JSON 部分だけを取り出してパースする。
    //
    // 【なぜ正規表現で抽出するのか】
    //   OpenAI は「JSONだけ返して」と指示しても、まれに前後に余計なテキストを付けることがある。
    //   例）「以下がタスクです：{"tasks": [...]}」
    //   そのまま JSON としてパースすると失敗するため、{ ... } 部分だけ抜き出す。
    //
    // 【Pattern.compile("\\{.*\\}", Pattern.DOTALL) とは】
    //   \\{  → { にマッチ（{ は正規表現の特殊文字なので \\ でエスケープ）
    //   .*   → 任意の文字列（何文字でもOK）
    //   \\}  → } にマッチ
    //   Pattern.DOTALL → . が改行文字にもマッチするようにするオプション
    //   ※ 正規表現の詳細は覚える必要はない。「{}の中身を取り出してる」くらいでOK。
    //
    // 【objectMapper.readValue(json, type) とは】
    //   JSON 文字列を指定した Java クラスのオブジェクトに変換するメソッド。
    //   例）"{\"tasks\":[...]}" → DecomposeResponse オブジェクト
    //
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
