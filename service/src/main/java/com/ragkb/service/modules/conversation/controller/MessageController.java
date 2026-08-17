package com.ragkb.service.modules.conversation.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.conversation.dto.FeedbackDto;
import com.ragkb.service.modules.conversation.service.SearchChatService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class MessageController {

    private final SearchChatService searchChatService;

    public MessageController(SearchChatService searchChatService) {
        this.searchChatService = searchChatService;
    }

    @PostMapping("/{messageId}/feedback")
    public ResponseEntity<Void> submitFeedbackByMessage(
            @PathVariable long messageId,
            @Valid @RequestBody FeedbackDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        searchChatService.submitFeedback(messageId, request, idempotencyKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{messageId}/sources/{sourceId}")
    public ApiResponse<Object> getMessageSource(@PathVariable long messageId, @PathVariable String sourceId) {
        return ApiResponse.ok(searchChatService.getMessageSource(messageId, sourceId));
    }
}
