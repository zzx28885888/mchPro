package com.excelai.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
/**
 * 中文：从 Authorization: Bearer 读取 JWT，校验后把用户 ID 放入 Spring Security 身份对象。
 * English: Reads Authorization: Bearer JWTs and places the verified user ID in Spring Security's Authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService j) {
        jwt = j;
    }

    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        // 中文：无效或过期令牌按未登录处理，后续由 SecurityConfig 对受保护路由拒绝。
        // English: Invalid or expired tokens are treated as anonymous; SecurityConfig rejects protected routes afterward.
        if (h != null && h.startsWith("Bearer ")) try {
            Long id = jwt.userId(h.substring(7));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id, null, List.of()));
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(req, res);
    }
}
