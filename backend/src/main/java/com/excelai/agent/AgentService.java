package com.excelai.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.UUID;

@Service
/** 中文：Spring 到 LangGraph Agent 服务的 HTTP 适配器。English: HTTP adapter from Spring task workers to LangGraph. */
public class AgentService {
    private final RestClient client;
    private final String internalToken;

    @Autowired
    public AgentService(@Value("${agent.service.url}") String url,
                        @Value("${agent.internal-token}") String internalToken) {
        this(RestClient.builder().baseUrl(url).build(), internalToken);
    }

    AgentService(RestClient client, String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    public AgentResult execute(UUID executionId, Long userId, Long taskId, List<Long> fileIds, String workflowType,
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
                .header("X-Agent-Token", internalToken)
                .body(Map.of("message", prompt, "execution_id", executionId.toString(), "context", context))
                .retrieve().body(Map.class);
        if (response == null || response.get("answer") == null)
            throw new IllegalStateException("Agent returned an empty response");
        AgentUsageSummary usage = null;
        if (response.get("usage") instanceof Map<?, ?> usageMap) {
            usage = new AgentUsageSummary(
                    stringValue(usageMap.get("provider_code")),
                    stringValue(usageMap.get("model_name")),
                    longValue(usageMap.get("input_tokens")),
                    longValue(usageMap.get("output_tokens")),
                    longValue(usageMap.get("cached_input_tokens")),
                    longValue(usageMap.get("model_call_count")),
                    longValue(usageMap.get("llm_duration_ms")),
                    stringValue(usageMap.get("measurement_status")));
        }
        return new AgentResult(String.valueOf(response.get("answer")), usage);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
