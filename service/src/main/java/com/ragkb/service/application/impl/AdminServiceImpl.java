package com.ragkb.service.application.impl;

import com.ragkb.service.application.AdminService;
import com.ragkb.service.application.NotYetImplemented;
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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理中心桩实现（实现点由人工替换）。
 */
@Service
public class AdminServiceImpl implements AdminService {

    @Override
    public PageData<User> listUsers(int page, int size) {
        return NotYetImplemented.stub("AdminService#listUsers");
    }

    @Override
    public User disableUser(long userId) {
        return NotYetImplemented.stub("AdminService#disableUser");
    }

    @Override
    public User enableUser(long userId) {
        return NotYetImplemented.stub("AdminService#enableUser");
    }

    @Override
    public User updateUserOrg(long userId, Long orgId) {
        return NotYetImplemented.stub("AdminService#updateUserOrg");
    }

    @Override
    public List<Org> listOrgs() {
        return NotYetImplemented.stub("AdminService#listOrgs");
    }

    @Override
    public Org createOrg(OrgInput request, String idempotencyKey) {
        return NotYetImplemented.stub("AdminService#createOrg");
    }

    @Override
    public Org updateOrg(long orgId, OrgInput request) {
        return NotYetImplemented.stub("AdminService#updateOrg");
    }

    @Override
    public void deleteOrg(long orgId) {
        NotYetImplemented.stub("AdminService#deleteOrg");
    }

    @Override
    public PageData<AuditLogEntry> listAuditLogs(int page, int size, String action, String resourceType,
                                                Long actorId, String dateFrom, String dateTo, String result) {
        return NotYetImplemented.stub("AdminService#listAuditLogs");
    }

    @Override
    public List<Webhook> listWebhooks() {
        return NotYetImplemented.stub("AdminService#listWebhooks");
    }

    @Override
    public Webhook createWebhook(WebhookInput request, String idempotencyKey) {
        return NotYetImplemented.stub("AdminService#createWebhook");
    }

    @Override
    public Webhook toggleWebhook(long subscriptionId, WebhookToggleRequest request) {
        return NotYetImplemented.stub("AdminService#toggleWebhook");
    }

    @Override
    public void deleteWebhook(long subscriptionId) {
        NotYetImplemented.stub("AdminService#deleteWebhook");
    }

    @Override
    public PageData<WebhookDelivery> listWebhookDeliveries(int page, int size, String status) {
        return NotYetImplemented.stub("AdminService#listWebhookDeliveries");
    }

    @Override
    public List<NotificationItem> listNotifications() {
        return NotYetImplemented.stub("AdminService#listNotifications");
    }

    @Override
    public void markNotificationRead(long notificationId) {
        NotYetImplemented.stub("AdminService#markNotificationRead");
    }

    @Override
    public void markAllNotificationsRead() {
        NotYetImplemented.stub("AdminService#markAllNotificationsRead");
    }
}
