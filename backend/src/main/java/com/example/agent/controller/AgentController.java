package com.example.agent.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
/** 中文：V1.6 通用 Agent 对话转发接口。English: Generic V1.6 Agent chat proxy endpoint. */
public class AgentController {

    private final RestClient client;

    public AgentController(@Value("${agent.service.url}") String agentUrl) {
        this.client = RestClient.builder()
                .baseUrl(agentUrl)
                .build();
    }

    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        // 中文：业务编排由 Python/LangGraph 执行；这里仅转发请求并返回 Agent 响应。
        // English: Python/LangGraph owns orchestration; this controller only forwards the request and response.
        return client.post()
                .uri("/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
    }
}
