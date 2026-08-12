package com.ragkb.service.modules.admin.service.impl;

import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.admin.vo.AuditLogEntryVo;
import com.ragkb.service.modules.admin.vo.NotificationItemVo;
import com.ragkb.service.modules.admin.vo.OrgVo;
import com.ragkb.service.modules.admin.dto.OrgDto;
import com.ragkb.service.modules.admin.vo.WebhookVo;
import com.ragkb.service.modules.admin.vo.WebhookDeliveryVo;
import com.ragkb.service.modules.admin.dto.WebhookDto;
import com.ragkb.service.modules.admin.dto.WebhookToggleDto;
import com.ragkb.service.modules.admin.service.AdminService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理中心桩实现（实现点由人工替换）。成员账号管理已迁 identity 模块，见 UserAccountService。
 */
@Service
public class AdminServiceImpl implements AdminService {

    @Override
    public List<OrgVo> listOrgs() {
        return TodoSupport.notImplemented("AdminService#listOrgs");
    }

    @Override
    public OrgVo createOrg(OrgDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("AdminService#createOrg");
    }

    @Override
    public OrgVo updateOrg(long orgId, OrgDto request) {
        return TodoSupport.notImplemented("AdminService#updateOrg");
    }

    @Override
    public void deleteOrg(long orgId) {
        TodoSupport.notImplemented("AdminService#deleteOrg");
    }

    @Override
    public PageData<AuditLogEntryVo> listAuditLogs(int page, int size, String action, String resourceType,
                                                Long actorId, String dateFrom, String dateTo, String result) {
        return TodoSupport.notImplemented("AdminService#listAuditLogs");
    }

    @Override
    public List<WebhookVo> listWebhooks() {
        return TodoSupport.notImplemented("AdminService#listWebhooks");
    }

    @Override
    public WebhookVo createWebhook(WebhookDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("AdminService#createWebhook");
    }

    @Override
    public WebhookVo toggleWebhook(long subscriptionId, WebhookToggleDto request) {
        return TodoSupport.notImplemented("AdminService#toggleWebhook");
    }

    @Override
    public void deleteWebhook(long subscriptionId) {
        TodoSupport.notImplemented("AdminService#deleteWebhook");
    }

    @Override
    public PageData<WebhookDeliveryVo> listWebhookDeliveries(int page, int size, String status) {
        return TodoSupport.notImplemented("AdminService#listWebhookDeliveries");
    }

    @Override
    public List<NotificationItemVo> listNotifications() {
        return TodoSupport.notImplemented("AdminService#listNotifications");
    }

    @Override
    public void markNotificationRead(long notificationId) {
        TodoSupport.notImplemented("AdminService#markNotificationRead");
    }

    @Override
    public void markAllNotificationsRead() {
        TodoSupport.notImplemented("AdminService#markAllNotificationsRead");
    }
}
