package com.ragkb.service.modules.document.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.document.vo.FavoriteItemVo;
import com.ragkb.service.modules.document.dto.FavoriteDto;
import com.ragkb.service.modules.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final DocumentService documentService;

    public FavoriteController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("")
    public ApiResponse<PageData<FavoriteItemVo>> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(documentService.listFavorites(page, size));
    }

    @PostMapping("")
    public ResponseEntity<Void> addFavorite(@Valid @RequestBody FavoriteDto request) {
        documentService.setFavorite(request.documentId(), true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("")
    public ResponseEntity<Void> removeFavorite(@Valid @RequestBody FavoriteDto request) {
        documentService.setFavorite(request.documentId(), false);
        return ResponseEntity.noContent().build();
    }
}
