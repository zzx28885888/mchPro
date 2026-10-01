package com.excelai.task;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
/**
 * 中文：保存前端轮询所需的实时进度字段；PostgreSQL 仍保存可恢复的任务主记录。
 * English: Stores live progress fields for frontend polling; PostgreSQL remains the durable task record.
 */
public class TaskRedisService {
    private final StringRedisTemplate r;

    public TaskRedisService(StringRedisTemplate r) {
        this.r = r;
    }

    public void update(Long id, String status, int progress, String error, Long result) {
        var o = r.opsForHash();
        String k = "excelai:task:" + id;
        o.put(k, "status", status);
        o.put(k, "progress", String.valueOf(progress));
        if (error != null) o.put(k, "error", error);
        if (result != null) o.put(k, "resultFileId", result.toString());
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
    public Map<Object, Object> get(Long id) {
        return r.opsForHash().entries("excelai:task:" + id);
    }
}
