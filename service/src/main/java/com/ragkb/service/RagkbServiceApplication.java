package com.ragkb.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 通用企业知识库平台 — 领域 API 服务入口。
 *
 * <p>当前为 v0.2 模块化单体骨架，仅提供健康检查与最小接口适配验证。
 * 业务代码按功能包组织，每个功能包内聚 Controller、DTO、Service
 * 和专属端口/适配器；未实现功能保留有明确方法名的 TODO 占位。</p>
 *
 * <p>{@code DataSourceAutoConfiguration} 被排除：数据源由 {@code DatabaseConfig}
 * 按 {@code ragkb.db.enabled} 开关条件创建（默认关闭 → 无数据库也能启动脚手架）。
 * 开启时 {@code spring.datasource.*} 生效，MyBatis-Plus 自动装配照常工作。</p>
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class RagkbServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagkbServiceApplication.class, args);
    }
}
