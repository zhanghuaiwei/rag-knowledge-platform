package com.ragkb.service.modules.governance.service.impl;

import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.governance.vo.DeletionReceiptVo;
import com.ragkb.service.modules.governance.vo.DeletionTaskVo;
import com.ragkb.service.modules.governance.vo.LegalHoldVo;
import com.ragkb.service.modules.governance.dto.LegalHoldDto;
import com.ragkb.service.modules.governance.vo.MetadataSchemaVo;
import com.ragkb.service.modules.governance.dto.MetadataSchemaDto;
import com.ragkb.service.modules.governance.vo.RetentionPolicyVo;
import com.ragkb.service.modules.governance.dto.RetentionPolicyDto;
import com.ragkb.service.modules.governance.dto.RetentionPolicyToggleDto;
import com.ragkb.service.modules.governance.dto.ReviewActionDto;
import com.ragkb.service.modules.governance.vo.ReviewItemVo;
import com.ragkb.service.modules.governance.service.GovernanceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 治理中心 scaffold 模式降级实现（无数据库时保持端点 501 语义）。
 *
 * <p>装配互补关系（对齐 access 模块 DenyByDefaultAccessPolicy 的先例）：
 * {@code ragkb.db.enabled=true} 时 {@link GovernanceServiceImpl}（依赖 MyBatis Mapper）注册、
 * 本桩不注册；{@code db.enabled=false}（或未配置）时仅本桩注册 ——
 * 4 个无条件装配的 Controller 在两种模式下都能注入唯一实现。
 */
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "false", matchIfMissing = true)
public class GovernanceServiceScaffoldImpl implements GovernanceService {

    @Override
    public List<MetadataSchemaVo> listMetadataSchemas() {
        // scaffold 模式（无数据库）：治理查询未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#listMetadataSchemas");
    }

    @Override
    public MetadataSchemaVo createMetadataSchema(MetadataSchemaDto request, String idempotencyKey) {
        // scaffold 模式（无数据库）：schema 创建未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#createMetadataSchema");
    }

    @Override
    public MetadataSchemaVo publishMetadataSchema(long schemaId, String idempotencyKey) {
        // scaffold 模式（无数据库）：schema 发布未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#publishMetadataSchema");
    }

    @Override
    public PageData<ReviewItemVo> listReviews(int page, int size) {
        // scaffold 模式（无数据库）：审核队列查询未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#listReviews");
    }

    @Override
    public void approveReview(long reviewId, ReviewActionDto request, String idempotencyKey) {
        // scaffold 模式（无数据库）：审核通过未实现，统一 501。
        TodoSupport.notImplemented("GovernanceService#approveReview");
    }

    @Override
    public void rejectReview(long reviewId, ReviewActionDto request, String idempotencyKey) {
        // scaffold 模式（无数据库）：审核驳回未实现，统一 501。
        TodoSupport.notImplemented("GovernanceService#rejectReview");
    }

    @Override
    public void withdrawDocument(long documentId, String idempotencyKey) {
        // scaffold 模式（无数据库）：文档撤回未实现，统一 501。
        TodoSupport.notImplemented("GovernanceService#withdrawDocument");
    }

    @Override
    public List<RetentionPolicyVo> listRetentionPolicies() {
        // scaffold 模式（无数据库）：保留策略查询未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#listRetentionPolicies");
    }

    @Override
    public RetentionPolicyVo createRetentionPolicy(RetentionPolicyDto request, String idempotencyKey) {
        // scaffold 模式（无数据库）：保留策略创建未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#createRetentionPolicy");
    }

    @Override
    public RetentionPolicyVo toggleRetentionPolicy(long policyId, RetentionPolicyToggleDto request) {
        // scaffold 模式（无数据库）：保留策略启停未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#toggleRetentionPolicy");
    }

    @Override
    public List<LegalHoldVo> listLegalHolds() {
        // scaffold 模式（无数据库）：法律保全查询未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#listLegalHolds");
    }

    @Override
    public LegalHoldVo createLegalHold(LegalHoldDto request, String idempotencyKey) {
        // scaffold 模式（无数据库）：法律保全创建未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#createLegalHold");
    }

    @Override
    public LegalHoldVo releaseLegalHold(long holdId) {
        // scaffold 模式（无数据库）：法律保全解除未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#releaseLegalHold");
    }

    @Override
    public List<DeletionTaskVo> listDeletionTasks() {
        // scaffold 模式（无数据库）：删除任务查询未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#listDeletionTasks");
    }

    @Override
    public DeletionTaskVo approveDeletion(long taskId) {
        // scaffold 模式（无数据库）：删除审批未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#approveDeletion");
    }

    @Override
    public List<DeletionReceiptVo> listDeletionReceipts() {
        // scaffold 模式（无数据库）：删除证明查询未实现，统一 501。
        return TodoSupport.notImplemented("GovernanceService#listDeletionReceipts");
    }
}
