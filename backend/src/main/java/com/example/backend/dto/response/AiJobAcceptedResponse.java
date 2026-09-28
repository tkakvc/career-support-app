package com.example.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiJobAcceptedResponse {

    // ジョブを作成した場合のみ値が入る（学習記録0件かつinterest未入力の即時応答ではnull）
    private UUID jobId;

    // 即時応答（学習記録0件かつinterest未入力）のときだけ値が入る。jobIdがnullならこちらを見る
    private String message;

    public static AiJobAcceptedResponse accepted(UUID jobId) {
        return new AiJobAcceptedResponse(jobId, null);
    }

    public static AiJobAcceptedResponse immediateMessage(String message) {
        return new AiJobAcceptedResponse(null, message);
    }
}
