package com.example.compiler.model;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message, List<String> validationErrors) {
    public ErrorResponse(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message, null);
    }

    public ErrorResponse(int status, String error, String message, List<String> validationErrors) {
        this(LocalDateTime.now(), status, error, message, validationErrors);
    }
}
