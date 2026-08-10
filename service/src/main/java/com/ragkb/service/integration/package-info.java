/**
 * 外部集成域：scoped API Key、Webhook 签名投递、死信与重放。
 * Webhook URL 需 SSRF 防护；secret 只存 secret_ref（03-详细设计 §10）。
 */
package com.ragkb.service.integration;
