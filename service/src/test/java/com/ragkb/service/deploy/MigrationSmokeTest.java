package com.ragkb.service.deploy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.4 迁移文件冒烟守卫：幂等写法与 DBeaver 兼容性（仓库约定：迁移纯 SQL、不含 psql 元命令、
 * 按连接角色直接执行不 SET ROLE）。
 */
class MigrationSmokeTest {

    private static final String MIGRATION_NAME = "V0.4__local_user_credentials.sql";

    @Test
    void migrationIsIdempotentAndDBeaverSafe() throws IOException {
        String sql = readMigration();
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS user_credential"), "建表必须幂等");
        assertTrue(sql.contains("uq_user_credential_username"), "登录标识唯一约束");
        assertTrue(sql.contains("BEGIN;") && sql.contains("COMMIT;"), "单事务包裹");
        // DBeaver 兼容守卫：不含 psql 元命令 / 变量占位 / 实际 SET ROLE 语句
        // （注释里可以出现 "SET ROLE" 字样，故按"语句行首"判断真实语句）
        assertFalse(sql.contains("\\set"), "不得含 psql \\set 元命令");
        assertFalse(sql.contains(":'"), "不得含 psql 变量占位 :'var'");
        boolean hasSetRoleStatement = sql.lines()
                .anyMatch(line -> line.stripLeading().startsWith("SET ROLE"));
        assertFalse(hasSetRoleStatement, "按连接角色直接执行，内部不切换角色");
    }

    @Test
    void migrationSeedsBootstrapAdminIdempotently() throws IOException {
        String sql = readMigration();
        assertTrue(sql.contains("ON CONFLICT (id) DO NOTHING"), "sys_user 按 id 幂等");
        assertTrue(sql.contains("WHERE NOT EXISTS") && sql.contains("WHERE lower(username) = 'admin'"),
                "user_credential 按登录标识（lower(username)）条件插入幂等");
        assertTrue(sql.contains("Bootstrap Admin"), "bootstrap 管理员存在");
        assertTrue(sql.contains("TENANT_ADMIN"), "bootstrap 角色为租户管理员");
    }

    private String readMigration() throws IOException {
        // mvn 测试工作目录为 service/，迁移在仓库根 deploy/ddl/migrations/；从 user.dir 向上逐级查找
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 6; i++) {
            Path candidate = dir.resolve("deploy").resolve("ddl").resolve("migrations").resolve(MIGRATION_NAME);
            if (Files.exists(candidate)) {
                return Files.readString(candidate);
            }
            Path parent = dir.getParent();
            if (parent == null) {
                break;
            }
            dir = parent;
        }
        throw new IllegalStateException("找不到迁移文件 deploy/ddl/migrations/" + MIGRATION_NAME);
    }
}
