package com.ragkb.service.modules.conversation.service;

import com.ragkb.service.common.api.CursorPageData;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.conversation.dto.ChatAskDto;
import com.ragkb.service.modules.conversation.vo.ChatEventVo;
import com.ragkb.service.modules.conversation.vo.ChatMessageVo;
import com.ragkb.service.modules.conversation.vo.ChatSessionVo;
import com.ragkb.service.modules.conversation.dto.ChatSessionCreateDto;
import com.ragkb.service.modules.conversation.dto.FeedbackDto;

import java.util.List;
import java.util.function.Consumer;

/**
 * 搜索与智能问答用例（实现点由人工完成；问答经 RagEnginePort 走 rag-engine 检索增强）。
 */
public interface SearchChatService {

    CursorPageData<?> search(String keyword, List<Long> kbIds, List<String> fileExts,
                             String dateFrom, String dateTo, String cursor, int size);

    Object getSearchExcerpt(String hitId);

    PageData<ChatSessionVo> listChatSessions(int page, int size);

    ChatSessionVo createChatSession(ChatSessionCreateDto request, String idempotencyKey);

    CursorPageData<ChatMessageVo> listChatMessages(long chatId, String cursor);

    /** 归档会话（产品契约所需；OpenAPI 草案仅定义删除）。 */
    ChatSessionVo archiveChatSession(long chatId);

    void deleteChatSession(long chatId);

    /** SSE 提问：应用层逐条回调事件，控制器负责写事件流。 */
    void ask(long chatId, ChatAskDto request, Consumer<ChatEventVo> onEvent, String idempotencyKey);

    void submitFeedback(long messageId, FeedbackDto request, String idempotencyKey);

    Object getMessageSource(long messageId, String sourceId);
}
