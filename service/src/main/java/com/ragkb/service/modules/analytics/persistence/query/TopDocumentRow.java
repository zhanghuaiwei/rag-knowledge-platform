package com.ragkb.service.modules.analytics.persistence.query;

/**
 * 热门文档聚合行（事实源：{@code chat_message_source} JOIN {@code document}/{@code kb}）。
 *
 * <p>qaCount = 被多少条不同回答引用（COUNT(DISTINCT message_id)）；
 * searchCount = 检索来源命中总次数（COUNT(*)，同一回答引用多次重复计）。
 */
public class TopDocumentRow {

    /** 文档 id（chat_message_source.document_id → document.id）。 */
    private Long documentId;

    /** 文件名（document.file_name，前端列表展示与跳转详情用）。 */
    private String fileName;

    /** 所属知识库名（document.kb_id → kb.name；库被删则 LEFT JOIN 为 null）。 */
    private String kbName;

    /** 引用该文档的回答条数（去重 message_id）。 */
    private Long qaCount;

    /** 该文档作为检索来源出现的总次数。 */
    private Long searchCount;

    /** 读取文档 id。 */
    public Long getDocumentId() {
        return documentId;
    }

    /** 设置文档 id（SQL 聚合结果回填）。 */
    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    /** 读取文件名。 */
    public String getFileName() {
        return fileName;
    }

    /** 设置文件名（SQL 聚合结果回填）。 */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** 读取知识库名。 */
    public String getKbName() {
        return kbName;
    }

    /** 设置知识库名（SQL 聚合结果回填）。 */
    public void setKbName(String kbName) {
        this.kbName = kbName;
    }

    /** 读取引用回答条数。 */
    public Long getQaCount() {
        return qaCount;
    }

    /** 设置引用回答条数（SQL 聚合结果回填）。 */
    public void setQaCount(Long qaCount) {
        this.qaCount = qaCount;
    }

    /** 读取来源命中总次数。 */
    public Long getSearchCount() {
        return searchCount;
    }

    /** 设置来源命中总次数（SQL 聚合结果回填）。 */
    public void setSearchCount(Long searchCount) {
        this.searchCount = searchCount;
    }
}
