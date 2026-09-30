package com.example.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ReferenceRequest {

    // 任意入力。空欄なら学習記録だけをもとに参考資料を生成する
    @Size(max = 200, message = "興味のある技術・分野は200文字以内で入力してください")
    private String interest;

    // 任意入力。学習記録の詳細画面から「この記録について参考資料を作る」で生成する場合だけ値が入る
    private UUID recordId;
}
