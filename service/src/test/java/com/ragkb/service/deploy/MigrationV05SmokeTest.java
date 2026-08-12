package com.ragkb.service.deploy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.5 迁移文件冒烟守卫：幂等写法与 DBeaver 兼容性，以及关键的
 * "约束→部分唯一索引" 迁移语义（仓库约定：迁移纯 SQL、不含 psql 元命令、按连接角色直接执行）。
 */
class MigrationV05SmokeTest {

    private static final String MIGRATION_NAME = "V0.5__tenant_accounts.sql";

    @Test
    void migrationAddsCredentialPolicyColumnAndPartialUniqueIndex() throws IOException {
        String sql = readMigration();
        assertTrue(sql.contains("must_change_password BOOLEAN NOT NULL DEFAULT false"), "首登强制改密列");
        assertTrue(sql.contains("DROP CONSTRAINT IF EXISTS uq_user_credential_username"),
                "须先幂等删除旧 UNIQUE 约束");
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_user_credential_username"),
                "以同名部分唯一索引重建");
        assertTrue(sql.contains("ON user_credential (lower(username)) WHERE del_flag = 0"),
                "部分唯一索引仅约束未逻辑删除行（支持删除后用户名复用）");
        assertTrue(sql.contains("ON CONFLICT (lower(username)) WHERE del_flag = 0 DO NOTHING"),
                "seed/upsert 必须改用部分索引推断语法");
        // 约束删除后不得再"使用" ON CONFLICT ON CONSTRAINT（头注释中的 gotcha 说明除外）
        boolean hasOnConstraintStatement = sql.lines()
                .anyMatch(line -> !line.stripLeading().startsWith("--")
                        && line.contains("ON CONFLICT ON CONSTRAINT"));
        assertFalse(hasOnConstraintStatement,
                "约束删除后不得再使用 ON CONFLICT ON CONSTRAINT（会导致 V0.4 重跑报错）");
    }

    @Test
    void migrationIsIdempotentAndDBeaverSafe() throws IOException {
        String sql = readMigration();
        assertTrue(sql.contains("BEGIN;") && sql.contains("COMMIT;"), "单事务包裹");
        assertFalse(sql.contains("\\set"), "不得含 psql \\set 元命令");
        assertFalse(sql.contains(":'"), "不得含 psql 变量占位 :'var'");
        boolean hasSetRoleStatement = sql.lines()
                .anyMatch(line -> line.stripLeading().startsWith("SET ROLE"));
        assertFalse(hasSetRoleStatement, "按连接角色直接执行，内部不切换角色");
    }

    @Test
    void headerDocumentsV04RerunCompatibility() throws IOException {
        String sql = readMigration();
        assertTrue(sql.contains("V0.4"), "头注释须说明与 V0.4 的关系");
        assertTrue(sql.contains("ON CONFLICT ON CONSTRAINT"), "头注释须写明 V0.4 的 ON CONFLICT gotcha");
        assertTrue(sql.contains("部分唯一索引"), "头注释须说明约束→部分唯一索引变更");
    }

    @Test
    void migrationKeepsBootstrapSeedIdempotent() throws IOException {
        String sql = readMigration();
        assertTrue(sql.contains("ON CONFLICT (id) DO NOTHING"), "sys_user 按 id 幂等");
        assertTrue(sql.contains("Bootstrap Admin"), "bootstrap 管理员存在");
        assertTrue(sql.contains("TENANT_ADMIN"), "bootstrap 角色为租户管理员");
    }

    private String readMigration() throws IOException {
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
