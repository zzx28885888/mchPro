package com.example.agent.tool.registry;

import com.example.agent.tool.annotation.AgentTool;
import com.example.agent.tool.annotation.ToolParam;
import com.example.agent.tool.model.ToolDefinition;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
/**
 * 中文：启动时扫描 Spring Bean 上的 @AgentTool 方法，把注解参数转换成可供模型理解的 JSON Schema。
 * English: Scans Spring beans for @AgentTool methods at startup and converts their parameter annotations into model-facing JSON schemas.
 */
public class ToolRegistry {

    private final Map<String, RegisteredTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(ApplicationContext context) {
        discover(context);
    }

    private void discover(ApplicationContext context) {
        // 中文：扫描容器中所有 Bean，因此工具只需实现为 Spring Bean 并添加注解即可注册。
        // English: Scanning all Spring beans means a tool is registered simply by making it a bean and adding the annotation.
        context.getBeansOfType(Object.class).forEach((beanName, bean) -> {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getDeclaredMethods()) {
                AgentTool ann = method.getAnnotation(AgentTool.class);
                if (ann == null) continue;

                Map<String, RegisteredTool.ParameterSpec> params = new LinkedHashMap<>();
                for (Parameter parameter : method.getParameters()) {
                    ToolParam p = parameter.getAnnotation(ToolParam.class);
                    if (p == null) {
                        throw new IllegalStateException(
                                "AgentTool parameter must use @ToolParam: "
                                        + targetClass.getName() + "#" + method.getName());
                    }
                    params.put(p.name(), new RegisteredTool.ParameterSpec(
                            p.name(), p.description(), p.required(), parameter.getType()));
                }

                ToolDefinition definition = new ToolDefinition(
                        ann.name(), ann.description(), ann.permission(), ann.timeoutMs(),
                        buildSchema(params));

                if (tools.putIfAbsent(ann.name(),
                        new RegisteredTool(definition, bean, method, params)) != null) {
                    throw new IllegalStateException("Duplicate AgentTool name: " + ann.name());
                }
            }
        });
    }

    private Map<String, Object> buildSchema(Map<String, RegisteredTool.ParameterSpec> params) {
        // 中文：additionalProperties=false 会在模型侧表达“不接受未声明参数”的约束。
        // English: additionalProperties=false tells the model-facing schema to reject undeclared arguments.
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        params.values().forEach(p -> {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", jsonType(p.type()));
            property.put("description", p.description());
            properties.put(p.name(), property);
            if (p.required()) required.add(p.name());
        });

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    private String jsonType(Class<?> type) {
        if (type == int.class || type == Integer.class ||
                type == long.class || type == Long.class ||
                type == double.class || type == Double.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) return "object";
        return "string";
    }

    public Collection<RegisteredTool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public RegisteredTool get(String name) {
        return tools.get(name);
    }
}
