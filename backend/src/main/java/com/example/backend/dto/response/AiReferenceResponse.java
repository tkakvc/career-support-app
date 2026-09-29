package com.example.backend.dto.response;

import com.example.backend.entity.AiReference;
import com.example.backend.entity.AiReferenceLink;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// GET /api/ai/references の1件分、および GET /api/ai/jobs/{jobId} の result（DONE時）と同じ形
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiReferenceResponse {

    private UUID id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String interest;

    private String summaryHtml;

    // 既存タグに一致した場合だけ値が入る。一致しなかった場合はnullで、代わりにsuggestedTagNameを見る
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID tagId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tagName;

    // tagIdがnullのときだけ値が入る、AIが提案したタグ名（まだ作成されていない）
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String suggestedTagName;

    private List<Link> links;
    private LocalDateTime createdAt;

    public static AiReferenceResponse from(AiReference reference) {
        List<Link> links = reference.getLinks().stream()
                .map(Link::from)
                .toList();
        return new AiReferenceResponse(
                reference.getId(),
                reference.getInterest(),
                reference.getSummaryHtml(),
                reference.getTag() == null ? null : reference.getTag().getId(),
                reference.getTag() == null ? null : reference.getTag().getName(),
                reference.getSuggestedTagName(),
                links,
                reference.getCreatedAt());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Link {
        private String url;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String title;

        public static Link from(AiReferenceLink link) {
            return new Link(link.getUrl(), link.getTitle());
        }
    }
}
