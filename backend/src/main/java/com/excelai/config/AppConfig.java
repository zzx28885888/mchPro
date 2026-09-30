package com.excelai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
/** 中文：声明应用级 Spring Bean 配置，例如异步任务线程池。English: Declares application-level Spring beans such as the asynchronous task pool. */
public class AppConfig {
    @Bean("taskExecutor")
    ThreadPoolTaskExecutor taskExecutor(AppProperties p) {
        // 中文：任务池和队列大小均由配置文件控制，线程名前缀便于在日志里识别异步任务。
        // English: Pool and queue sizes are configurable; the prefix makes asynchronous task threads easy to identify in logs.
        var e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(p.task().corePoolSize());
        e.setMaxPoolSize(p.task().maxPoolSize());
        e.setQueueCapacity(p.task().queueCapacity());
        e.setThreadNamePrefix("excel-task-");
        e.initialize();
        return e;
    }

}
