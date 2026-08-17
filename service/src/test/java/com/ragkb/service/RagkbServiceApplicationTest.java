package com.ragkb.service;

import com.ragkb.service.modules.document.service.DocumentService;
import com.ragkb.service.modules.knowledge.service.KbService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 验证按功能重组包后，Controller、Service 与全局配置仍能被 Spring 正确扫描。
 *
 * <p>以 {@code ragkb.db.enabled=false} 运行（免库冒烟）：Kb / Document 为真实 DB 实现
 * （注入 MyBatis Mapper），在免库上下文中用 {@link MockitoBean} 替换，其余 Bean 装配照常校验。
 */
@SpringBootTest(properties = {
        "ragkb.db.enabled=false",
        "ragkb.auth.mode=form"
})
class RagkbServiceApplicationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private KbService kbService;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void contextLoads() {
        // SpringBootTest 成功启动即证明功能包扫描和 Bean 装配有效。

        String password = "123456";
        String encodedPassword = passwordEncoder.encode(password);
        System.out.println("123456的密文是:" + encodedPassword);
    }
}
