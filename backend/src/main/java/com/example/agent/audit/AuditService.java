package com.example.agent.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
/**
 * 中文：集中记录受控工具的调用结果、耗时与失败原因，便于开发排错和审计。
 * English: Centralizes tool-call outcomes, latency, and failure details for debugging and auditing.
 */
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public void record(String auditId, String principal, String tool,
                       String status, long start, String detail) {
        log.info("AGENT_TOOL_AUDIT auditId={} principal={} tool={} status={} durationMs={} detail={}",
                auditId, principal, tool, status,
                System.currentTimeMillis() - start, detail);
    }
}
