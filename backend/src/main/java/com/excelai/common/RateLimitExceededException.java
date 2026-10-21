package com.excelai.common;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
