package com.ragkb.service.modules.connector.vo;

/**
 * 连接器配置校验结果响应视图。
 */
public record ConnectorValidateResultVo(boolean ok, String message) {
}
