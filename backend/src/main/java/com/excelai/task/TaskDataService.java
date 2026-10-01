package com.excelai.task;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

@Service
/**
 * 中文：按 taskId 隔离存储当前 Excel 行集和输出文件 ID；短 TTL 避免临时 Agent 数据无限堆积。
 * English: Stores each task's working rows and output-file ID under task-scoped Redis keys with a short TTL.
 */
public class TaskDataService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public TaskDataService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> rows(Long taskId) {
        // 中文：每次读取反序列化为独立列表；调用方修改后需通过 setRows 写回 Redis。
        // English: Each read returns a deserialized copy; callers must use setRows to persist mutations.
        String json = redis.opsForValue().get(key(taskId, "rows"));
        if (json == null) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read task rows from Redis", e);
        }
    }

    public void setRows(Long taskId, List<Map<String, Object>> data) {
        try {
            redis.opsForValue().set(key(taskId, "rows"), mapper.writeValueAsString(data), Duration.ofHours(2));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save task rows to Redis", e);
        }
    }

    public void markInputsInspected(Long taskId) {
        redis.opsForValue().set(key(taskId, "workflowInspected"), "true", Duration.ofHours(2));
    }

    public boolean inputsInspected(Long taskId) {
        return "true".equals(redis.opsForValue().get(key(taskId, "workflowInspected")));
    }
    public void setWorkflowResult(Long taskId, com.excelai.excel.WorkflowResult result) {
        try {
            redis.opsForValue().set(key(taskId, "workflowResult"), mapper.writeValueAsString(result), Duration.ofHours(2));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save workflow result", e);
        }
    }

    public com.excelai.excel.WorkflowResult workflowResult(Long taskId) {
        String json = redis.opsForValue().get(key(taskId, "workflowResult"));
        if (json == null) return null;
        try {
            return mapper.readValue(json, com.excelai.excel.WorkflowResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read workflow result", e);
        }
    }
    public void setResult(Long taskId, Long fileId) {
        redis.opsForValue().set(key(taskId, "result"), fileId.toString(), Duration.ofHours(2));
    }

    public Long result(Long taskId) {
        String v = redis.opsForValue().get(key(taskId, "result"));
        return v == null ? null : Long.valueOf(v);
    }

    public void clear(Long taskId) {
        redis.delete(List.of(key(taskId, "rows"), key(taskId, "result"), key(taskId, "workflowResult"), key(taskId, "workflowInspected")));
    }

    private String key(Long taskId, String part) {
        return "excelai:taskdata:" + taskId + ":" + part;
    }
}
