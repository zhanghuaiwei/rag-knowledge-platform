package com.ragkb.service.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 回答反馈入参。
 */
public record FeedbackDto(
        @NotBlank String reaction,
        @Size(max = 2048) String reason) {
}
