package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TagUpdateRequest {

    @NotBlank(message = "タグ名は必須です")
    @Size(max = 50, message = "タグ名は50文字以内で入力してください")
    private String name;
}
