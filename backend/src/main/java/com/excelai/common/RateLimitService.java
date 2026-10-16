package com.excelai.common;

import com.excelai.config.RateLimitProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class RateLimitService {
    private static final String WINDOW_SCRIPT = """
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local blocked = 0
            if current >= limit then
              blocked = 1
            else
              current = redis.call('INCR', KEYS[1])
              if current == 1 then
                redis.call('PEXPIRE', KEYS[1], window)
              end
            end
            return {blocked, redis.call('PTTL', KEYS[1])}
            """;
    private static final RedisScript<List> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(WINDOW_SCRIPT, List.class);
    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void enforce(String category, String subject, RateLimitProperties.Policy policy) {
        long windowMillis = policy.window().toMillis();
        String key = "excelai:rate:" + category + ":" + hash(subject == null ? "unknown" : subject);
        List<?> result;
        try {
            result = redis.execute(RATE_LIMIT_SCRIPT, List.of(key),
                    String.valueOf(policy.maxRequests()), String.valueOf(windowMillis));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "rate limiter unavailable");
        }
        if (result == null || result.size() < 2) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "rate limiter unavailable");
        }

        long blocked = number(result.get(0));
        long remainingMillis = number(result.get(1));
        if (blocked != 0) {
            long retryAfterSeconds = Math.max(1, (Math.max(0, remainingMillis) + 999) / 1000);
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "rate limiter unavailable");
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
