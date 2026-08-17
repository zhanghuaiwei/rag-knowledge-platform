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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/chats")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class ChatController {

    private final SearchChatService searchChatService;

    public ChatController(SearchChatService searchChatService) {
        this.searchChatService = searchChatService;
    }

    @GetMapping("")
    public ApiResponse<PageData<ChatSessionVo>> listChatSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(searchChatService.listChatSessions(page, size));
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatSessionVo> createChatSession(
            @Valid @RequestBody ChatSessionCreateDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(searchChatService.createChatSession(request, idempotencyKey));
    }

    @GetMapping("/{chatId}")
    public ApiResponse<CursorPageData<ChatMessageVo>> listChatMessages(
            @PathVariable long chatId,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.ok(searchChatService.listChatMessages(chatId, cursor));
    }

    @PostMapping("/{chatId}/archive")
    public ApiResponse<ChatSessionVo> archiveChatSession(@PathVariable long chatId) {
        return ApiResponse.ok(searchChatService.archiveChatSession(chatId));
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChatSession(@PathVariable long chatId) {
        searchChatService.deleteChatSession(chatId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{chatId}/messages", produces = "text/event-stream")
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

    @PostMapping("/{chatId}/messages/{messageId}/feedback")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable long chatId,
            @PathVariable long messageId,
            @Valid @RequestBody FeedbackDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        searchChatService.submitFeedback(messageId, request, idempotencyKey);
        return ResponseEntity.noContent().build();
    }
}
