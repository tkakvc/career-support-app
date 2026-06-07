package com.example.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;

@Getter
public class ErrorResponse {

    private final int status;
    private final String message;

    // バリデーションエラー（400）のときだけ使う。null のときは JSON キーごと省略したいため NON_NULL を指定
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<String> errors;

    // errors が不要なケース（401, 409, 500 など）向けの簡略版。コンストラクタチェーンで3引数版に委譲
    public ErrorResponse(int status, String message) {
        this(status, message, null);
    }

    public ErrorResponse(int status, String message, List<String> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors;
    }
}
