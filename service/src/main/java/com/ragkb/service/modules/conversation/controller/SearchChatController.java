package com.ragkb.service.modules.conversation.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.CursorPageData;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.conversation.dto.ChatAskDto;
import com.ragkb.service.modules.conversation.vo.ChatMessageVo;
import com.ragkb.service.modules.conversation.vo.ChatSessionVo;
import com.ragkb.service.modules.conversation.dto.ChatSessionCreateDto;
import com.ragkb.service.modules.conversation.dto.FeedbackDto;
import com.ragkb.service.modules.conversation.service.SearchChatService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 搜索与智能问答接口入口（全文搜索 / 问答 SSE）。业务实现见 {@link SearchChatService}。
 *
 * <p>依赖 chat_session 等 DB 表，随 {@code ragkb.db.enabled} 开关注册
 * （免库脚手架模式不挂载本控制器，与对应 Service 实现一致）。
 */
@RestController
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class SearchChatController {

    private final SearchChatService searchChatService;

    public SearchChatController(SearchChatService searchChatService) {
        this.searchChatService = searchChatService;
    }

    @GetMapping("/api/v1/search")
    public ApiResponse<CursorPageData<?>> search(
            @RequestParam String q,
            @RequestParam(required = false) List<Long> kbIds,
            @RequestParam(required = false) List<String> fileExts,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(searchChatService.search(q, kbIds, fileExts, dateFrom, dateTo, cursor, size));
    }

    @GetMapping("/api/v1/search/hits/{hitId}/excerpt")
    public ApiResponse<Object> getSearchExcerpt(@PathVariable String hitId) {
        return ApiResponse.ok(searchChatService.getSearchExcerpt(hitId));
    }

    @GetMapping("/api/v1/chats")
    public ApiResponse<PageData<ChatSessionVo>> listChatSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(searchChatService.listChatSessions(page, size));
    }

    @PostMapping("/api/v1/chats")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatSessionVo> createChatSession(
            @Valid @RequestBody ChatSessionCreateDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(searchChatService.createChatSession(request, idempotencyKey));
    }

    @GetMapping("/api/v1/chats/{chatId}")
    public ApiResponse<CursorPageData<ChatMessageVo>> listChatMessages(
            @PathVariable long chatId,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(searchChatService.listChatMessages(chatId, cursor));
    }

    /** 归档会话（产品契约所需）。 */
    @PostMapping("/api/v1/chats/{chatId}/archive")
    public ApiResponse<ChatSessionVo> archiveChatSession(@PathVariable long chatId) {
        return ApiResponse.ok(searchChatService.archiveChatSession(chatId));
    }

    @DeleteMapping("/api/v1/chats/{chatId}")
    public ResponseEntity<Void> deleteChatSession(@PathVariable long chatId) {
        searchChatService.deleteChatSession(chatId);
        return ResponseEntity.noContent().build();
    }

    /** 提问：SSE 事件流（meta → token* → sources? → final）。 */
    @PostMapping(value = "/api/v1/chats/{chatId}/messages", produces = "text/event-stream")
    public SseEmitter ask(
            @PathVariable long chatId,
            @Valid @RequestBody ChatAskDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            searchChatService.ask(chatId, request, event -> {
                try {
                    emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }, idempotencyKey);
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @PostMapping("/api/v1/chats/{chatId}/messages/{messageId}/feedback")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable long chatId,
            @PathVariable long messageId,
            @Valid @RequestBody FeedbackDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        searchChatService.submitFeedback(messageId, request, idempotencyKey);
        return ResponseEntity.noContent().build();
    }

    /** 按 messageId 反馈（产品契约所需；前端反馈入参不含会话 id）。 */
    @PostMapping("/api/v1/messages/{messageId}/feedback")
    public ResponseEntity<Void> submitFeedbackByMessage(
            @PathVariable long messageId,
            @Valid @RequestBody FeedbackDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        searchChatService.submitFeedback(messageId, request, idempotencyKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/messages/{messageId}/sources/{sourceId}")
    public ApiResponse<Object> getMessageSource(@PathVariable long messageId, @PathVariable String sourceId) {
        return ApiResponse.ok(searchChatService.getMessageSource(messageId, sourceId));
    }
}
