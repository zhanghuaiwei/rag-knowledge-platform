package com.ragkb.service.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.security.SecurityUtils;
import com.ragkb.service.modules.admin.dto.OrgDto;
import com.ragkb.service.modules.admin.dto.WebhookDto;
import com.ragkb.service.modules.admin.dto.WebhookToggleDto;
import com.ragkb.service.modules.admin.persistence.entity.AuditLog;
import com.ragkb.service.modules.admin.persistence.entity.Notification;
import com.ragkb.service.modules.admin.persistence.entity.SysOrg;
import com.ragkb.service.modules.admin.persistence.entity.SysUserOrg;
import com.ragkb.service.modules.admin.persistence.entity.WebhookDelivery;
import com.ragkb.service.modules.admin.persistence.entity.WebhookSubscription;
import com.ragkb.service.modules.admin.persistence.mapper.AuditLogMapper;
import com.ragkb.service.modules.admin.persistence.mapper.NotificationMapper;
import com.ragkb.service.modules.admin.persistence.mapper.SysOrgMapper;
import com.ragkb.service.modules.admin.persistence.mapper.SysUserOrgMapper;
import com.ragkb.service.modules.admin.persistence.mapper.WebhookDeliveryMapper;
import com.ragkb.service.modules.admin.persistence.mapper.WebhookSubscriptionMapper;
import com.ragkb.service.modules.admin.persistence.query.OrgMemberCountRow;
import com.ragkb.service.modules.admin.service.AdminService;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import com.ragkb.service.modules.admin.vo.NotificationItemVo;
import com.ragkb.service.modules.admin.vo.OrgVo;
import com.ragkb.service.modules.admin.vo.WebhookDeliveryVo;
import com.ragkb.service.modules.admin.vo.WebhookVo;
import com.ragkb.service.modules.identity.service.TokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 管理中心用例实现（admin 模块）：组织目录 / 安全审计 / Webhook 订阅 / 站内通知。
 *
 * <p>多租户隔离（deny-by-default）：所有读写先按当前认证租户
 * （{@code TokenService.JwtPrincipal.tenantId()}，不信任客户端自报）过滤；
 * 按主键定位的资源在跨租户访问时统一按「不存在」（404）处理，不泄露存在性。
 *
 * <p>安全审计：组织与 Webhook 的写操作在同事务内写 {@code audit_log}
 * （经本模块 {@link AuditLogMapper}，对齐 identity 模块的 writeAudit 模式），
 * 只记 actor/动作/对象 id，绝不落 webhook secret 等敏感值。
 *
 * <p>Webhook secret：创建时生成 {@code whsec_} 前缀的 256bit 随机密钥，
 * 存入 {@code webhook_subscription.secret_ref}（签名时需恢复明文，无法像 API Key 只存摘要），
 * 仅在创建响应中返回一次，之后任何接口与日志均不再透出。
 */
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class AdminServiceImpl implements AdminService {

    // ---------- 状态/枚举常量（与 DDL CHECK 约束一一对应，禁止裸写魔法值扩散） ----------

    /** sys_org.status：正常态（ck_sys_org_status）。 */
    private static final String ORG_STATUS_ACTIVE = "ACTIVE";
    /** webhook_subscription.status：启用（ck_webhook_subscription_status）。 */
    private static final String WEBHOOK_STATUS_ACTIVE = "ACTIVE";
    /** webhook_subscription.status：暂停（toggle 暂停语义；投递引擎只消费 ACTIVE 订阅）。 */
    private static final String WEBHOOK_STATUS_PAUSED = "PAUSED";
    /** webhook_subscription.status：撤销（删除订阅的终态，投递引擎停止匹配）。 */
    private static final String WEBHOOK_STATUS_REVOKED = "REVOKED";
    /** webhook_delivery.status：投递成功终态（ck_webhook_delivery_status）。 */
    private static final String DELIVERY_STATUS_SUCCEEDED = "SUCCEEDED";
    /** audit_log.result：成功（ck_audit_result；admin 写操作审计均为 SUCCEEDED）。 */
    private static final String AUDIT_RESULT_SUCCEEDED = "SUCCEEDED";
    /** audit_log.actor_type：管理端操作者均为登录用户（ck_audit_actor）。 */
    private static final String AUDIT_ACTOR_TYPE_USER = "USER";

    /** 审计动作码（audit_log.action，命名：<资源>.<动作>，对齐 identity 模块约定）。 */
    private static final String AUDIT_ACTION_ORG_CREATE = "org.create";
    private static final String AUDIT_ACTION_ORG_UPDATE = "org.update";
    private static final String AUDIT_ACTION_ORG_DELETE = "org.delete";
    private static final String AUDIT_ACTION_WEBHOOK_CREATE = "webhook.create";
    private static final String AUDIT_ACTION_WEBHOOK_TOGGLE = "webhook.toggle";
    private static final String AUDIT_ACTION_WEBHOOK_DELETE = "webhook.delete";

    /** 审计资源类型（audit_log.resource_type；与被操作表的领域名对齐）。 */
    private static final String RESOURCE_TYPE_ORG = "ORG";
    private static final String RESOURCE_TYPE_WEBHOOK = "WEBHOOK";

    /** webhook secret 明文前缀（投递引擎用它识别平台签发的签名密钥，对齐 ApiKey 的 rk_ 前缀模式）。 */
    private static final String WEBHOOK_SECRET_PREFIX = "whsec_";

    /** 通知列表最大返回条数（契约为不分页 List，取最近 100 条防止响应膨胀）。 */
    private static final int NOTIFICATION_LIST_LIMIT = 100;

    /** 生成 webhook secret 的 CSPRNG（每次调用生成 32 字节 >=256bit 熵）。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** webhook secret 使用的 base64url 编码器（无填充，URL 安全）。 */
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    // ---------- 依赖（全部为 admin 模块自有持久化 + common/identity 的 service 层） ----------

    private final SysOrgMapper sysOrgMapper;
    private final SysUserOrgMapper sysUserOrgMapper;
    private final AuditLogMapper auditLogMapper;
    private final WebhookSubscriptionMapper webhookSubscriptionMapper;
    private final WebhookDeliveryMapper webhookDeliveryMapper;
    private final NotificationMapper notificationMapper;
    /** event_types JSONB（实体映射 String）与 List<String> 互转。 */
    private final ObjectMapper objectMapper;

    public AdminServiceImpl(SysOrgMapper sysOrgMapper,
                            SysUserOrgMapper sysUserOrgMapper,
                            AuditLogMapper auditLogMapper,
                            WebhookSubscriptionMapper webhookSubscriptionMapper,
                            WebhookDeliveryMapper webhookDeliveryMapper,
                            NotificationMapper notificationMapper,
                            ObjectMapper objectMapper) {
        this.sysOrgMapper = sysOrgMapper;
        this.sysUserOrgMapper = sysUserOrgMapper;
        this.auditLogMapper = auditLogMapper;
        this.webhookSubscriptionMapper = webhookSubscriptionMapper;
        this.webhookDeliveryMapper = webhookDeliveryMapper;
        this.notificationMapper = notificationMapper;
        this.objectMapper = objectMapper;
    }

    // =====================================================================
    // ① 组织目录 CRUD
    // =====================================================================

    @Override
    public List<OrgVo> listOrgs() {
        // 当前认证租户（deny-by-default：未认证直接拒绝，组织目录不允许匿名访问）。
        long tenantId = currentTenantId();
        // 平铺返回（前端 Org 契约自带 parentId，由前端组树）；按父节点 → 排序号 → id 稳定排序。
        List<SysOrg> orgs = sysOrgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getTenantId, tenantId)
                .orderByAsc(SysOrg::getParentId)
                .orderByAsc(SysOrg::getSortOrder)
                .orderByAsc(SysOrg::getId));
        // 成员数一次 GROUP BY 聚合（避免逐组织 N+1 计数）。
        Map<Long, Long> memberCounts = new HashMap<>();
        for (OrgMemberCountRow row : sysOrgMapper.selectMemberCounts(tenantId)) {
            memberCounts.put(row.getOrgId(), row.getMemberCount());
        }
        return orgs.stream()
                .map(org -> toOrgVo(org, memberCounts.getOrDefault(org.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public OrgVo createOrg(OrgDto request, String idempotencyKey) {
        long tenantId = currentTenantId();
        String name = request.name().trim();
        // parentId 语义：null = 建为根节点；非 null 必须是本租户内 ACTIVE 的既有组织（防跨租户挂靠/挂到停用组织）。
        String parentPath = "/";
        if (request.parentId() != null) {
            SysOrg parent = requireOrg(request.parentId());
            if (!ORG_STATUS_ACTIVE.equals(parent.getStatus())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "父组织已停用，无法在其下新建组织");
            }
            parentPath = parent.getPath() != null ? parent.getPath() : "/";
        }
        // 同级名称唯一预检（uq_sys_org_sibling_name 兜底并发窗口）。
        requireSiblingNameAvailable(tenantId, request.parentId(), name, null);
        SysOrg org = new SysOrg();
        org.setTenantId(tenantId);          // 租户归属从 JWT 推导，不信任客户端
        org.setParentId(request.parentId()); // 父节点（null = 根）
        org.setName(name);                   // 已 trim 的展示名
        org.setPath("/");                    // 先占位满足 ck_sys_org_path，插入拿到自增 id 后回填物化路径
        org.setStatus(ORG_STATUS_ACTIVE);    // 新建直接可用
        try {
            sysOrgMapper.insert(org);
        } catch (DuplicateKeyException e) {
            // 并发建号撞同级唯一约束：业务化为 409。
            throw new ApiException(ErrorCode.CONFLICT, "同级已存在同名组织");
        }
        // 物化路径回填：path = 父路径 + 自身 id + '/'（根节点即 '/<id>/'，支持前缀 LIKE 子树查询）。
        org.setPath(parentPath + org.getId() + "/");
        sysOrgMapper.updateById(org);
        // 安全审计：记录建组织动作（同事务，回滚时审计一并回滚）。
        writeAudit(AUDIT_ACTION_ORG_CREATE, RESOURCE_TYPE_ORG, org.getId());
        return toOrgVo(org, 0L);
    }

    @Override
    @Transactional
    public OrgVo updateOrg(long orgId, OrgDto request) {
        long tenantId = currentTenantId();
        // ① 加载并校验目标组织（存在性 + 租户归属）。
        SysOrg org = requireOrg(orgId);
        String name = request.name().trim();
        // ② 名称变更：同级唯一（排除自身）。
        if (!name.equals(org.getName())) {
            requireSiblingNameAvailable(tenantId, request.parentId() != null ? request.parentId() : org.getParentId(),
                    name, orgId);
            org.setName(name);
        }
        // ③ 移动（PATCH 语义：parentId 为 null 表示本次不移动，前端 updateOrg 只传 name）。
        if (request.parentId() != null && !request.parentId().equals(org.getParentId())) {
            applyOrgMove(org, request.parentId());
        }
        // ④ 乐观锁更新（row_version 不符即并发冲突）。
        if (sysOrgMapper.updateById(org) == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "组织已被他人修改，请刷新后重试");
        }
        writeAudit(AUDIT_ACTION_ORG_UPDATE, RESOURCE_TYPE_ORG, orgId);
        return toOrgVo(sysOrgMapper.selectById(orgId), memberCountOf(tenantId, orgId));
    }

    @Override
    @Transactional
    public void deleteOrg(long orgId) {
        // ① 加载并校验目标组织。
        requireOrg(orgId);
        // ② 有成员则拒绝：sys_user_org 存在引用时组织不可删（FK ON DELETE CASCADE 只是兜底，应用层先显式拦截给出可读错误）。
        if (sysUserOrgMapper.selectCount(new LambdaQueryWrapper<SysUserOrg>()
                .eq(SysUserOrg::getTenantId, currentTenantId())
                .eq(SysUserOrg::getOrgId, orgId)) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "组织下仍有成员，请先移出成员再删除");
        }
        // ③ 有子组织则拒绝：子组织的 FK 指向本组织，悬挂父节点会破坏目录树一致性。
        if (sysOrgMapper.selectCount(new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getTenantId, currentTenantId())
                .eq(SysOrg::getParentId, orgId)) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "存在子组织，请先删除或移出子组织");
        }
        // ④ 安全审计先行（同事务），再物理删除。
        //    物理删而非软删：uq_sys_org_sibling_name 唯一约束不含 del_flag，软删会占住同级名称导致无法重建同名组织
        //    （对齐 identity 模块 hardDeleteByTenantAndUser 的处理理由）。
        writeAudit(AUDIT_ACTION_ORG_DELETE, RESOURCE_TYPE_ORG, orgId);
        sysOrgMapper.hardDeleteById(currentTenantId(), orgId);
    }

    // =====================================================================
    // ② 安全审计查询
    // =====================================================================

    @Override
    public PageData<AuditLogEntryVo> listAuditLogs(int page, int size, String action, String resourceType,
                                                   Long actorId, String dateFrom, String dateTo, String result) {
        // 当前认证租户（audit_log 带 tenant_id 列，按租户隔离查询）。
        long tenantId = currentTenantId();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getTenantId, tenantId)   // 租户隔离红线：所有查询带 tenant_id
                .eq(StringUtils.hasText(action), AuditLog::getAction, action)                       // 动作（模糊）
                .like(StringUtils.hasText(resourceType), AuditLog::getResourceType, resourceType)     // 资源类型（模糊）
                .eq(actorId != null, AuditLog::getActorId, String.valueOf(actorId))                  // 操作者（actor_id 为 VARCHAR 列）
                .eq(StringUtils.hasText(result), AuditLog::getResult, result);                       // 结果（SUCCEEDED/DENIED/FAILED）
        // 时间范围过滤：前端传 ISO-8601 字符串，解析失败按参数错误拒绝（不给静默的全量结果）。
        if (StringUtils.hasText(dateFrom)) {
            wrapper.ge(AuditLog::getOccurredAt, parseInstant(dateFrom, "dateFrom"));
        }
        if (StringUtils.hasText(dateTo)) {
            wrapper.le(AuditLog::getOccurredAt, parseInstant(dateTo, "dateTo"));
        }
        // 审计流按发生时间倒序（最新在前，走 idx_audit_time 索引）。
        IPage<AuditLog> auditPage = auditLogMapper.selectPage(new Page<>(safePage, safeSize),
                wrapper.orderByDesc(AuditLog::getOccurredAt));
        List<AuditLogEntryVo> items = auditPage.getRecords().stream()
                .map(AdminServiceImpl::toAuditVo)
                .toList();
        return PageData.of(items, auditPage.getTotal(), safePage, safeSize);
    }

    // =====================================================================
    // ③ Webhook 订阅管理
    // =====================================================================

    @Override
    public List<WebhookVo> listWebhooks() {
        // 当前租户全部未删除订阅（del_flag 由 @TableLogic 自动过滤），按创建序倒序。
        List<WebhookSubscription> subs = webhookSubscriptionMapper.selectList(
                new LambdaQueryWrapper<WebhookSubscription>()
                        .eq(WebhookSubscription::getTenantId, currentTenantId())
                        .orderByDesc(WebhookSubscription::getId));
        // secret 一律不回显（只在创建响应返回过一次）。
        return subs.stream().map(sub -> toWebhookVo(sub, null)).toList();
    }

    @Override
    @Transactional
    public WebhookVo createWebhook(WebhookDto request, String idempotencyKey) {
        long tenantId = currentTenantId();
        // targetUrl 必须 https（对齐 DDL ck_webhook_target_https，应用层先拦截给出可读错误）。
        if (!request.targetUrl().startsWith("https://")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "回调地址必须为 https:// 开头");
        }
        // 订阅名租户内唯一预检（uq_webhook_subscription_name 兜底并发窗口）。
        if (webhookSubscriptionMapper.selectCount(new LambdaQueryWrapper<WebhookSubscription>()
                .eq(WebhookSubscription::getTenantId, tenantId)
                .eq(WebhookSubscription::getName, request.name())) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "同名 Webhook 订阅已存在: " + request.name());
        }
        // 生成签名密钥：whsec_ + 32 字节 CSPRNG 的 base64url（>=256bit 熵，对齐 ApiKeyCrypto 的生成规格）。
        // secret_ref 存该明文：HMAC 签名时必须恢复密钥，无法像 API Key 只存摘要（见类注释的权衡说明）。
        byte[] secretBytes = new byte[32];
        SECURE_RANDOM.nextBytes(secretBytes);
        String secret = WEBHOOK_SECRET_PREFIX + BASE64_URL.encodeToString(secretBytes);
        WebhookSubscription sub = new WebhookSubscription();
        sub.setTenantId(tenantId);                    // 租户归属从 JWT 推导
        sub.setName(request.name());                  // 订阅展示名
        sub.setTargetUrl(request.targetUrl());        // 回调地址（已校验 https）
        sub.setEventTypes(toEventTypesJson(request.eventTypes())); // 事件类型数组序列化为 JSON（去重）
        sub.setSecretRef(secret);                     // 签名密钥（仅创建响应透出一次）
        sub.setStatus(WEBHOOK_STATUS_ACTIVE);         // 新建即启用（PAUSED/REVOKED 由后续操作流转）
        try {
            // event_types 为 JSONB 列：走 XML 的 CAST(? AS jsonb) 插入（BaseMapper.insert 会按 varchar 写入报类型错）。
            webhookSubscriptionMapper.insertWithJsonb(sub);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT, "同名 Webhook 订阅已存在: " + request.name());
        }
        // 安全审计：只记订阅 id，绝不记录 secret。
        writeAudit(AUDIT_ACTION_WEBHOOK_CREATE, RESOURCE_TYPE_WEBHOOK, sub.getId());
        // 创建响应携带 secret 明文（唯一一次），前端须立即保存。
        return toWebhookVo(sub, secret);
    }

    @Override
    @Transactional
    public WebhookVo toggleWebhook(long subscriptionId, WebhookToggleDto request) {
        // 加载并校验订阅归属，再按入参流转 ACTIVE/PAUSED。
        WebhookSubscription sub = requireWebhook(subscriptionId);
        sub.setStatus(Boolean.TRUE.equals(request.paused()) ? WEBHOOK_STATUS_PAUSED : WEBHOOK_STATUS_ACTIVE);
        if (webhookSubscriptionMapper.updateById(sub) == 0) {
            // 乐观锁冲突：并发启停时提示刷新重试。
            throw new ApiException(ErrorCode.CONFLICT, "订阅已被他人修改，请刷新后重试");
        }
        writeAudit(AUDIT_ACTION_WEBHOOK_TOGGLE, RESOURCE_TYPE_WEBHOOK, subscriptionId);
        return toWebhookVo(webhookSubscriptionMapper.selectById(subscriptionId), null);
    }

    @Override
    @Transactional
    public void deleteWebhook(long subscriptionId) {
        // 加载并校验订阅归属（跨租户按不存在处理）。
        requireWebhook(subscriptionId);
        // 安全审计先行（同事务）。
        writeAudit(AUDIT_ACTION_WEBHOOK_DELETE, RESOURCE_TYPE_WEBHOOK, subscriptionId);
        // 软删：置 status=REVOKED + del_flag=1（setSql 显式置位，@TableLogic 字段不参与实体更新）。
        // 不物理删的原因：webhook_delivery 对 subscription 的 FK 为 ON DELETE CASCADE，
        // 物理删会连带抹掉投递历史（排障证据）；代价是软删行占住 (tenant_id, name) 唯一键，
        // 同名重建需换名（文档中说明该权衡）。
        webhookSubscriptionMapper.update(null, new LambdaUpdateWrapper<WebhookSubscription>()
                .eq(WebhookSubscription::getTenantId, currentTenantId())
                .eq(WebhookSubscription::getId, subscriptionId)
                .set(WebhookSubscription::getStatus, WEBHOOK_STATUS_REVOKED)
                .setSql("del_flag = 1"));
    }

    @Override
    public PageData<WebhookDeliveryVo> listWebhookDeliveries(int page, int size, String status) {
        // 当前租户投递记录分页（status 过滤：PENDING/SENDING/SUCCEEDED/RETRY/DEAD），最新在前。
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        IPage<WebhookDelivery> deliveryPage = webhookDeliveryMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<WebhookDelivery>()
                        .eq(WebhookDelivery::getTenantId, currentTenantId())
                        .eq(StringUtils.hasText(status), WebhookDelivery::getStatus, status)
                        .orderByDesc(WebhookDelivery::getId));
        List<WebhookDeliveryVo> items = deliveryPage.getRecords().stream()
                .map(AdminServiceImpl::toDeliveryVo)
                .toList();
        return PageData.of(items, deliveryPage.getTotal(), safePage, safeSize);
    }

    // =====================================================================
    // ④ 站内通知
    // =====================================================================

    @Override
    public List<NotificationItemVo> listNotifications() {
        // 通知是用户维度资源：当前登录用户（全局身份 id）+ 租户双重过滤，最近 100 条。
        long userId = currentUserId();
        long tenantId = currentTenantId();
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getTenantId, tenantId)
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + NOTIFICATION_LIST_LIMIT))
                .stream()
                .map(AdminServiceImpl::toNotificationVo)
                .toList();
    }

    @Override
    @Transactional
    public void markNotificationRead(long notificationId) {
        // 只能操作自己的通知（tenant + userId 双过滤），0 行即不存在/越权，统一按 404。
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getTenantId, currentTenantId())
                .eq(Notification::getUserId, currentUserId())
                .eq(Notification::getId, notificationId)
                .set(Notification::getRead, true)
                .set(Notification::getReadAt, Instant.now()));
        if (updated == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "通知不存在");
        }
    }

    @Override
    @Transactional
    public void markAllNotificationsRead() {
        // 批量置已读：仅命中未读行（read=false），幂等且减少写放大。
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getTenantId, currentTenantId())
                .eq(Notification::getUserId, currentUserId())
                .eq(Notification::getRead, false)
                .set(Notification::getRead, true)
                .set(Notification::getReadAt, Instant.now()));
    }

    // =====================================================================
    // 内部工具：校验 / 审计 / VO 映射
    // =====================================================================

    /** 加载组织并做租户归属校验（所有按 orgId 操作的统一入口，跨租户按不存在处理）。 */
    private SysOrg requireOrg(long orgId) {
        SysOrg org = sysOrgMapper.selectById(orgId);
        if (org == null || !Long.valueOf(currentTenantId()).equals(org.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "组织不存在");
        }
        return org;
    }

    /** 加载 Webhook 订阅并做租户归属校验（跨租户按不存在处理，不泄露存在性）。 */
    private WebhookSubscription requireWebhook(long subscriptionId) {
        WebhookSubscription sub = webhookSubscriptionMapper.selectById(subscriptionId);
        if (sub == null || !Long.valueOf(currentTenantId()).equals(sub.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Webhook 订阅不存在");
        }
        return sub;
    }

    /** 同级名称唯一预检：excludeId 非空时排除自身（更新场景）。 */
    private void requireSiblingNameAvailable(long tenantId, Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<SysOrg> wrapper = new LambdaQueryWrapper<SysOrg>()
                .eq(SysOrg::getTenantId, tenantId)
                .eq(SysOrg::getName, name);
        // 根节点（parentId 为 null）与挂靠节点（parentId 非 null）分属不同唯一键槽位，按 NULL 语义分别构造条件。
        if (parentId == null) {
            wrapper.isNull(SysOrg::getParentId);
        } else {
            wrapper.eq(SysOrg::getParentId, parentId);
        }
        if (excludeId != null) {
            wrapper.ne(SysOrg::getId, excludeId);
        }
        if (sysOrgMapper.selectCount(wrapper) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "同级已存在同名组织: " + name);
        }
    }

    /** 组织移动：校验新父合法（存在/ACTIVE/非自身/非后代，防环）并重算本节点与子孙的物化路径。 */
    private void applyOrgMove(SysOrg org, Long newParentId) {
        // 移到根：直接以 '/' 作为父路径。
        if (newParentId == null) {
            rewriteSubtreePath(org, "/");
            return;
        }
        // 防自环：不能把自己挂到自己下面。
        if (newParentId.equals(org.getId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不能将组织移动到自身之下");
        }
        SysOrg newParent = requireOrg(newParentId);
        if (!ORG_STATUS_ACTIVE.equals(newParent.getStatus())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "目标父组织已停用");
        }
        String parentPath = newParent.getPath() != null ? newParent.getPath() : "/";
        // 防环核心校验：新父的物化路径若以本组织路径为前缀，说明新父是本组织的后代，挂靠会成环。
        if (parentPath.startsWith(org.getPath())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不能将组织移动到其子组织之下");
        }
        rewriteSubtreePath(org, parentPath);
    }

    /** 以新父路径重算本节点与全部子孙节点的物化路径（前缀替换，一条 SQL 完成子树重写）。 */
    private void rewriteSubtreePath(SysOrg org, String newParentPath) {
        String oldPrefix = org.getPath();                       // 旧子树前缀（含自身）
        String newPrefix = newParentPath + org.getId() + "/";   // 新子树前缀（含自身）
        // 先重写子孙（path LIKE 前缀命中整棵子树，含自身，一次完成），再回填自身实体的 path 供后续 updateById 落库。
        sysOrgMapper.replaceSubtreePath(currentTenantId(), oldPrefix, newPrefix);
        org.setPath(newPrefix);
    }

    /** 统计组织在当前租户的成员数（sys_user_org 引用计数，删除守卫与 VO 展示共用）。 */
    private long memberCountOf(long tenantId, long orgId) {
        Long count = sysUserOrgMapper.selectCount(
                new LambdaQueryWrapper<com.ragkb.service.modules.admin.persistence.entity.SysUserOrg>()
                        .eq(com.ragkb.service.modules.admin.persistence.entity.SysUserOrg::getTenantId, tenantId)
                        .eq(com.ragkb.service.modules.admin.persistence.entity.SysUserOrg::getOrgId, orgId));
        return count == null ? 0 : count;
    }

    /** 写安全审计（actor=当前登录用户；只记动作与对象 id，不落 secret 等敏感值；detail 走数据库默认 '{}'）。 */
    private void writeAudit(String action, String resourceType, long resourceId) {
        AuditLog audit = new AuditLog();
        audit.setTenantId(currentTenantId());                       // 审计归属租户
        audit.setActorType(AUDIT_ACTOR_TYPE_USER);                  // 管理端操作者均为登录用户
        audit.setActorId(String.valueOf(currentUserId()));          // 全局用户 id（VARCHAR 列）
        audit.setAction(action);                                    // 动作码（org.create 等）
        audit.setResourceType(resourceType);                        // 资源类型（ORG/WEBHOOK）
        audit.setResourceId(String.valueOf(resourceId));            // 对象 id
        audit.setResult(AUDIT_RESULT_SUCCEEDED);                    // 管理写操作审计均为成功（失败在异常路径不落审计）
        audit.setOccurredAt(Instant.now());                         // 事件发生时间
        auditLogMapper.insert(audit);
    }

    /** 组织实体 → 列表视图（memberCount 由调用方聚合传入）。 */
    private static OrgVo toOrgVo(SysOrg org, long memberCount) {
        return new OrgVo(
                org.getId(),
                org.getParentId(),
                org.getName(),
                org.getPath(),
                memberCount,
                org.getStatus());
    }

    /** 审计实体 → 条目视图（actor 直接展示 actor_id，前端按需再解析显示名）。 */
    private static AuditLogEntryVo toAuditVo(AuditLog audit) {
        return new AuditLogEntryVo(
                audit.getId(),
                audit.getActorId() != null ? audit.getActorId() : "",
                audit.getActorType(),
                audit.getAction(),
                audit.getResourceType(),
                audit.getResourceId() != null ? audit.getResourceId() : "",
                audit.getResult(),
                audit.getReasonCode(),
                audit.getRequestId() != null ? audit.getRequestId() : "",
                audit.getOccurredAt());
    }

    /** 订阅实体 → 视图（eventTypes JSONB 字符串解析为数组；secret 仅创建场景由调用方传入）。 */
    private WebhookVo toWebhookVo(WebhookSubscription sub, String secret) {
        return new WebhookVo(
                sub.getId(),
                sub.getName(),
                sub.getTargetUrl(),
                parseEventTypes(sub.getEventTypes()),
                sub.getStatus(),
                sub.getCreateTime(),
                secret);
    }

    /** 投递实体 → 视图（eventId 为 outbox_event 主键的字符串化；摘要列给出人可读的结果概况）。 */
    private static WebhookDeliveryVo toDeliveryVo(WebhookDelivery delivery) {
        // responseSummary：成功展示 HTTP 状态码，失败展示最后错误码（无则给状态）。
        String summary = DELIVERY_STATUS_SUCCEEDED.equals(delivery.getStatus())
                ? (delivery.getHttpStatus() != null ? "HTTP " + delivery.getHttpStatus() : "OK")
                : (delivery.getLastErrorCode() != null ? delivery.getLastErrorCode() : delivery.getStatus());
        return new WebhookDeliveryVo(
                delivery.getId(),
                delivery.getSubscriptionId(),
                delivery.getEventId() != null ? String.valueOf(delivery.getEventId()) : "",
                delivery.getStatus(),
                delivery.getAttemptCount() != null ? delivery.getAttemptCount() : 0,
                delivery.getNextAttemptAt(),
                summary);
    }

    /** 通知实体 → 条目视图。 */
    private static NotificationItemVo toNotificationVo(Notification notification) {
        return new NotificationItemVo(
                notification.getId(),
                notification.getKind(),
                notification.getLevel(),
                notification.getTitle(),
                notification.getBody(),
                Boolean.TRUE.equals(notification.getRead()),
                notification.getCreateTime(),
                notification.getHref());
    }

    /** 事件类型列表去重并序列化为 JSON 数组字符串（满足 ck_webhook_event_types 的 jsonb 数组约束）。 */
    private String toEventTypesJson(List<String> eventTypes) {
        try {
            // LinkedHashSet 去重且保持入参顺序（重复事件类型对投递无意义）。
            return objectMapper.writeValueAsString(new LinkedHashSet<>(eventTypes));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "事件类型序列化失败");
        }
    }

    /** event_types JSONB 字符串解析回列表；空值/脏值降级为空列表（展示链路不因脏数据中断）。 */
    private List<String> parseEventTypes(String eventTypesJson) {
        if (!StringUtils.hasText(eventTypesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(eventTypesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** ISO-8601 字符串解析为 Instant（审计时间范围过滤入参），非法值按参数错误拒绝。 */
    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, field + " 必须为 ISO-8601 格式");
        }
    }

    /** 当前认证租户（JwtPrincipal 推导；未认证或租户无效则拒绝）。 */
    private long currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal
                && principal.tenantId() > 0) {
            return principal.tenantId();
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
    }

    /** 当前登录用户全局 id（通知等用户维度资源的属主）。 */
    private long currentUserId() {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
        }
        return userId;
    }
}
