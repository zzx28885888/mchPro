package com.excelai.common;

import com.excelai.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitService rateLimits;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimitService rateLimits, RateLimitProperties properties) {
        this.rateLimits = rateLimits;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod)) return true;

        String path = request.getServletPath();
        if ("/api/auth/register".equals(path)) {
            rateLimits.enforce("registration", request.getRemoteAddr(), properties.registration());
        } else if ("/api/auth/login".equals(path)) {
            rateLimits.enforce("login", request.getRemoteAddr(), properties.login());
        } else if ("/api/tasks".equals(path) || "/api/tasks/preview".equals(path)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Object principal = authentication == null ? null : authentication.getPrincipal();
            if (!(principal instanceof Number userId)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
            }
            rateLimits.enforce("tasks", userId.toString(), properties.tasks());
        }
        return true;
    }
}
