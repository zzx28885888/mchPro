package com.example.agent.tool.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 中文：给工具参数提供稳定名称和说明，避免依赖 Java 编译器保留参数名。
 * English: Provides a stable name and description for a tool parameter instead of relying on compiler metadata.
 */
public @interface ToolParam {
    String name();

    String description() default "";

    boolean required() default true;
}
