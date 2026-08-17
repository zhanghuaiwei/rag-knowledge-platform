package com.ragkb.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 全局 Web 配置。
 * Spring Boot 会把 WebMvcConfigurer 里注册的 CorsRegistry 自动转换成 CorsConfigurationSource Bean,
 * 供Security的cors使用
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
                // OPTIONS: 预检请求
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "Range", "Content-Range")
                .allowCredentials(true)
                // 缓存预检结果1 小时, 缓存有效期内，浏览器不再发 OPTIONS 预检，直接发真实请求
                .maxAge(3600);
    }
}
