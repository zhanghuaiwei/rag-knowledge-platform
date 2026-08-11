package com.ragkb.service.modules.conversation.service.impl;

import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.common.api.CursorPageData;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.conversation.dto.ChatAskDto;
import com.ragkb.service.modules.conversation.vo.ChatEventVo;
import com.ragkb.service.modules.conversation.vo.ChatMessageVo;
import com.ragkb.service.modules.conversation.vo.ChatSessionVo;
import com.ragkb.service.modules.conversation.dto.ChatSessionCreateDto;
import com.ragkb.service.modules.conversation.dto.FeedbackDto;
import com.ragkb.service.modules.conversation.service.SearchChatService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 搜索与问答桩实现（实现点由人工替换；问答经 RagEnginePort 对接 rag-engine）。
 */
@Service
public class SearchChatServiceImpl implements SearchChatService {

    @Override
    public CursorPageData<?> search(String keyword, List<Long> kbIds, List<String> fileExts,
                                    String dateFrom, String dateTo, String cursor, int size) {
        return TodoSupport.notImplemented("SearchChatService#search");
    }

    @Override
    public Object getSearchExcerpt(String hitId) {
        return TodoSupport.notImplemented("SearchChatService#getSearchExcerpt");
    }

    @Override
    public PageData<ChatSessionVo> listChatSessions(int page, int size) {
        return TodoSupport.notImplemented("SearchChatService#listChatSessions");
    }

    @Override
    public ChatSessionVo createChatSession(ChatSessionCreateDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("SearchChatService#createChatSession");
    }

    @Override
    public CursorPageData<ChatMessageVo> listChatMessages(long chatId, String cursor) {
        return TodoSupport.notImplemented("SearchChatService#listChatMessages");
    }

    @Override
    public ChatSessionVo archiveChatSession(long chatId) {
        return TodoSupport.notImplemented("SearchChatService#archiveChatSession");
    }

    @Override
    public void deleteChatSession(long chatId) {
        TodoSupport.notImplemented("SearchChatService#deleteChatSession");
    }

    @Override
    public void ask(long chatId, ChatAskDto request, Consumer<ChatEventVo> onEvent, String idempotencyKey) {
        TodoSupport.notImplemented("SearchChatService#ask");
    }

    @Override
    public void submitFeedback(long messageId, FeedbackDto request, String idempotencyKey) {
        TodoSupport.notImplemented("SearchChatService#submitFeedback");
    }

    @Override
    public Object getMessageSource(long messageId, String sourceId) {
        return TodoSupport.notImplemented("SearchChatService#getMessageSource");
    }
}
