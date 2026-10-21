package com.excelai.task;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
@Service
/**
 * 中文：保存短期任务预览摘要；任务状态和进度以 PostgreSQL 为准。
 * English: Stores short-lived task preview digests; PostgreSQL is authoritative for task status and progress.
 */
public class TaskRedisService {
    private final StringRedisTemplate r;

    public TaskRedisService(StringRedisTemplate r) {
        this.r = r;
    }

    public void putPreview(Long userId, String fingerprint, String digest) {
        r.opsForValue().set(previewKey(userId, fingerprint), digest, Duration.ofMinutes(15));
    }

    public String getPreview(Long userId, String fingerprint) {
        return r.opsForValue().get(previewKey(userId, fingerprint));
    }

    public void deletePreview(Long userId, String fingerprint) {
        r.delete(previewKey(userId, fingerprint));
    }

    private String previewKey(Long userId, String fingerprint) {
        return "excelai:taskpreview:" + userId + ":" + fingerprint;
    }
}
