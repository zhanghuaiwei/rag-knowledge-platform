package com.ragkb.service.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 认证安全配置——按环境变量开关切换登录方式：
 *
 * <ul>
 *   <li>{@code RAGKB_AUTH_MODE=form}（默认，账号密码，本地账号体系）：登录判定来自数据库
 *       （{@code user_credential}/{@code sys_user}，见 V0.4 迁移）；仅当
 *       {@code RAGKB_DB_ENABLED=false}（脚手架无库兜底）时用内存 dev 用户
 *       （{@code RAGKB_DEV_USERNAME / RAGKB_DEV_PASSWORD / RAGKB_DEV_ROLES}）。
 *       成功后签发 access token（响应体，前端仅内存持有）+ refresh token
 *       （HttpOnly cookie {@code ragkb_refresh}，轮换 + Redis 黑名单）；请求经
 *       {@link JwtAuthenticationFilter} 校验 {@code Authorization: Bearer}。
 *       JWT 签发/校验与 Redis 命令为人工实现点（{@code TokenServiceImpl} 与两个 Adapter）。
 *       ⚠️ 凭据策略（失败锁定/密码过期）接线为人工实现点。</li>
 *   <li>{@code RAGKB_AUTH_MODE=oidc}（生产）：OIDC Authorization Code，由企业 IdP 承载认证，
 *       BFF 会话 cookie（JSESSIONID），配置见
 *       {@code RAGKB_OIDC_CLIENT_ID / RAGKB_OIDC_CLIENT_SECRET / RAGKB_OIDC_ISSUER_URI}。</li>
 * </ul>
 *
 * <p>鉴权未通过时 API 统一返回 JSON 401（{@code E-1001}）。
 *
 * <p>⚠️ 脚手架说明：CSRF 暂未启用。form 模式登录/刷新/登出端点为 permitAll +
 * HttpOnly cookie（SameSite=Lax，跨站 POST 不携带），CSRF 面小但存在；生产如需收紧可加
 * Origin 校验或 double-submit token，属人工实现点。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtTokenProperties.class, LocalAuthProperties.class})
public class SecurityConfig {

    /** 会话 cookie 存储（登录后 JSESSIONID 持久化 SecurityContext）。 */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 供 Controller 显式登录使用。 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /** API 未认证时返回 JSON 401（而非重定向到登录页）。 */
    @Bean
    public AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"E-1001\",\"message\":\"未认证或登录已过期\",\"data\":null}");
        };
    }

    // ---------- form 模式（开发默认）：账号密码登录 ----------

    @Bean
    @ConditionalOnProperty(name = "ragkb.auth.mode", havingValue = "form", matchIfMissing = true)
    public SecurityFilterChain formFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint entryPoint,
            JwtAuthenticationFilter jwtFilter,
            ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilterProvider,
            ObjectProvider<CredentialPolicyGateFilter> credentialPolicyGateProvider) throws Exception {
        http
                // 对所有请求开启CORS支持
                // 目的是让 CORS 处理先于认证逻辑执行：预检请求（OPTIONS）到达时直接返回 CORS 响应头，不会被后续的认证/鉴权拦截。

                // Customizer.withDefaults() 会自动找到容器中的 CorsConfigurationSource Bean 并应用,
                // Spring Boot 会把 WebMvcConfigurer 里注册的 CorsRegistry 自动转换成 CorsConfigurationSource Bean
                .cors(Customizer.withDefaults())  // 避免预检请求被认证拦截
                .csrf(csrf -> csrf.disable()) // 脚手架说明见类注释
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 容器内部再分发（SseEmitter 的 ASYNC 完成派发、completeWithError 的 ERROR 派发）
                        // 不是新的客户端请求：原始 REQUEST dispatch 已完成鉴权，OncePerRequestFilter
                        // 在再分发时跳过 JWT → SecurityContext 为空，若不放行会拦截已提交的 SSE 响应，
                        // 连接被掐断（客户端 ERR_INCOMPLETE_CHUNKED_ENCODING / 真实错误被掩成 401）。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/ping", "/actuator/**", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable()) // 登出由 AuthController 显式处理
                // API Key 过滤（db.enabled=true 时挂载）须在 JWT 过滤之前：rk_ 前缀分流，不把 API Key 交给 JWT Parser
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        apiKeyFilterProvider.ifAvailable(apiKeyFilter ->
                http.addFilterBefore(apiKeyFilter, JwtAuthenticationFilter.class));
        // 本地凭据策略门禁（db+form 时挂载）在 JWT 认证之后：重读凭据，强制首登改密/过期改密
        credentialPolicyGateProvider.ifAvailable(gateFilter ->
                http.addFilterAfter(gateFilter, JwtAuthenticationFilter.class));
        return http.build();
    }


    /** 脚手架无数据库兜底用的内存用户（db.enabled=false 且 mode=form 时生效；启用 DB 后由
     *  {@code JdbcUserDetailsService} 从 user_credential 接管，登录判定落库）。 */
    @Bean
    @Conditional(IdentityConditions.NoDbFormMode.class)
    public UserDetailsService devUserDetailsService(
            @Value("${ragkb.auth.dev.username:admin}") String username,
            @Value("${ragkb.auth.dev.password:admin123}") String password,
            @Value("${ragkb.auth.dev.roles:TENANT_ADMIN}") String roles,
            PasswordEncoder passwordEncoder) {
        // 仅无数据库时兜底：数据库用户体系见 JdbcUserDetailsService + user_credential（V0.4）
        UserDetails devUser = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles(roles.split(","))
                .build();
        return new InMemoryUserDetailsManager(devUser);
    }

    @Bean
    @ConditionalOnProperty(name = "ragkb.auth.mode", havingValue = "form", matchIfMissing = true)
    public PasswordEncoder devPasswordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    // ---------- oidc 模式（生产）：企业 IdP 认证 ----------

    /**
     * OIDC 客户端注册（仅 oidc 模式）：由 issuer-uri 解析 OIDC 发现文档得到各端点地址。
     * 注意：不能走 spring.security.oauth2.client.* 属性（空值时 form 模式启动也会校验失败）。
     */
    @Bean
    @ConditionalOnProperty(name = "ragkb.auth.mode", havingValue = "oidc")
    public ClientRegistrationRepository oidcClientRegistrationRepository(
            @Value("${ragkb.auth.oidc.client-id:}") String clientId,
            @Value("${ragkb.auth.oidc.client-secret:}") String clientSecret,
            @Value("${ragkb.auth.oidc.issuer-uri:}") String issuerUri) {
        if (issuerUri.isBlank() || clientId.isBlank()) {
            throw new IllegalStateException(
                    "OIDC 模式需要配置 RAGKB_OIDC_ISSUER_URI 与 RAGKB_OIDC_CLIENT_ID");
        }
        ClientRegistration registration = ClientRegistrations.fromIssuerLocation(issuerUri)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @ConditionalOnProperty(name = "ragkb.auth.mode", havingValue = "oidc")
    public SecurityFilterChain oidcFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint entryPoint,
            SecurityContextRepository contextRepository) throws Exception {
        http
                .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
                .csrf(csrf -> csrf.disable()) // 脚手架说明见类注释
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/authorize", "/api/v1/ping", "/actuator/**",
                                "/oauth2/**", "/login/**", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .oauth2Login(oauth -> oauth
                        .loginPage("/api/v1/auth/authorize")
                        .defaultSuccessUrl("/", true));
        return http.build();
    }
}
