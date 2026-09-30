package com.example.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 中文：Spring Boot 启动入口，同时扫描 V1.6 Agent 核心与合并进来的 Excel SaaS 业务包。
 * English: Spring Boot entry point; scans both the V1.6 Agent core and the integrated Excel SaaS packages.
 */
@SpringBootApplication(scanBasePackages = {"com.example.agent", "com.excelai"})
// 中文：扫描 MyBatis Mapper 接口并为它们创建代理 Bean。English: Register MyBatis mapper interfaces as Spring proxy beans.
@MapperScan("com.excelai.persistence.mapper")
@EnableAsync
public class AgentBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentBackendApplication.class, args);
    }
}
