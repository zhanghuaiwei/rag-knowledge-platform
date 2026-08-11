package com.ragkb.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 全局 Web 配置。
 *
 * <p>CORS：前端（默认 localhost:3000）直连后端时需要携带会话 cookie（withCredentials）。
 * 允许来源经环境变量 {@code RAGKB_CORS_ALLOWED_ORIGINS} 配置（逗号分隔），禁止硬编码。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${ragkb.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "Range", "Content-Range")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
