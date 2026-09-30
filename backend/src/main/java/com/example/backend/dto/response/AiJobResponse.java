package com.example.backend.dto.response;

import com.example.backend.entity.AiJobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

// resultはObject型：DB（ai_jobs.result）にはジョブ結果をJSON文字列として保存しており、
// ai_jobsテーブル自体はジョブ種別に依存しない作り。今のところ実際に入るのはAiReferenceResponse
// （AiService.parseResult()でobjectMapper.readValueして詰め直している）。
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiJobResponse(AiJobStatus status, Object result, String message) {

    // pending/done/failedはAiJobStatus（PENDING/PROCESSING/DONE/FAILED）と対応する組み立て方。
    // pending(status)だけstatusを引数に取るのは、PENDINGとPROCESSINGの2つをこれ1つでカバーする
    // ため（AiService.getJob()のswitch式参照）。done/failedは状態が1対1で固定。
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
