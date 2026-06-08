package com.example.backend.exception;

// RuntimeException を継承することで、throws 宣言なしに throw できる（非検査例外）
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
