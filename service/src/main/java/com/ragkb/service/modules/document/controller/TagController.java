package com.ragkb.service.modules.document.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.document.vo.TagVo;
import com.ragkb.service.modules.document.dto.CreateTagDto;
import com.ragkb.service.modules.document.service.DocumentService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 装配条件：标签依赖数据库持久化（ragkb.db.enabled=true），scaffold 模式下端点整体下线。
@RestController
@RequestMapping("/api/v1/tags")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class TagController {

    private final DocumentService documentService;

    public TagController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("")
    public ApiResponse<List<TagVo>> listTags() {
        return ApiResponse.ok(documentService.listTags());
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TagVo> createTag(
            @Valid @RequestBody CreateTagDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(documentService.createTag(request.name(), idempotencyKey));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable long tagId) {
        documentService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }
}
