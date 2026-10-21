package com.example.agent.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
/** 中文：给 Docker/开发者使用的存活检查，同时轻量探测 PostgreSQL 与 Redis。English: Liveness endpoint with lightweight PostgreSQL and Redis probes. */
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redis;

    public HealthController(DataSource dataSource, StringRedisTemplate redis) {
        this.dataSource = dataSource;
        this.redis = redis;
    }

    @GetMapping("/api/health")
    public ResponseEntity<java.util.Map<String, Object>> health() {
        // 中文：使用 JDBC isValid 验证数据库连接；Redis 使用 PING 验证连接。
        // English: Validate the JDBC connection with isValid; check Redis with PING.
        boolean db = false;
        boolean redisOk = false;

        try (Connection connection = dataSource.getConnection()) {
            db = connection.isValid(1);
        } catch (Exception ignored) {
            db = false;
        }

        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            redisOk = "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception ignored) {
            redisOk = false;
        }

        HttpStatus status = db && redisOk ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(java.util.Map.of(
                "status", db && redisOk ? "UP" : "DOWN",
                "postgres", db,
                "redis", redisOk));
    }
}
