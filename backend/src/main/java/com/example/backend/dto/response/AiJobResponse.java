package com.example.backend.dto.response;

import com.example.backend.entity.AiJobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

// 【学習ポイント：押さえておく】resultをObject型にしている理由：DB（ai_jobs.result）にはジョブの
// 結果をJSON文字列として保存しており、ai_jobsテーブル自体はジョブ種別に依存しない作りにしてある
// （現状は参考資料生成の1種類だが、将来別種のジョブが増えても同じテーブルで扱えるように）。
// AiJobResponse側もその形に合わせてObjectのまま持たせている。今のところ実際に入るのは
// AiReferenceResponse（AiService.parseResult() で objectMapper.readValue して詰め直している）。
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiJobResponse(AiJobStatus status, Object result, String message) {

    // pending/done/failedはAiJobStatus（PENDING/PROCESSING/DONE/FAILED）と対応する組み立て方。
    // pending(status)だけstatusを引数に取るのは、PENDINGとPROCESSINGの2つをこれ1つでカバーする
    // ため（AiService.getJob()のswitch式で case PENDING, PROCESSING -> pending(job.getStatus()) と
    // している）。done/failedは状態が1対1で固定なので、AiJobStatusを直接書いている。
    public static AiJobResponse pending(AiJobStatus status) {
        return new AiJobResponse(status, null, null);
    }

    public static AiJobResponse done(Object result) {
        return new AiJobResponse(AiJobStatus.DONE, result, null);
    }

    public static AiJobResponse failed(String message) {
        return new AiJobResponse(AiJobStatus.FAILED, null, message);
    }
}
