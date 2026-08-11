package com.ragkb.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证按功能重组包后，Controller、Service 与全局配置仍能被 Spring 正确扫描。
 */
@SpringBootTest(properties = {
        "ragkb.db.enabled=false",
        "ragkb.auth.mode=form"
})
class RagkbServiceApplicationTest {

    @Test
    void contextLoads() {
        // SpringBootTest 成功启动即证明功能包扫描和 Bean 装配有效。
    }
}
