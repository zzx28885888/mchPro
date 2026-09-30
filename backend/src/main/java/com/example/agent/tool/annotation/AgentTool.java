package com.example.agent.tool.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 中文：声明一个可被 Agent 调用的 Java 方法；注册表启动时扫描该注解并生成工具描述。
 * English: Marks a Java method as an Agent tool; the registry scans it at startup to build its schema.
 */
public @interface AgentTool {
    String name();

    String description();

    String permission();

    long timeoutMs() default 3000;
}
