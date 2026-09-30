package com.excelai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
/**
 * 中文：把 app.* 配置绑定成类型安全的 Java record，减少散落在业务代码里的字符串配置键。
 * English: Binds app.* settings into typed Java records instead of scattering string-based configuration lookups.
 */
public record AppProperties(Storage storage, Jwt jwt, Task task) {
    /**
     * 中文：文件根目录。English: Root directory for private uploaded/generated files.
     */
    public record Storage(String root) {
    }

    /**
     * 中文：JWT 签名密钥和有效期。English: JWT signing secret and lifetime.
     */
    public record Jwt(String secret, long expirationHours) {
    }

    /**
     * 中文：异步任务线程池的并发数与排队上限。English: Async task-pool concurrency and queue limits.
     */
    public record Task(int corePoolSize, int maxPoolSize, int queueCapacity) {
    }
}
