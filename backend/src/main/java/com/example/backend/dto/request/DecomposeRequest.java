package com.example.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class DecomposeRequest {

    @NotBlank(message = "目標を入力してください")
    @Size(max = 200, message = "目標は200文字以内で入力してください")
    private String goal;
}
