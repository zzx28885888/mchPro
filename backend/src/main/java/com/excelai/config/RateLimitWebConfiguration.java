package com.excelai.config;

import com.excelai.common.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RateLimitWebConfiguration implements WebMvcConfigurer {
    private final RateLimitInterceptor interceptor;

    public RateLimitWebConfiguration(RateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/auth/**", "/api/tasks/**");
    }
}
