package com.ragkb.service.modules.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragkb.service.modules.document.persistence.entity.Document;
import com.ragkb.service.modules.document.persistence.entity.DocumentAcl;
import com.ragkb.service.modules.document.persistence.mapper.DocumentAclMapper;
import com.ragkb.service.modules.document.persistence.mapper.DocumentMapper;
import com.ragkb.service.modules.document.port.DocumentAccessPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link DocumentAccessPort} 的数据库实现（db.enabled=true 时激活）。
 * 文档 + 该文档 ACL 各一次查询，无循环查库（N+1 零容忍）。
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class DocumentAccessQueryService implements DocumentAccessPort {

    private final DocumentMapper documentMapper;
    private final DocumentAclMapper documentAclMapper;

    public DocumentAccessQueryService(DocumentMapper documentMapper, DocumentAclMapper documentAclMapper) {
        this.documentMapper = documentMapper;
        this.documentAclMapper = documentAclMapper;
    }

    @Override
    public Optional<DocumentAccessView> viewOf(long tenantId, long documentId) {
        Document document = documentMapper.selectOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getTenantId, tenantId)
                .eq(Document::getId, documentId)
                .last("LIMIT 1"));
        if (document == null) {
            return Optional.empty();
        }
        List<AclEntry> acls = documentAclMapper.selectList(new LambdaQueryWrapper<DocumentAcl>()
                        .eq(DocumentAcl::getTenantId, tenantId)
                        .eq(DocumentAcl::getDocumentId, documentId))
                .stream()
                .map(acl -> new AclEntry(acl.getPrincipalType(), acl.getPrincipalKey(), acl.getPermission()))
                .toList();
        return Optional.of(new DocumentAccessView(
                document.getId(), document.getKbId(), document.getLifecycleStatus(),
                document.getIsDisabled(), document.getDelFlag(), document.getPolicyVersion(), acls));
    }
}
