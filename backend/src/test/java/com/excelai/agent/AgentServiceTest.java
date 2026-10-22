package com.excelai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** 中文/English：验证 Agent 请求关联和用量 DTO 反序列化 / Verifies Agent request linkage and usage DTO deserialization. */
class AgentServiceTest {
    @Test
    void sendsExecutionIdAndParsesNullableUsageFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentService service = new AgentService(builder.build(), "test-token");
        UUID executionId = UUID.fromString("b6db8fca-ae06-4d1b-a67b-17f4c6c6d810");
        server.expect(requestTo("http://agent.test/agent/chat"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Agent-Token", "test-token"))
                .andExpect(jsonPath("$.execution_id").value(executionId.toString()))
                .andExpect(jsonPath("$.context.taskId").value(42))
                .andRespond(withSuccess("""
                        {"answer":"finished","usage":{"provider_code":"deepseek","model_name":"deepseek-flash",
                        "input_tokens":120,"output_tokens":30,"cached_input_tokens":null,
                        "model_call_count":2,"llm_duration_ms":875,"measurement_status":"PARTIAL"}}
                        """, MediaType.APPLICATION_JSON));

        AgentResult result = service.execute(executionId, 7L, 42L, List.of(81L), "FREEFORM", Map.of(), "synthetic prompt", 8);

        assertEquals("finished", result.answer());
        assertEquals("deepseek", result.usage().providerCode());
        assertEquals("deepseek-flash", result.usage().modelName());
        assertEquals(120L, result.usage().inputTokens());
        assertEquals(30L, result.usage().outputTokens());
        assertNull(result.usage().cachedInputTokens());
        assertEquals(2L, result.usage().modelCallCount());
        assertEquals("PARTIAL", result.usage().measurementStatus());
        server.verify();
    }

    @Test
    void propagatesAgentHttpFailureWithoutLoggingResponseBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://agent.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentService service = new AgentService(builder.build(), "test-token");
        server.expect(requestTo("http://agent.test/agent/chat"))
                .andRespond(withServerError().body("synthetic private response"));

        assertThrows(RuntimeException.class, () -> service.execute(
                UUID.randomUUID(), 7L, 42L, List.of(81L), "FREEFORM", Map.of(), "synthetic prompt", 8));
        server.verify();
    }
}
