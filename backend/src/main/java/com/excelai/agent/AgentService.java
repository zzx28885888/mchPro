package com.excelai.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
/** 中文：Spring 到 LangGraph Agent 服务的 HTTP 适配器。English: HTTP adapter from Spring task workers to the LangGraph Agent service. */
public class AgentService {
    private final RestClient client;

    public AgentService(@Value("${agent.service.url}") String url) {
        client = RestClient.builder().baseUrl(url).build();
    }

    public String execute(Long userId, Long taskId, Long fileId, String prompt, int maxCalls) {
        // 中文：只发送任务标识和提示词，不发送本地文件路径；工具端通过已校验的 fileId 读取文件。
        // English: Send task identifiers and the prompt, never a local path; Java tools resolve the file through the validated file ID.
        Map<?, ?> response = client.post().uri("/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", prompt, "context", Map.of("userId", userId, "taskId", taskId, "inputFileId", fileId, "maxToolCalls", maxCalls)))
                .retrieve().body(Map.class);
        if (response == null || response.get("answer") == null)
            throw new IllegalStateException("Agent returned an empty response");
        return String.valueOf(response.get("answer"));
    }
}
