package com.example.backend.service;

import com.example.backend.dto.AiJobMessage;
import com.example.backend.entity.AiJob;
import com.example.backend.entity.AiJobStatus;
import com.example.backend.repository.AiJobRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 【学習ポイント：押さえておく】@SqsListenerを付けたメソッドは、アプリ起動時にSpring Cloud AWSが
// 自動でバックグラウンドスレッドを立ち上げ、指定したキューを継続的にポーリングしてくれる。
// while(true)のようなポーリングのループを自分で書く必要は無い。
//
// 【学習ポイント：使い方だけでよい】io.awspring.cloud.sqs.annotation.SqsListener というパッケージ名は
// Spring Cloud AWSのバージョンで変わりうる実装の詳細。「@SqsListenerというアノテーションがある」
// という役割だけ覚えておけば、正確なimport先は使うたびにIDEの補完で確認すればよい。
@Component
@RequiredArgsConstructor
public class AiJobWorker {

    private final AiJobRepository aiJobRepository;
    private final AiService aiService;

    @SqsListener("career-support-ai-jobs")
    public void handle(AiJobMessage message) {
        AiJob job = aiJobRepository.findById(message.jobId()).orElseThrow();

        // 【学習ポイント：最重要・押さえておく】冪等性チェック。SQSは「最低1回は届くが、
        // 稀に重複して届くこともある」仕組み（at-least-once配信）。再配信で同じメッセージが
        // 2回目に届いたとき、すでに完了しているならOpenAIを呼び直さない。
        // これが無いと、再配信のたびにOpenAI課金が二重に発生するリスクがある。
        if (job.getStatus() == AiJobStatus.DONE || job.getStatus() == AiJobStatus.FAILED) {
            return;
        }

        job.markProcessing();
        aiJobRepository.save(job);

        try {
            String result = aiService.generateResult(job);
            job.markDone(result);
        } catch (Exception e) {
            job.markFailed("現在AIサービスが利用できません。しばらく経ってから再度お試しください");
        }
        aiJobRepository.save(job);

        // 【学習ポイント：押さえておく】ここで例外を再スローせず、必ず正常終了させている。
        // @SqsListenerのメソッドが例外を投げずに終わると、Spring Cloud AWSは「処理済み」として
        // SQSにDeleteMessageを送る。もし再スローしていたら、SQSは再配信・最終的にDLQへの退避を
        // 行うが、それは「OpenAI呼び出し自体が有料で、再配信されると二重課金になる」という
        // 前述の冪等性チェックと矛盾する動きになる。失敗の扱いは全てこのメソッド内で完結させ、
        // ジョブをFAILEDにする形でユーザーに伝える（ユーザーがもう一度ボタンを押せば新しいジョブになる）。
    }
}
