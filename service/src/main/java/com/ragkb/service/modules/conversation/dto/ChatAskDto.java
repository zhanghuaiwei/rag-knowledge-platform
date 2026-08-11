package com.ragkb.service.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提问入参（OpenAPI ChatAskDto）。
 */
public record ChatAskDto(
        @NotBlank @Size(max = 4096) String question,
        Integer memoryTurns,
        Boolean stream) {
}
