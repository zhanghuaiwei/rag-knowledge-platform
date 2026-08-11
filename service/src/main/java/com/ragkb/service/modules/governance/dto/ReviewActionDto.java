package com.ragkb.service.modules.governance.dto;

import jakarta.validation.constraints.Size;

/**
 * 审核动作入参（F2.13）。
 */
public record ReviewActionDto(@Size(max = 2048) String comment) {
}
