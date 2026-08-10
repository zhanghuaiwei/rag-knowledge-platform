/**
 * 索引域：index profile、全量/增量 build、数量/质量校验、alias 原子切换。
 * 数据库只保存逻辑信息，不保存向量；索引是可重建派生数据（05-技术选型 ADR-3）。
 */
package com.ragkb.service.indexing;
