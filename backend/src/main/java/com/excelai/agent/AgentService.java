package com.excelai.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
/** 中文：Spring 到 LangGraph Agent 服务的 HTTP 适配器。English: HTTP adapter from Spring task workers to LangGraph. */
public class AgentService {
    private final RestClient client;

    public AgentService(@Value("${agent.service.url}") String url) {
        client = RestClient.builder().baseUrl(url).build();
    }

    public String execute(Long userId, Long taskId, Long fileId, String prompt, int maxCalls) {
        return execute(userId, taskId, List.of(fileId), "FREEFORM", Map.of(), prompt, maxCalls);
    }

    public String execute(Long userId, Long taskId, List<Long> fileIds, String workflowType,
                          Map<String, Object> options, String prompt, int maxCalls) {
        // 中文：只传任务标识和文件 ID，不传本地路径；服务端校验后的上下文不由模型参数控制。
        // English: Send task identifiers and file IDs, never local paths; the model cannot control trusted context.
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userId", userId);
        context.put("taskId", taskId);
        context.put("inputFileId", fileIds.get(0));
        context.put("inputFileIds", fileIds);
        context.put("workflowType", workflowType);
        context.put("options", options);
        context.put("maxToolCalls", maxCalls);
        Map<?, ?> response = client.post().uri("/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", prompt, "context", context)).retrieve().body(Map.class);
        if (response == null || response.get("answer") == null)
            throw new IllegalStateException("Agent returned an empty response");
        return String.valueOf(response.get("answer"));
    }
}