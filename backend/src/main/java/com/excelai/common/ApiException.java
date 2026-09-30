package com.excelai.common;

import org.springframework.http.HttpStatus;

/**
 * 中文：携带 HTTP 状态码的业务异常，由 GlobalExceptionHandler 转换成统一 JSON 响应。
 * English: Business exception carrying an HTTP status, translated into a uniform JSON response by GlobalExceptionHandler.
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus s, String m) {
        super(m);
        status = s;
    }

    public HttpStatus status() {
        return status;
    }
}
