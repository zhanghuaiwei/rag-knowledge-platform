package com.ragkb.service.modules.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.api.CursorPageData;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.common.model.UserId;
import com.ragkb.service.common.security.SecurityUtils;
import com.ragkb.service.modules.access.service.AccessPolicyUseCase;
import com.ragkb.service.modules.conversation.dto.ChatAskDto;
import com.ragkb.service.modules.conversation.dto.ChatSessionCreateDto;
import com.ragkb.service.modules.conversation.dto.FeedbackDto;
import com.ragkb.service.modules.conversation.persistence.entity.ChatMessage;
import com.ragkb.service.modules.conversation.persistence.entity.ChatMessageSource;
import com.ragkb.service.modules.conversation.persistence.entity.ChatSession;
import com.ragkb.service.modules.conversation.persistence.entity.ChatSessionKb;
import com.ragkb.service.modules.conversation.persistence.mapper.ChatMessageMapper;
import com.ragkb.service.modules.conversation.persistence.mapper.ChatMessageSourceMapper;
import com.ragkb.service.modules.conversation.persistence.mapper.ChatSessionKbMapper;
import com.ragkb.service.modules.conversation.persistence.mapper.ChatSessionMapper;
import com.ragkb.service.modules.conversation.service.SearchChatService;
import com.ragkb.service.modules.conversation.vo.ChatEventVo;
import com.ragkb.service.modules.conversation.vo.ChatMessageVo;
import com.ragkb.service.modules.conversation.vo.ChatSessionVo;
import com.ragkb.service.modules.conversation.vo.ChatSourceVo;
import com.ragkb.service.modules.document.service.DocumentService;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.knowledge.service.KbService;
import com.ragkb.service.modules.rag.port.RagEnginePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 搜索与智能问答用例实现：会话 CRUD + 提问（SSE 转发 + 落库）。
 *
 * <p>提问链路（对齐前端 Chat 契约）：
 * <pre>
 *   前端 POST /chats/{id}/messages → 本类：
 *     ① 建 USER / ASSISTANT 两条消息（seq 递增）；
 *     ② 组装 rag-engine QueryChatRequest（kbIds / history / kbConfig / tenantId）→ chatStream；
 *     ③ 逐事件转发（meta/token/sources/final），meta/final 补 messageId，sources 补 fileName；
 *     ④ 结束后回填 ASSISTANT 内容/状态/置信度，并按 sources 落 chat_message_source。
 * </pre>
 * 未认证请求 fail-closed：{@link #currentUserId()} 与 {@link #requireTenantId()} 会直接抛
 * {@code UNAUTHORIZED}，绝不静默兜底种子租户/用户，避免跨租户串数据。
 */
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class SearchChatServiceImpl implements SearchChatService {

    private static final Logger log = LoggerFactory.getLogger(SearchChatServiceImpl.class);

    private static final int HISTORY_BATCH = 50;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatSessionKbMapper chatSessionKbMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageSourceMapper chatMessageSourceMapper;
    private final DocumentService documentService;
    private final RagEnginePort ragEnginePort;
    private final ObjectMapper objectMapper;
    private final KbService kbService;
    private final AccessPolicyUseCase accessPolicy;

    public SearchChatServiceImpl(ChatSessionMapper chatSessionMapper,
                                 ChatSessionKbMapper chatSessionKbMapper,
                                 ChatMessageMapper chatMessageMapper,
                                 ChatMessageSourceMapper chatMessageSourceMapper,
                                 DocumentService documentService,
                                 RagEnginePort ragEnginePort,
                                 ObjectMapper objectMapper,
                                 KbService kbService,
                                 AccessPolicyUseCase accessPolicy) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatSessionKbMapper = chatSessionKbMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageSourceMapper = chatMessageSourceMapper;
        this.documentService = documentService;
        this.ragEnginePort = ragEnginePort;
        this.objectMapper = objectMapper;
        this.kbService = kbService;
        this.accessPolicy = accessPolicy;
    }

    // =====================================================================
    // 会话
    // =====================================================================

    @Override
    public PageData<ChatSessionVo> listChatSessions(int page, int size) {
        IPage<ChatSession> sessionPage = chatSessionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, currentUserId())
                        .ne(ChatSession::getStatus, "DELETED")
                        .orderByDesc(ChatSession::getUpdateTime));
        if (sessionPage.getRecords().isEmpty()) {
            return PageData.empty(page, size);
        }
        return PageData.of(toSessionVos(sessionPage.getRecords()), sessionPage.getTotal(), page, size);
    }

    @Override
    public ChatSessionVo createChatSession(ChatSessionCreateDto request, String idempotencyKey) {
        long tenantId = requireTenantId();
        ChatSession session = new ChatSession();
        session.setTenantId(tenantId);
        session.setUserId(currentUserId());
        session.setTitle(request.title() == null || request.title().isBlank()
                ? "新会话" : request.title().trim());
        session.setStatus("ACTIVE");
        chatSessionMapper.insert(session);

        for (Long kbId : request.kbIds()) {
            ChatSessionKb link = new ChatSessionKb();
            link.setTenantId(tenantId);
            link.setSessionId(session.getId());
            link.setKbId(kbId);
            chatSessionKbMapper.insert(link);
        }
        return toSessionVo(session, request.kbIds(), 0L);
    }

    @Override
    public CursorPageData<ChatMessageVo> listChatMessages(long chatId, String cursor) {
        requireSession(chatId);
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, chatId)
                        .orderByAsc(ChatMessage::getSeq)
                        .last("LIMIT " + HISTORY_BATCH));
        Map<Long, List<ChatMessageSource>> sourcesByMessage = sourcesByMessageIds(
                messages.stream().map(ChatMessage::getId).toList());
        return CursorPageData.of(messages.stream()
                .map(message -> toMessageVo(message,
                        sourcesByMessage.getOrDefault(message.getId(), List.of())))
                .toList(), null, messages.size() >= HISTORY_BATCH);
    }

    @Override
    public ChatSessionVo archiveChatSession(long chatId) {
        ChatSession session = requireSession(chatId);
        session.setStatus("ARCHIVED");
        chatSessionMapper.updateById(session);
        return toSessionVo(session, kbIdsOf(chatId), messageCountOf(chatId));
    }

    @Override
    public void deleteChatSession(long chatId) {
        requireSession(chatId);
        // 逻辑删除必须与 status='DELETED' 同一条 UPDATE 原子提交：
        // ck_chat_session_del_flag 要求 del_flag=1 ⟺ status='DELETED'，
        // 仅置 del_flag（MP deleteById）或先置 status 再删都会出现违反 CHECK 的中间态。
        chatSessionMapper.markDeleted(chatId);
    }

    // =====================================================================
    // 提问（SSE）
    // =====================================================================

    @Override
    public void ask(long chatId, ChatAskDto request, Consumer<ChatEventVo> onEvent, String idempotencyKey) {
        ChatSession session = requireSession(chatId);
        long tenantId = session.getTenantId();
        List<Long> kbIds = kbIdsOf(chatId);

        int memoryTurns = request.memoryTurns() == null ? 10 : Math.max(request.memoryTurns(), 0);
        List<ChatMessage> history = loadRecentMessages(chatId, memoryTurns * 2);

        // ① 占位 USER / ASSISTANT 消息（流式过程中先落空内容，结束回填）。
        int nextSeq = chatMessageMapper.nextSeq(chatId);
        ChatMessage userMsg = new ChatMessage();
        userMsg.setTenantId(tenantId);
        userMsg.setSessionId(chatId);
        userMsg.setSeq(nextSeq);
        userMsg.setRole("USER");
        userMsg.setContent(request.question());
        chatMessageMapper.insert(userMsg);

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setTenantId(tenantId);
        assistantMsg.setSessionId(chatId);
        assistantMsg.setSeq(nextSeq + 1);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent("");
        assistantMsg.setAnswerStatus("ANSWERED");
        chatMessageMapper.insert(assistantMsg);
        long assistantMsgId = assistantMsg.getId();

        // ② 组装 rag-engine 请求并流式转发。
        String requestId = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey : UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("sessionId", chatId);
        payload.put("kbIds", kbIds);
        payload.put("question", request.question());
        // 过滤空 content 的历史（失败请求残留的空 ASSISTANT 占位）：
        // rag-engine ChatHistoryItem 要求 content min_length=1，透传空串会 422。
        payload.put("history", history.stream()
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .map(message -> Map.of(
                        "role", message.getRole().toLowerCase(),
                        "content", message.getContent()))
                .toList());
        payload.put("kbConfig", RagEnginePort.defaultKbConfig());

        StringBuilder answer = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<String, Object> finalPayload = new LinkedHashMap<>();
        List<String> modelName = new ArrayList<>();

        ragEnginePort.chatStream(new TenantId(tenantId), payload, event -> {
            Map<String, Object> data = asMap(event.data());
            if ("meta".equals(event.type())) {
                if (data.get("modelName") instanceof String name) {
                    modelName.add(name);
                }
                data.put("messageId", assistantMsgId);
            }
            if ("token".equals(event.type())) {
                Object text = data.get("text");
                if (text != null) {
                    answer.append(text);
                }
            }
            if ("sources".equals(event.type())) {
                replaceSources(sources, data);
                data = withFileNames(new LinkedHashMap<>(data), sources);
            }
            if ("final".equals(event.type())) {
                replaceSources(sources, data);
                finalPayload.clear();
                finalPayload.putAll(data);
                data = withFileNames(new LinkedHashMap<>(data), sources);
                data.put("messageId", assistantMsgId);
            }
            onEvent.accept(new ChatEventVo(event.type(), data));
        });

        // ③ 回填 ASSISTANT 消息与引用来源。
        String streamed = answer.toString();
        assistantMsg.setContent(streamed.isEmpty()
                && finalPayload.get("content") instanceof String content ? content : streamed);
        assistantMsg.setAnswerStatus(finalPayload.getOrDefault("answerStatus", "ANSWERED").toString());
        if (finalPayload.get("confidence") instanceof Number confidence) {
            assistantMsg.setConfidence(BigDecimal.valueOf(confidence.doubleValue()));
        }
        if (finalPayload.get("tokenIn") instanceof Number tokenIn) {
            assistantMsg.setTokenIn(tokenIn.longValue());
        }
        if (finalPayload.get("tokenOut") instanceof Number tokenOut) {
            assistantMsg.setTokenOut(tokenOut.longValue());
        }
        if (!modelName.isEmpty()) {
            assistantMsg.setModelName(modelName.get(0));
        }
        chatMessageMapper.updateById(assistantMsg);
        persistSources(assistantMsgId, tenantId, sources);
    }

    // =====================================================================
    // 反馈 / 来源
    // =====================================================================

    @Override
    public void submitFeedback(long messageId, FeedbackDto request, String idempotencyKey) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        message.setFeedback("up".equalsIgnoreCase(request.reaction()) ? 1
                : "down".equalsIgnoreCase(request.reaction()) ? -1 : 0);
        message.setFeedbackReason(request.reason());
        chatMessageMapper.updateById(message);
    }

    @Override
    public Object getMessageSource(long messageId, String sourceId) {
        ChatMessageSource source = chatMessageSourceMapper.selectById(sourceId);
        if (source == null || !Objects.equals(source.getMessageId(), messageId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "引用来源不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunkId", source.getChunkId());
        result.put("documentId", source.getDocumentId());
        result.put("versionId", source.getVersionId());
        result.put("score", source.getScore());
        result.put("location", parseLocation(source.getLocationJson()));
        return result;
    }

    // =====================================================================
    // 全文搜索（web → Java → rag-engine → pgvector）
    // =====================================================================

    @Override
    @SuppressWarnings("unchecked") // Python items 反序列化为 Map<String, Object>（与 extractSources 同一模式）
    public CursorPageData<?> search(String keyword, List<Long> kbIds, List<String> fileExts,
                                    String dateFrom, String dateTo, String cursor, int size) {
        // ① 认证与租户上下文（fail-closed：未认证/无租户直接拒绝，绝不静默兜底）。
        long tenantId = requireTenantId();
        Long userId = currentUserId();
        // ② kb 级权限过滤：显式传入的 kbIds 必须存在且属于当前租户（deny-by-default）。
        List<Long> authorizedKbIds = authorizeKbIds(kbIds, tenantId);

        // ③ cursor → page 换算：cursor 即页码字符串（不透明 token 的最小实现；
        //    篡改 cursor 只会跳页，不会越权——租户/kb 过滤每次请求都重新校验）。
        int page = parseCursorToPage(cursor);

        // ④ 组装 rag-engine 检索请求（camelCase 对齐 Python ApiModel 别名）。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", UUID.randomUUID().toString());
        payload.put("keyword", keyword);
        // 未传 kbIds 时为空列表：Python 侧仅按 tenant_id 过滤，等价于「当前租户全部库」
        // （kb 成员级过滤为已知限制，与 listKbs/listDocuments 现状一致）。
        payload.put("kbIds", authorizedKbIds);
        payload.put("docIdWhitelist", List.of());
        payload.put("types", fileExts == null ? List.of() : fileExts);
        payload.put("dateFrom", dateFrom);
        payload.put("dateTo", dateTo);
        payload.put("vectorFusion", true); // 固定开启：词项覆盖融合优先关键词命中
        payload.put("page", page);
        payload.put("size", size);
        Map<String, Object> result = ragEnginePort.search(new TenantId(tenantId), payload);

        // ⑤ 解析命中并做 view_excerpt 二次授权（搜索结果仅包含有权查看摘要的内容；
        //    未认证/dev 场景无 userId 时放行，与预览/下载链路的既有约定一致）。
        List<Map<String, Object>> items = new ArrayList<>();
        if (result.get("items") instanceof List<?> rawItems) {
            for (Object raw : rawItems) {
                if (!(raw instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) map);
                if (userId != null && userId > 0
                        && item.get("documentId") instanceof Number documentId
                        && !accessPolicy.canViewExcerpt(
                                new TenantId(tenantId), new UserId(userId), documentId.longValue())) {
                    continue; // 无 VIEW_EXCERPT 权限的命中直接剔除（不泄露摘要）。
                }
                items.add(item);
            }
        }
        boolean hasMore = result.get("hasMore") instanceof Boolean more && more;
        // nextCursor 用页码 +1 编码；无更多页时置 null 终止翻页。
        String nextCursor = hasMore ? String.valueOf(page + 1) : null;
        return CursorPageData.of(items, nextCursor, hasMore);
    }

    @Override
    public Object getSearchExcerpt(String hitId) {
        // ① 认证与租户上下文（fail-closed）。
        long tenantId = requireTenantId();
        Long userId = currentUserId();
        // ② hitId 即 chunkId：按 id 直查分块正文（避免「重新检索定位」在深翻页丢命中）。
        Map<String, Object> chunk = ragEnginePort.getChunk(new TenantId(tenantId), hitId);
        if (!(chunk.get("documentId") instanceof Number documentId)) {
            // 跨租户/不存在的命中统一按 404 拒绝（deny-by-default，不泄露存在性）。
            throw new ApiException(ErrorCode.NOT_FOUND, "搜索命中不存在");
        }
        // ③ 文档存在性 + 租户归属校验（requireDocument deny-by-default）。
        documentService.getDocument(documentId.longValue());
        // ④ 摘录权限：VIEW_EXCERPT（策略层统一判定；未认证/dev 场景放行，同搜索链路约定）。
        if (userId != null && userId > 0 && !accessPolicy.canViewExcerpt(
                new TenantId(tenantId), new UserId(userId), documentId.longValue())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无摘要查看权限（需要 VIEW_EXCERPT）");
        }
        // ⑤ 组装摘录 VO（camelCase 对齐前端 SearchHit 契约）。
        Map<String, Object> excerpt = new LinkedHashMap<>();
        excerpt.put("hitId", hitId);
        excerpt.put("documentId", documentId.longValue());
        excerpt.put("versionId", chunk.get("versionId"));
        excerpt.put("fileName", chunk.get("fileName"));
        excerpt.put("fileExt", chunk.get("fileExt"));
        excerpt.put("pageNo", chunk.get("pageNo"));
        excerpt.put("sectionTitle", chunk.get("sectionTitle"));
        excerpt.put("text", chunk.get("text"));
        return excerpt;
    }

    /** 校验请求的 kbIds 全部存在且属于当前租户（fail-closed），返回原样合法 id 列表。 */
    private List<Long> authorizeKbIds(List<Long> kbIds, long tenantId) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        List<Long> authorized = new ArrayList<>();
        for (Long kbId : kbIds) {
            // kbBrief：不存在抛 NOT_FOUND；再比对租户归属（跨租户 → FORBIDDEN）。
            KbService.KbBrief brief = kbService.kbBrief(kbId);
            if (brief.tenantId() > 0 && brief.tenantId() != tenantId) {
                throw new ApiException(ErrorCode.FORBIDDEN, "无权访问该知识库");
            }
            authorized.add(brief.id());
        }
        return authorized;
    }

    /** cursor（页码字符串）→ page；空/缺省为第 1 页，非法值直接拒绝。 */
    private int parseCursorToPage(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 1;
        }
        try {
            int page = Integer.parseInt(cursor.trim());
            if (page < 1) {
                throw new NumberFormatException("page must be >= 1");
            }
            return page;
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的分页游标: " + cursor);
        }
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    private ChatSession requireSession(long chatId) {
        ChatSession session = chatSessionMapper.selectById(chatId);
        if (session == null || "DELETED".equals(session.getStatus())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    private List<Long> kbIdsOf(long chatId) {
        return chatSessionKbMapper.selectList(new LambdaQueryWrapper<ChatSessionKb>()
                        .eq(ChatSessionKb::getSessionId, chatId))
                .stream().map(ChatSessionKb::getKbId).toList();
    }

    private long messageCountOf(long chatId) {
        return chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, chatId));
    }

    private List<ChatMessage> loadRecentMessages(long chatId, int limit) {
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, chatId)
                .orderByDesc(ChatMessage::getSeq)
                .last("LIMIT " + Math.max(limit, 1)));
        Collections.reverse(messages);
        return messages;
    }

    private List<ChatSessionVo> toSessionVos(List<ChatSession> sessions) {
        Map<Long, List<Long>> kbIdsBySession = chatSessionKbMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(ChatSessionKb::getSessionId,
                        Collectors.mapping(ChatSessionKb::getKbId, Collectors.toList())));
        Map<Long, Long> countBySession = chatMessageMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(ChatMessage::getSessionId, Collectors.counting()));
        return sessions.stream()
                .map(session -> toSessionVo(session,
                        kbIdsBySession.getOrDefault(session.getId(), List.of()),
                        countBySession.getOrDefault(session.getId(), 0L)))
                .toList();
    }

    private ChatSessionVo toSessionVo(ChatSession session, List<Long> kbIds, long messageCount) {
        // 实体审计列经 V0.3 统一为 create_time/update_time；VO 契约字段为 createdAt/updatedAt。
        return new ChatSessionVo(session.getId(), session.getTitle(), session.getStatus(),
                kbIds, messageCount, session.getCreateTime(), session.getUpdateTime());
    }

    private ChatMessageVo toMessageVo(ChatMessage message, List<ChatMessageSource> sources) {
        return new ChatMessageVo(
                message.getId(),
                message.getSessionId(),
                message.getSeq() == null ? 0 : message.getSeq(),
                message.getRole(),
                message.getContent(),
                message.getAnswerStatus(),
                message.getConfidence() == null ? null : message.getConfidence().doubleValue(),
                message.getFeedback() == null ? 0 : message.getFeedback(),
                message.getTokenIn() == null ? 0 : message.getTokenIn(),
                message.getTokenOut() == null ? 0 : message.getTokenOut(),
                message.getModelName(),
                sources.stream().map(this::toSourceVo).toList(),
                List.of(),
                message.getCreateTime());
    }

    private ChatSourceVo toSourceVo(ChatMessageSource source) {
        Map<String, Object> location = parseLocation(source.getLocationJson());
        int pageNo = location.get("pageNo") instanceof Number n ? n.intValue() : 0;
        Object section = location.get("sectionTitle");
        return new ChatSourceVo(
                source.getChunkId(),
                source.getDocumentId(),
                fileNameOf(source.getDocumentId()),
                pageNo,
                section instanceof String s ? s : null,
                source.getScore() == null ? 0.0 : source.getScore().doubleValue());
    }

    /** 文档文件名（跨模块经 DocumentService 取，不直接访问 document 持久化层）。 */
    private String fileNameOf(long documentId) {
        try {
            return documentService.getDocument(documentId).fileName();
        } catch (Exception e) {
            // 文档已删除/无权限等场景：文件名留空，不影响来源展示。
            return "";
        }
    }

    /** 批量加载消息的引用来源（避免 N+1）。 */
    private Map<Long, List<ChatMessageSource>> sourcesByMessageIds(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return chatMessageSourceMapper.selectList(new LambdaQueryWrapper<ChatMessageSource>()
                        .in(ChatMessageSource::getMessageId, messageIds))
                .stream().collect(Collectors.groupingBy(ChatMessageSource::getMessageId));
    }

    /** 用 Python sources 数组替换目标列表（lambda 内原地修改，保持捕获变量 effectively final）。 */
    private void replaceSources(List<Map<String, Object>> target, Map<String, Object> data) {
        target.clear();
        target.addAll(extractSources(data));
    }

    /** 把 Python sources 数组解析为 Java Map 列表（保留 versionId/text 供落库）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSources(Map<String, Object> data) {
        Object raw = data.get("sources");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                sources.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return sources;
    }

    /** sources 事件转发前确保有 fileName（Python 已 JOIN document 提供；缺失时经 DocumentService 兜底）。 */
    private Map<String, Object> withFileNames(Map<String, Object> data, List<Map<String, Object>> sources) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> source : sources) {
            Map<String, Object> copy = new LinkedHashMap<>(source);
            if (!(copy.get("fileName") instanceof String name) || name.isBlank()) {
                if (source.get("documentId") instanceof Number documentId) {
                    copy.put("fileName", fileNameOf(documentId.longValue()));
                }
            }
            enriched.add(copy);
        }
        data.put("sources", enriched);
        return data;
    }

    /** 落 chat_message_source（location_json 用 CAST JSONB 写入，对齐 OutboxEventMapper 模式）。 */
    private void persistSources(long messageId, long tenantId, List<Map<String, Object>> sources) {
        int rank = 1;
        for (Map<String, Object> source : sources) {
            if (!(source.get("chunkId") instanceof String chunkId)
                    || !(source.get("documentId") instanceof Number documentId)
                    || !(source.get("versionId") instanceof Number versionId)) {
                continue;
            }
            Object text = source.get("text");
            String citedSha = text instanceof String s && !s.isBlank()
                    ? sha256Hex(s) : sha256Hex(chunkId);
            Map<String, Object> location = new LinkedHashMap<>();
            if (source.get("pageNo") instanceof Number pageNo) {
                location.put("pageNo", pageNo.intValue());
            }
            if (source.get("sectionTitle") instanceof String sectionTitle) {
                location.put("sectionTitle", sectionTitle);
            }
            ChatMessageSource row = new ChatMessageSource();
            row.setTenantId(tenantId);
            row.setMessageId(messageId);
            row.setDocumentId(documentId.longValue());
            row.setVersionId(versionId.longValue());
            row.setChunkId(chunkId);
            row.setSourceRank(rank);
            row.setScore(source.get("score") instanceof Number score
                    ? BigDecimal.valueOf(score.doubleValue()) : null);
            row.setLocationJson(toJson(location));
            row.setCitedTextSha256(citedSha);
            chatMessageSourceMapper.insertWithJsonb(row);
            rank++;
        }
    }

    private Map<String, Object> asMap(Object data) {
        if (data instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> parseLocation(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败", e);
        }
    }

    private String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 当前用户 id；未认证直接抛 UNAUTHORIZED（fail-closed，绝不静默兜底种子用户）。 */
    private Long currentUserId() {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null || userId <= 0) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
        }
        return userId;
    }

    /** 当前 JWT 主体的租户 id；未认证或无租户上下文直接抛 UNAUTHORIZED（fail-closed）。 */
    private long requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal
                && principal.tenantId() > 0) {
            return principal.tenantId();
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或缺少有效租户上下文");
    }
}
