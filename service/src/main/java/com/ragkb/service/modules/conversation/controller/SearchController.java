package com.ragkb.service.modules.conversation.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.api.CursorPageData;
import com.ragkb.service.modules.conversation.service.SearchChatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class SearchController {

    private final SearchChatService searchChatService;

    public SearchController(SearchChatService searchChatService) {
        this.searchChatService = searchChatService;
    }

    @GetMapping("")
    public ApiResponse<CursorPageData<?>> search(
            @RequestParam String q,
            @RequestParam(required = false) List<Long> kbIds,
            @RequestParam(required = false) List<String> fileExts,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(searchChatService.search(q, kbIds, fileExts, dateFrom, dateTo, cursor, size));
    }

    @GetMapping("/hits/{hitId}/excerpt")
    public ApiResponse<Object> getSearchExcerpt(@PathVariable String hitId) {
        return ApiResponse.ok(searchChatService.getSearchExcerpt(hitId));
    }
}
