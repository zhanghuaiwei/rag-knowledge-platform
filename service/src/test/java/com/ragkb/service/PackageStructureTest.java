package com.ragkb.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保护模块化单体的包边界，避免后续开发重新退化为全局技术分层或空包占位。
 */
class PackageStructureTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java");
    private static final Path BASE_PACKAGE = JAVA_ROOT.resolve("com/ragkb/service");
    private static final Set<String> ALLOWED_ROOT_PACKAGES = Set.of(
            "common", "config", "health", "modules", "util"
    );
    private static final Set<String> ALLOWED_MODULE_LAYERS = Set.of(
            "adapter", "controller", "domain", "dto", "persistence", "port", "service", "vo"
    );
    private static final Pattern NAMED_TODO = Pattern.compile(
            "TodoSupport\\.notImplemented\\(\"[A-Za-z][A-Za-z0-9]*#[A-Za-z][A-Za-z0-9]*\"\\)"
    );
    private static final Pattern MODULE_IMPORT = Pattern.compile(
            "import com\\.ragkb\\.service\\.modules\\.([a-z0-9]+)\\.([a-z0-9]+)\\.[^;]+;"
    );

    @Test
    void packageDeclarationsMatchDirectories() throws IOException {
        for (Path source : javaSources()) {
            String relativePackage = JAVA_ROOT.relativize(source.getParent()).toString().replace('/', '.');
            String content = Files.readString(source);
            assertTrue(content.startsWith("package " + relativePackage + ";"),
                    () -> source + " 的 package 声明与目录不一致");
        }
    }

    @Test
    void businessCodeLivesUnderFeatureModules() throws IOException {
        try (Stream<Path> children = Files.list(BASE_PACKAGE)) {
            List<String> unexpectedDirectories = children
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !ALLOWED_ROOT_PACKAGES.contains(name))
                    .sorted()
                    .toList();
            assertTrue(unexpectedDirectories.isEmpty(),
                    () -> "业务包必须位于 modules/<feature>，发现根级目录: " + unexpectedDirectories);
        }

        Path modules = BASE_PACKAGE.resolve("modules");
        for (Path source : javaSources(modules)) {
            Path relative = modules.relativize(source);
            assertTrue(relative.getNameCount() >= 3,
                    () -> source + " 至少应满足 modules/<feature>/<layer>/<Type>.java");
            assertTrue(ALLOWED_MODULE_LAYERS.contains(relative.getName(1).toString()),
                    () -> source + " 使用了未约定的模块内分层");
        }
    }

    @Test
    void sharedCodeDoesNotDependOnBusinessModules() throws IOException {
        for (Path source : javaSources(BASE_PACKAGE.resolve("common"))) {
            assertFalse(Files.readString(source).contains("com.ragkb.service.modules."),
                    () -> source + " 中的公共代码不应反向依赖业务模块");
        }
        for (Path source : javaSources(BASE_PACKAGE.resolve("util"))) {
            assertFalse(Files.readString(source).contains("com.ragkb.service.modules."),
                    () -> source + " 中的工具代码不应依赖业务模块");
        }
    }

    @Test
    void controllersDoNotDependOnImplementationsOrPersistence() throws IOException {
        for (Path source : javaSources(BASE_PACKAGE.resolve("modules"))) {
            if (!source.toString().contains("/controller/")) {
                continue;
            }
            String content = Files.readString(source);
            assertFalse(content.contains(".service.impl."),
                    () -> source + " 的 Controller 只能依赖 Service 接口");
            assertFalse(content.contains(".persistence."),
                    () -> source + " 的 Controller 不得直接访问持久化层");
        }
    }

    @Test
    void modulesOnlyUseOtherModulesThroughServiceOrPort() throws IOException {
        Path modules = BASE_PACKAGE.resolve("modules");
        for (Path source : javaSources(modules)) {
            String sourceFeature = modules.relativize(source).getName(0).toString();
            Matcher matcher = MODULE_IMPORT.matcher(Files.readString(source));
            while (matcher.find()) {
                String targetFeature = matcher.group(1);
                String targetLayer = matcher.group(2);
                if (!sourceFeature.equals(targetFeature)) {
                    assertTrue(Set.of("port", "service").contains(targetLayer),
                            () -> source + " 不得跨模块依赖 " + targetFeature + "/" + targetLayer
                                    + "，请通过目标模块的 Service/Port 协作");
                }
            }
        }
    }

    @Test
    void placeholdersHaveMethodNamesAndNoPackageInfoFiles() throws IOException {
        assertTrue(javaSources().stream().noneMatch(path -> path.endsWith("package-info.java")),
                "使用真实类型或带方法名的 TODO 占位，不使用 package-info.java 占空包");

        for (Path source : javaSources()) {
            String content = Files.readString(source);
            int todoCalls = content.split("TodoSupport\\.notImplemented\\(", -1).length - 1;
            int namedCalls = NAMED_TODO.matcher(content).results().toList().size();
            assertEquals(todoCalls, namedCalls,
                    () -> source + " 的 TODO 占位必须使用 Type#method 形式的方法名");
        }
    }

    private static List<Path> javaSources() throws IOException {
        return javaSources(BASE_PACKAGE);
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }
}
