package com.ragkb.service.modules.knowledge.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.knowledge.dto.CloneKbDto;
import com.ragkb.service.modules.knowledge.vo.IndexBuildVo;
import com.ragkb.service.modules.knowledge.vo.KbVo;
import com.ragkb.service.modules.knowledge.dto.KbCreateDto;
import com.ragkb.service.modules.knowledge.vo.KbMemberVo;
import com.ragkb.service.modules.knowledge.dto.KbMemberDto;
import com.ragkb.service.modules.knowledge.dto.KbUpdateDto;
import com.ragkb.service.modules.knowledge.service.KbService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接口入口（CRUD / 成员 / 索引构建）。业务实现见 {@link KbService}。
 */
@RestController
public class KbController {

    private final KbService kbService;

    public KbController(KbService kbService) {
        this.kbService = kbService;
    }

    @GetMapping("/api/v1/kbs")
    public ApiResponse<PageData<KbVo>> listKbs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(kbService.listKbs(page, size));
    }

    @PostMapping("/api/v1/kbs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KbVo> createKb(
            @Valid @RequestBody KbCreateDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(kbService.createKb(request, idempotencyKey));
    }

    @GetMapping("/api/v1/kbs/{kbId}")
    public ApiResponse<KbVo> getKb(@PathVariable long kbId) {
        return ApiResponse.ok(kbService.getKb(kbId));
    }

    @PatchMapping("/api/v1/kbs/{kbId}")
    public ApiResponse<KbVo> updateKb(
            @PathVariable long kbId,
            @Valid @RequestBody KbUpdateDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(kbService.updateKb(kbId, request, idempotencyKey));
    }

    /** 归档（产品契约所需；OpenAPI 草案未单独定义，用独立动作端点避免混入 PATCH 语义）。 */
    @PostMapping("/api/v1/kbs/{kbId}/archive")
    public ApiResponse<KbVo> archiveKb(@PathVariable long kbId) {
        return ApiResponse.ok(kbService.archiveKb(kbId));
    }

    @DeleteMapping("/api/v1/kbs/{kbId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> deleteKb(
            @PathVariable long kbId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(kbService.deleteKb(kbId, idempotencyKey));
    }

    @PostMapping("/api/v1/kbs/{kbId}/clone")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> cloneKb(
            @PathVariable long kbId,
            @Valid @RequestBody(required = false) CloneKbDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CloneKbDto resolved = request == null ? new CloneKbDto(null) : request;
        return ApiResponse.ok(kbService.cloneKb(kbId, resolved, idempotencyKey));
    }

    @GetMapping("/api/v1/kbs/{kbId}/members")
    public ApiResponse<List<KbMemberVo>> listKbMembers(@PathVariable long kbId) {
        return ApiResponse.ok(kbService.listKbMembers(kbId));
    }

    @PostMapping("/api/v1/kbs/{kbId}/members")
    public ApiResponse<KbMemberVo> addOrUpdateKbMember(
            @PathVariable long kbId,
            @Valid @RequestBody KbMemberDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(kbService.addOrUpdateKbMember(kbId, request, idempotencyKey));
    }

    @DeleteMapping("/api/v1/kbs/{kbId}/members/{userId}")
    public ResponseEntity<Void> removeKbMember(@PathVariable long kbId, @PathVariable long userId) {
        kbService.removeKbMember(kbId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/kbs/{kbId}/index-builds")
    public ApiResponse<List<IndexBuildVo>> listIndexBuilds(
            @PathVariable long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(kbService.listIndexBuilds(kbId, page, size));
    }

    @PostMapping("/api/v1/kbs/{kbId}/index-builds")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> triggerIndexBuild(
            @PathVariable long kbId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(kbService.triggerIndexBuild(kbId, idempotencyKey));
    }

    @GetMapping("/api/v1/index-builds/{buildId}")
    public ApiResponse<IndexBuildVo> getIndexBuild(@PathVariable long buildId) {
        return ApiResponse.ok(kbService.getIndexBuild(buildId));
    }
}
