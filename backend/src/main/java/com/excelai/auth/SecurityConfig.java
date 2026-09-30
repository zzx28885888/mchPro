package com.excelai.auth;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
/**
 * 中文：配置无状态 API 安全策略。公开页面、注册登录、健康检查和内部工具路由单独放行；工具路由仍由共享令牌二次保护。
 * English: Configures stateless API security. The UI, auth, health, and internal tool routes are permitted here; internal tools still require their shared token.
 */
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity h, JwtAuthenticationFilter f) throws Exception {
        // 中文：不使用服务端 Session；前端每次请求都携带 Bearer JWT。
        // English: No server-side session is used; the frontend sends a Bearer JWT on each authenticated request.
        return h.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/", "/index.html", "/favicon.ico", "/api/auth/**", "/api/health", "/internal/agent/tools/**").permitAll().anyRequest().authenticated())
                .addFilterBefore(f, UsernamePasswordAuthenticationFilter.class).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
