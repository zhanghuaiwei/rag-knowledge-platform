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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 治理中心桩实现（实现点由人工替换）。
 */
@Service
public class GovernanceServiceImpl implements GovernanceService {

    @Override
    public List<MetadataSchemaVo> listMetadataSchemas() {
        return TodoSupport.notImplemented("GovernanceService#listMetadataSchemas");
    }

    @Override
    public MetadataSchemaVo createMetadataSchema(MetadataSchemaDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("GovernanceService#createMetadataSchema");
    }

    @Override
    public MetadataSchemaVo publishMetadataSchema(long schemaId, String idempotencyKey) {
        return TodoSupport.notImplemented("GovernanceService#publishMetadataSchema");
    }

    @Override
    public PageData<ReviewItemVo> listReviews(int page, int size) {
        return TodoSupport.notImplemented("GovernanceService#listReviews");
    }

    @Override
    public void approveReview(long reviewId, ReviewActionDto request, String idempotencyKey) {
        TodoSupport.notImplemented("GovernanceService#approveReview");
    }

    @Override
    public void rejectReview(long reviewId, ReviewActionDto request, String idempotencyKey) {
        TodoSupport.notImplemented("GovernanceService#rejectReview");
    }

    @Override
    public void withdrawDocument(long documentId, String idempotencyKey) {
        TodoSupport.notImplemented("GovernanceService#withdrawDocument");
    }

    @Override
    public List<RetentionPolicyVo> listRetentionPolicies() {
        return TodoSupport.notImplemented("GovernanceService#listRetentionPolicies");
    }

    @Override
    public RetentionPolicyVo createRetentionPolicy(RetentionPolicyDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("GovernanceService#createRetentionPolicy");
    }

    @Override
    public RetentionPolicyVo toggleRetentionPolicy(long policyId, RetentionPolicyToggleDto request) {
        return TodoSupport.notImplemented("GovernanceService#toggleRetentionPolicy");
    }

    @Override
    public List<LegalHoldVo> listLegalHolds() {
        return TodoSupport.notImplemented("GovernanceService#listLegalHolds");
    }

    @Override
    public LegalHoldVo createLegalHold(LegalHoldDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("GovernanceService#createLegalHold");
    }

    @Override
    public LegalHoldVo releaseLegalHold(long holdId) {
        return TodoSupport.notImplemented("GovernanceService#releaseLegalHold");
    }

    @Override
    public List<DeletionTaskVo> listDeletionTasks() {
        return TodoSupport.notImplemented("GovernanceService#listDeletionTasks");
    }

    @Override
    public DeletionTaskVo approveDeletion(long taskId) {
        return TodoSupport.notImplemented("GovernanceService#approveDeletion");
    }

    @Override
    public List<DeletionReceiptVo> listDeletionReceipts() {
        return TodoSupport.notImplemented("GovernanceService#listDeletionReceipts");
    }
}
