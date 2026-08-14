package com.ragkb.service;

import com.ragkb.service.modules.identity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 验证按功能重组包后，Controller、Service 与全局配置仍能被 Spring 正确扫描。
 */
@SpringBootTest(properties = {
        "ragkb.db.enabled=false",
        "ragkb.auth.mode=form"
})
class RagkbServiceApplicationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoads() {
        // SpringBootTest 成功启动即证明功能包扫描和 Bean 装配有效。

        String password = "123456";
        String encodedPassword = passwordEncoder.encode(password);
        System.out.println("123456的密文是:" + encodedPassword);
    }
}
