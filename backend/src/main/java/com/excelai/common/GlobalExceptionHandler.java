package com.excelai.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
/**
 * 中文：集中把业务异常与未处理异常转换为 JSON，避免 Controller 重复拼装错误响应。
 * English: Converts business and unhandled exceptions into JSON centrally, keeping controllers free of duplicate error handling.
 */
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("timestamp", Instant.now().toString(), "message", e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("timestamp", Instant.now().toString(), "message", "request failed"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("timestamp", Instant.now().toString(), "message", "invalid request"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<?> rateLimited(RateLimitExceededException e) {
        return ResponseEntity.status(429)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(Map.of("timestamp", Instant.now().toString(), "message", "rate limit exceeded"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception e) {
        LOGGER.error("Unhandled request exception", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("timestamp", Instant.now().toString(), "message", "internal error"));
    }
}
