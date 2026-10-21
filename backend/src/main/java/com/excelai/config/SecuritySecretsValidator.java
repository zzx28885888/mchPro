package com.excelai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 中文：阻止已知默认凭据和示例占位值进入运行环境。
 * English: Prevents known default credentials and example placeholders from reaching a running environment.
 */
@Component
public class SecuritySecretsValidator {
    private static final String PLACEHOLDER_PREFIX = "replace-with-";

    public SecuritySecretsValidator(
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${agent.internal-token}") String agentToken,
            AppProperties properties) {
        String normalizedDatabasePassword = databasePassword == null ? "" : databasePassword.strip();
        String normalizedAgentToken = agentToken == null ? "" : agentToken.strip();
        String normalizedJwtSecret = properties.jwt().secret() == null ? "" : properties.jwt().secret().strip();
        rejectPlaceholder("POSTGRES_PASSWORD", databasePassword, Set.of("agent_dev_password"));
        rejectPlaceholder("AGENT_INTERNAL_TOKEN", agentToken, Set.of("local-agent-token-change-me"));
        rejectPlaceholder("JWT_SECRET", properties.jwt().secret(),
                Set.of("local-development-jwt-secret-change-me-32-bytes-minimum"));
        if (normalizedDatabasePassword.equals(normalizedAgentToken)
                || normalizedDatabasePassword.equals(normalizedJwtSecret)
                || normalizedAgentToken.equals(normalizedJwtSecret)) {
            throw new IllegalStateException("POSTGRES_PASSWORD, AGENT_INTERNAL_TOKEN, and JWT_SECRET must be different");
        }
    }

    private void rejectPlaceholder(String name, String value, Set<String> legacyValues) {
        String normalized = value == null ? "" : value.trim();
        String normalizedLowercase = normalized.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || !normalized.equals(value)
                || normalizedLowercase.startsWith(PLACEHOLDER_PREFIX)
                || legacyValues.contains(normalizedLowercase)) {
            throw new IllegalStateException(name + " must be set to a non-placeholder secret");
        }
    }
}
