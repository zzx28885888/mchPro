package com.example.agent.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
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
    public Object health() throws Exception {
        // 中文：数据库连接可借出即视为可用；Redis 使用 PING 验证连接。
        // English: A successfully acquired JDBC connection indicates database availability; Redis is checked with PING.
        boolean db = false;
        boolean redisOk = false;

        try (Connection ignored = dataSource.getConnection()) {
            db = true;
        }

        try {
            redisOk = "PONG".equalsIgnoreCase(redis.getConnectionFactory()
                    .getConnection().ping());
        } catch (Exception ignored) {
        }

        return java.util.Map.of(
                "status", "UP",
                "postgres", db,
                "redis", redisOk
        );
    }
}
