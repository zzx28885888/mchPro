package com.example.agent.tool.service;

import com.example.agent.tool.annotation.AgentTool;
import com.example.agent.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
/**
 * 中文：V1.6 最初的演示工具，返回内存中的假用户/订单数据；不是 SaaS 真实业务接口。
 * English: Original V1.6 demo tools returning in-memory sample user/order data; these are not SaaS business endpoints.
 */
public class DemoAgentTools {

    @AgentTool(
            name = "getUser",
            description = "Get a demo user by user ID.",
            permission = "user:read",
            timeoutMs = 3000)
    public Map<String, Object> getUser(
            @ToolParam(name = "userId", description = "User ID") Integer userId) {
        return Map.of(
                "id", userId,
                "name", "demo-user-" + userId,
                "status", "ACTIVE");
    }

    @AgentTool(
            name = "getOrder",
            description = "Get a demo order by order ID.",
            permission = "order:read",
            timeoutMs = 3000)
    public Map<String, Object> getOrder(
            @ToolParam(name = "orderId", description = "Order ID") Integer orderId) {
        return Map.of(
                "orderId", orderId,
                "amount", 99.90,
                "status", "PAID");
    }

    @AgentTool(
            name = "slowTool",
            description = "Demo tool used to verify timeout handling.",
            permission = "debug:read",
            timeoutMs = 1000)
    public Map<String, Object> slowTool(
            @ToolParam(name = "sleepMs", description = "Sleep milliseconds") Integer sleepMs)
            throws InterruptedException {
        Thread.sleep(sleepMs);
        return Map.of("sleptMs", sleepMs);
    }
}
