package com.example.backend.service;

import com.example.backend.dto.AiJobMessage;
import com.example.backend.entity.AiJob;
import com.example.backend.entity.AiJobStatus;
import com.example.backend.repository.AiJobRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// @SqsListenerを付けたメソッドは、アプリ起動時にSpring Cloud AWSが自動でバックグラウンド
// スレッドを立ち上げ、指定したキューを継続的にポーリングする。
@Component
@RequiredArgsConstructor
public class AiJobWorker {

    private final AiJobRepository aiJobRepository;
    private final AiService aiService;

    @SqsListener("career-support-ai-jobs")
    public void handle(AiJobMessage message) {
        AiJob job = aiJobRepository.findById(message.jobId()).orElseThrow();

        // 冪等性チェック。SQSは「最低1回は届くが稀に重複して届くこともある」仕組み
        // （at-least-once配信）。再配信で同じメッセージが届いても、既に完了しているなら
        // OpenAIを呼び直さない（無いと再配信のたびに二重課金が発生しうる）。
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

        // ここで例外を再スローせず正常終了させる。再スローするとSQSが再配信・DLQ退避を行うが、
        // それは冪等性チェックの前提（再配信されても二重課金しない）と矛盾する。失敗はジョブを
        // FAILEDにする形で完結させる（ユーザーがもう一度ボタンを押せば新しいジョブになる）。
    }
}
