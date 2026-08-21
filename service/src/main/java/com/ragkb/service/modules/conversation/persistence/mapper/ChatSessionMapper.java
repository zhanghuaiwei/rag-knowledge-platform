package com.ragkb.service.modules.conversation.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ragkb.service.modules.conversation.persistence.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * {@code chat_session} 表 Mapper —— MyBatis-Plus 骨架模板。
 *
 * <p>{@link BaseMapper} 已内置单表 CRUD 与分页（selectPage/selectById/insert/updateById/...）；
 * 复杂查询按需在 {@code resources/mapper/ChatSessionMapper.xml} 写 XML，或在方法上加 {@code @Select}。
 *
 * <p>⚠️ 模板：复制本接口改名即可（{@code @MapperScan} 已覆盖本包，{@code @Mapper} 可省略，
 * 保留以便单独使用）。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 逻辑删除会话：一条 UPDATE 同时置 {@code status='DELETED'} 与 {@code del_flag=1}。
     *
     * <p>不能拆两步也不能只置 {@code del_flag}：V0.3 后 {@code ck_chat_session_del_flag}
     * 要求 {@code del_flag=1 ⟺ status='DELETED'}，任何中间态（如 status='DELETED', del_flag=0）
     * 都违反 CHECK；MyBatis-Plus {@code deleteById} 只置 del_flag，同样违反。
     */
    @Update("UPDATE chat_session SET status = 'DELETED', del_flag = 1, update_time = now() "
            + "WHERE id = #{chatId} AND del_flag = 0")
    int markDeleted(@Param("chatId") long chatId);
}
