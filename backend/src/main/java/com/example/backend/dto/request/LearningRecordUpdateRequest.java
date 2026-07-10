package com.example.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class LearningRecordUpdateRequest {

    @NotNull(message = "学習日は必須です")
    @PastOrPresent(message = "学習日は過去日または当日を指定してください")
    private LocalDate date;

    @NotBlank(message = "学習内容は必須です")
    @Size(max = 2000, message = "学習内容は2000文字以内で入力してください")
    private String content;

    @NotNull(message = "学習時間は必須です")
    @Min(value = 1, message = "学習時間は1分以上を指定してください")
    @Max(value = 1440, message = "学習時間は1440分以内を指定してください")
    private Integer duration;

    // null のとき既存タグを維持。空配列のとき全タグ削除。最大10件。
    @Size(max = 10, message = "タグは10件以内で指定してください")
    private List<UUID> tagIds;
}
