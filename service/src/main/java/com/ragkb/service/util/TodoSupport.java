package com.ragkb.service.util;

/**
 * 脚手架阶段的 TODO 占位工具。
 *
 * <p>接口入口已就绪；业务实现由人工按模块完成。完成一个方法后替换对应桩实现即可，
 * 无需改动 Controller 与契约。
 */
public final class TodoSupport {

    private TodoSupport() {
    }

    /**
     * 抛出带签名信息的 {@link UnsupportedOperationException}，由 GlobalExceptionHandler
     * 统一映射为 501。泛型返回使该方法可用于任意返回类型的桩方法。
     */
    public static <T> T notImplemented(String signature) {
        throw new UnsupportedOperationException("TODO: 人工实现 " + signature);
    }
}
