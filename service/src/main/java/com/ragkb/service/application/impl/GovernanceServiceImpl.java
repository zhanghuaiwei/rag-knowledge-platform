package com.ragkb.service.application.impl;

import com.ragkb.service.application.GovernanceService;
import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.common.PageData;
import com.ragkb.service.interfaces.dto.DocumentDtos.Tag;
import com.ragkb.service.interfaces.dto.GovernanceDtos.DeletionReceipt;
import com.ragkb.service.interfaces.dto.GovernanceDtos.DeletionTask;
import com.ragkb.service.interfaces.dto.GovernanceDtos.LegalHold;
import com.ragkb.service.interfaces.dto.GovernanceDtos.LegalHoldInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.MetadataSchema;
import com.ragkb.service.interfaces.dto.GovernanceDtos.MetadataSchemaInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicy;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicyInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicyToggleRequest;
import com.ragkb.service.interfaces.dto.GovernanceDtos.ReviewActionRequest;
import com.ragkb.service.interfaces.dto.GovernanceDtos.ReviewItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 治理中心桩实现（实现点由人工替换）。
 */
@Service
public class GovernanceServiceImpl implements GovernanceService {

    @Override
    public List<MetadataSchema> listMetadataSchemas() {
        return NotYetImplemented.stub("GovernanceService#listMetadataSchemas");
    }

    @Override
    public MetadataSchema createMetadataSchema(MetadataSchemaInput request, String idempotencyKey) {
        return NotYetImplemented.stub("GovernanceService#createMetadataSchema");
    }

    @Override
    public MetadataSchema publishMetadataSchema(long schemaId, String idempotencyKey) {
        return NotYetImplemented.stub("GovernanceService#publishMetadataSchema");
    }

    @Override
    public PageData<ReviewItem> listReviews(int page, int size) {
        return NotYetImplemented.stub("GovernanceService#listReviews");
    }

    @Override
    public void approveReview(long reviewId, ReviewActionRequest request, String idempotencyKey) {
        NotYetImplemented.stub("GovernanceService#approveReview");
    }

    @Override
    public void rejectReview(long reviewId, ReviewActionRequest request, String idempotencyKey) {
        NotYetImplemented.stub("GovernanceService#rejectReview");
    }

    @Override
    public void withdrawDocument(long documentId, String idempotencyKey) {
        NotYetImplemented.stub("GovernanceService#withdrawDocument");
    }

    @Override
    public List<RetentionPolicy> listRetentionPolicies() {
        return NotYetImplemented.stub("GovernanceService#listRetentionPolicies");
    }

    @Override
    public RetentionPolicy createRetentionPolicy(RetentionPolicyInput request, String idempotencyKey) {
        return NotYetImplemented.stub("GovernanceService#createRetentionPolicy");
    }

    @Override
    public RetentionPolicy toggleRetentionPolicy(long policyId, RetentionPolicyToggleRequest request) {
        return NotYetImplemented.stub("GovernanceService#toggleRetentionPolicy");
    }

    @Override
    public List<LegalHold> listLegalHolds() {
        return NotYetImplemented.stub("GovernanceService#listLegalHolds");
    }

    @Override
    public LegalHold createLegalHold(LegalHoldInput request, String idempotencyKey) {
        return NotYetImplemented.stub("GovernanceService#createLegalHold");
    }

    @Override
    public LegalHold releaseLegalHold(long holdId) {
        return NotYetImplemented.stub("GovernanceService#releaseLegalHold");
    }

    @Override
    public List<DeletionTask> listDeletionTasks() {
        return NotYetImplemented.stub("GovernanceService#listDeletionTasks");
    }

    @Override
    public DeletionTask approveDeletion(long taskId) {
        return NotYetImplemented.stub("GovernanceService#approveDeletion");
    }

    @Override
    public List<DeletionReceipt> listDeletionReceipts() {
        return NotYetImplemented.stub("GovernanceService#listDeletionReceipts");
    }
}
