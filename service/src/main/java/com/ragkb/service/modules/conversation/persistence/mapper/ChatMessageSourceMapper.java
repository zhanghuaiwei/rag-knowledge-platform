package com.ragkb.service.modules.conversation.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.conversation.persistence.entity.ChatMessageSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code chat_message_source} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/ChatMessageSourceMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 *
 * <p>{@code insertWithJsonb}：实体 {@code locationJson} 为 {@code String}（骨架约定 JSONB 列映射
 * String），直接走 BaseMapper.insert 会把字符串当作 varchar 写入 JSONB 列而报类型错，故提供
 * 显式 {@code CAST(? AS jsonb)} 的 INSERT（与 OutboxEventMapper.insertWithJsonb 同模式）。
 */
@Mapper
public interface ChatMessageSourceMapper extends BaseMapper<ChatMessageSource> {

    /** 以 JSONB 强转方式插入引用来源（locationJson 必须是合法 JSON 对象字符串）。 */
    @Insert("""
            INSERT INTO chat_message_source (
                tenant_id, message_id, document_id, version_id, chunk_id,
                source_rank, score, location_json, cited_text_sha256
            ) VALUES (
                #{tenantId}, #{messageId}, #{documentId}, #{versionId}, #{chunkId},
                #{sourceRank}, #{score}, CAST(#{locationJson} AS jsonb), #{citedTextSha256}
            )
            """)
    int insertWithJsonb(ChatMessageSource source);
}
