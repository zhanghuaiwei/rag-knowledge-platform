package com.ragkb.service.modules.conversation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建问答会话入参。
 */
public record ChatSessionCreateDto(
        @NotEmpty @Size(max = 5) List<@NotNull Long> kbIds,
        @Size(max = 128) String title) {
}
