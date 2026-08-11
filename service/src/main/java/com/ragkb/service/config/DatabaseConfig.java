package com.ragkb.service.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 数据访问骨架（MyBatis-Plus / PostgreSQL）。
 *
 * <p>由 {@code RAGKB_DB_ENABLED} 开关控制（默认 false）：关闭时本配置整体不生效，
 * service 无数据库也能启动（脚手架阶段）；开启后按 {@code spring.datasource.*} 连接
 * PostgreSQL，注册 MyBatis-Plus 拦截器，并扫描 Mapper 接口。
 *
 * <p>连接凭证一律走环境变量（{@code RAGKB_DB_URL / RAGKB_DB_USERNAME / RAGKB_DB_PASSWORD}），
 * 禁止硬编码。Schema 由 {@code deploy/ddl/init.sql} 人工执行，本配置不做启动建表。
 *
 * <p>⚠️ 骨架说明：本类只负责基础设施（连接池/分页/乐观锁/Mapper 扫描），
 * 不含任何业务逻辑。业务数据访问放在对应功能包内实现。
 */
@Configuration
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
@MapperScan(basePackages = "com.ragkb.service.modules", annotationClass = Mapper.class)
public class DatabaseConfig {

    /** Hikari 连接池。Schema 主键为数据库自增（IDENTITY），无需在应用侧生成。 */
    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setMinimumIdle(2);
        ds.setMaximumPoolSize(10);
        ds.setConnectionTimeout(30_000);
        return ds;
    }

    /**
     * MyBatis-Plus 拦截器：分页 + 乐观锁。
     *
     * <p>乐观锁：实体 {@code row_version} 字段标 {@code @Version}，更新时自动加 1，
     * 版本不符则更新 0 行（防并发覆盖）。多租户隔离由数据库 RLS 承担（deploy/ddl/init.sql 附录 A），
     * 应用层不引入 TenantLine 拦截器，避免与 RLS 双重约束冲突。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
