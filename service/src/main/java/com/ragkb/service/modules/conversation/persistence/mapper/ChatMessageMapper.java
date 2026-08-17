package com.ragkb.service.modules.conversation.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.conversation.persistence.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code chat_message} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/ChatMessageMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /** 会话下一条消息序号（chat_message.seq 唯一约束：tenant_id + session_id + seq）。 */
    @Select("SELECT COALESCE(MAX(seq), 0) + 1 FROM chat_message WHERE session_id = #{sessionId}")
    int nextSeq(@Param("sessionId") Long sessionId);
}
