package com.ragkb.service.modules.identity.vo;

/**
 * 创建/轮换 API Key 结果视图：明文 secret 只出现一次。
 */
public record ApiKeyCreatedVo(ApiKeyVo key, String secret) {
}
