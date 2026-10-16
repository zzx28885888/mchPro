package com.excelai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Policy registration, Policy login, Policy tasks) {
    public RateLimitProperties {
        Objects.requireNonNull(registration, "registration rate limit is required");
        Objects.requireNonNull(login, "login rate limit is required");
        Objects.requireNonNull(tasks, "task rate limit is required");
    }

    public record Policy(int maxRequests, Duration window) {
        public Policy {
            if (maxRequests < 1 || window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("rate limit count and window must be positive");
            }
            try {
                if (window.toMillis() < 1) throw new IllegalArgumentException("rate limit window must be at least one millisecond");
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("rate limit window is too large", e);
            }
        }
    }
}
