package com.ragkb.service.application.impl;

import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.application.SearchChatService;
import com.ragkb.service.common.CursorPageData;
import com.ragkb.service.common.PageData;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatAskRequest;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatEvent;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatMessage;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatSession;
import com.ragkb.service.interfaces.dto.ChatDtos.ChatSessionCreateRequest;
import com.ragkb.service.interfaces.dto.ChatDtos.FeedbackRequest;
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
        return NotYetImplemented.stub("SearchChatService#search");
    }

    @Override
    public Object getSearchExcerpt(String hitId) {
        return NotYetImplemented.stub("SearchChatService#getSearchExcerpt");
    }

    @Override
    public PageData<ChatSession> listChatSessions(int page, int size) {
        return NotYetImplemented.stub("SearchChatService#listChatSessions");
    }

    @Override
    public ChatSession createChatSession(ChatSessionCreateRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("SearchChatService#createChatSession");
    }

    @Override
    public CursorPageData<ChatMessage> listChatMessages(long chatId, String cursor) {
        return NotYetImplemented.stub("SearchChatService#listChatMessages");
    }

    @Override
    public ChatSession archiveChatSession(long chatId) {
        return NotYetImplemented.stub("SearchChatService#archiveChatSession");
    }

    @Override
    public void deleteChatSession(long chatId) {
        NotYetImplemented.stub("SearchChatService#deleteChatSession");
    }

    @Override
    public void ask(long chatId, ChatAskRequest request, Consumer<ChatEvent> onEvent, String idempotencyKey) {
        NotYetImplemented.stub("SearchChatService#ask");
    }

    @Override
    public void submitFeedback(long messageId, FeedbackRequest request, String idempotencyKey) {
        NotYetImplemented.stub("SearchChatService#submitFeedback");
    }

    @Override
    public Object getMessageSource(long messageId, String sourceId) {
        return NotYetImplemented.stub("SearchChatService#getMessageSource");
    }
}
