package com.ragkb.service.modules.admin.service;

import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import com.ragkb.service.modules.admin.vo.NotificationItemVo;
import com.ragkb.service.modules.admin.vo.OrgVo;
import com.ragkb.service.modules.admin.dto.OrgDto;
import com.ragkb.service.modules.admin.vo.WebhookVo;
import com.ragkb.service.modules.admin.vo.WebhookDeliveryVo;
import com.ragkb.service.modules.admin.dto.WebhookDto;
import com.ragkb.service.modules.admin.dto.WebhookToggleDto;

import java.util.List;

/**
 * 管理中心用例：组织 / 审计 / WebhookVo / 通知（实现点由人工完成）。
 *
 * <p>V0.5 起成员账号管理（建号/角色/停用/移出/改密/组织调整）全部迁至 identity 模块
 * {@link com.ragkb.service.modules.identity.service.UserAccountService}，
 * 本接口只保留组织目录 / 审计 / WebhookVo / 通知（均为 admin 模块持久化，跨模块安全）。
 */
public interface AdminService {

    List<OrgVo> listOrgs();

    OrgVo createOrg(OrgDto request, String idempotencyKey);

    OrgVo updateOrg(long orgId, OrgDto request);

    void deleteOrg(long orgId);

    PageData<AuditLogEntryVo> listAuditLogs(int page, int size, String action, String resourceType,
                                          Long actorId, String dateFrom, String dateTo, String result);

    List<WebhookVo> listWebhooks();

    WebhookVo createWebhook(WebhookDto request, String idempotencyKey);

    WebhookVo toggleWebhook(long subscriptionId, WebhookToggleDto request);

    void deleteWebhook(long subscriptionId);

    PageData<WebhookDeliveryVo> listWebhookDeliveries(int page, int size, String status);

    List<NotificationItemVo> listNotifications();

    void markNotificationRead(long notificationId);

    void markAllNotificationsRead();
}
