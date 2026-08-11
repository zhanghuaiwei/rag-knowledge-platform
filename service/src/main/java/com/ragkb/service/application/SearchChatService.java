package com.ragkb.service.application;

import com.ragkb.service.common.CursorPageData;
import com.ragkb.service.common.PageData;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatAskRequest;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatEvent;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatMessage;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatSession;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatSessionCreateRequest;
import com.ragkb.service.interfaces.dto.ChatDtos.FeedbackRequest;

import java.util.List;
import java.util.function.Consumer;

/**
 * 搜索与智能问答用例（实现点由人工完成；问答经 RagEnginePort 走 rag-engine 检索增强）。
 */
public interface SearchChatService {

    CursorPageData<?> search(String keyword, List<Long> kbIds, List<String> fileExts,
                             String dateFrom, String dateTo, String cursor, int size);

    Object getSearchExcerpt(String hitId);

    PageData<ChatSession> listChatSessions(int page, int size);

    ChatSession createChatSession(ChatSessionCreateRequest request, String idempotencyKey);

    CursorPageData<ChatMessage> listChatMessages(long chatId, String cursor);

    /** 归档会话（产品契约所需；OpenAPI 草案仅定义删除）。 */
    ChatSession archiveChatSession(long chatId);

    void deleteChatSession(long chatId);

    /** SSE 提问：应用层逐条回调事件，控制器负责写事件流。 */
    void ask(long chatId, ChatAskRequest request, Consumer<ChatEvent> onEvent, String idempotencyKey);

    void submitFeedback(long messageId, FeedbackRequest request, String idempotencyKey);

    Object getMessageSource(long messageId, String sourceId);
}
