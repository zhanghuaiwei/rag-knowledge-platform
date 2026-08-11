package com.ragkb.service.modules.admin.service;

import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import com.ragkb.service.modules.admin.vo.NotificationItemVo;
import com.ragkb.service.modules.admin.vo.OrgVo;
import com.ragkb.service.modules.admin.dto.OrgDto;
import com.ragkb.service.modules.admin.vo.UserVo;
import com.ragkb.service.modules.admin.vo.WebhookVo;
import com.ragkb.service.modules.admin.vo.WebhookDeliveryVo;
import com.ragkb.service.modules.admin.dto.WebhookDto;
import com.ragkb.service.modules.admin.dto.WebhookToggleDto;

import java.util.List;

/**
 * 管理中心用例：成员 / 组织 / 审计 / WebhookVo / 通知（实现点由人工完成）。
 */
public interface AdminService {

    PageData<UserVo> listUsers(int page, int size);

    UserVo disableUser(long userId);

    UserVo enableUser(long userId);

    UserVo updateUserOrg(long userId, Long orgId);

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
