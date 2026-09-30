package com.excelai.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
/**
 * 中文：集中把业务异常与未处理异常转换为 JSON，避免 Controller 重复拼装错误响应。
 * English: Converts business and unhandled exceptions into JSON centrally, keeping controllers free of duplicate error handling.
 */
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("timestamp", Instant.now().toString(), "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("timestamp", Instant.now().toString(), "message",
                e.getMessage() == null ? "internal error" : e.getMessage()));
    }
}
