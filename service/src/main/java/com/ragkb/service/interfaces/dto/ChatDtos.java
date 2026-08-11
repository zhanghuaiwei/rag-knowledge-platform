package com.ragkb.service.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 智能问答域 DTO（对齐前端 Chat 契约；服务端提问走 SSE 事件流）。
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatSession(
            long id,
            String title,
            String status,
            List<Long> kbIds,
            long messageCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ChatSessionCreateRequest(
            @NotEmpty @Size(max = 5) List<@NotNull Long> kbIds,
            @Size(max = 128) String title) {
    }

    /** 提问入参（OpenAPI ChatAskRequest）。 */
    public record ChatAskRequest(
            @NotBlank @Size(max = 4096) String question,
            Integer memoryTurns,
            Boolean stream) {
    }

    public record ChatSource(
            String chunkId,
            long documentId,
            String fileName,
            int pageNo,
            String sectionTitle,
            double score) {
    }

    public record ChatMessage(
            long id,
            long sessionId,
            long seq,
            String role,
            String content,
            String answerStatus,
            Double confidence,
            int feedback,
            long tokenIn,
            long tokenOut,
            String modelName,
            List<ChatSource> sources,
            List<String> suggestions,
            Instant createdAt) {
    }

    /** 单条 SSE 事件（OpenAPI 约定：meta / token / sources / final / error）。 */
    public record ChatEvent(String type, Object data) {
    }

    public record FeedbackRequest(
            @NotBlank String reaction,
            @Size(max = 2048) String reason) {
    }
}
