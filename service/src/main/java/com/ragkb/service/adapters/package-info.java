/**
 * 适配器层：PostgreSQL、Redis、S3、SearchIndex、IdP、连接器与模型 SDK 的具体实现。
 * 负责协议翻译、超时、重试、熔断与 provider capability；不包含业务逻辑（03-详细设计 §1.1）。
 */
package com.ragkb.service.adapters;
