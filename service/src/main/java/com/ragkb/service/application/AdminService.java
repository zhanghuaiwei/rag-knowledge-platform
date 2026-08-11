package com.ragkb.service.application;

import com.ragkb.service.common.PageData;
import com.ragkb.service.interfaces.dto.AdminDtos.AuditLogEntry;
import com.ragkb.service.interfaces.dto.AdminDtos.NotificationItem;
import com.ragkb.service.interfaces.dto.AdminDtos.Org;
import com.ragkb.service.interfaces.dto.AdminDtos.OrgInput;
import com.ragkb.service.interfaces.dto.AdminDtos.User;
import com.ragkb.service.interfaces.dto.AdminDtos.Webhook;
import com.ragkb.service.interfaces.dto.AdminDtos.WebhookDelivery;
import com.ragkb.service.interfaces.dto.AdminDtos.WebhookInput;
import com.ragkb.service.interfaces.dto.AdminDtos.WebhookToggleRequest;

import java.util.List;

/**
 * 管理中心用例：成员 / 组织 / 审计 / Webhook / 通知（实现点由人工完成）。
 */
public interface AdminService {

    PageData<User> listUsers(int page, int size);

    User disableUser(long userId);

    User enableUser(long userId);

    User updateUserOrg(long userId, Long orgId);

    List<Org> listOrgs();

    Org createOrg(OrgInput request, String idempotencyKey);

    Org updateOrg(long orgId, OrgInput request);

    void deleteOrg(long orgId);

    PageData<AuditLogEntry> listAuditLogs(int page, int size, String action, String resourceType,
                                          Long actorId, String dateFrom, String dateTo, String result);

    List<Webhook> listWebhooks();

    Webhook createWebhook(WebhookInput request, String idempotencyKey);

    Webhook toggleWebhook(long subscriptionId, WebhookToggleRequest request);

    void deleteWebhook(long subscriptionId);

    PageData<WebhookDelivery> listWebhookDeliveries(int page, int size, String status);

    List<NotificationItem> listNotifications();

    void markNotificationRead(long notificationId);

    void markAllNotificationsRead();
}
