package com.ragkb.service.application;

import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.ConnectorDtos.Connector;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorCreateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorUpdateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorValidateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorValidateResult;
import com.ragkb.service.interfaces.dto.ConnectorDtos.SyncJob;
import com.ragkb.service.interfaces.dto.ConnectorDtos.SyncRequest;

import java.util.List;

/**
 * 内容源连接器用例（实现点由人工完成）。
 */
public interface ConnectorService {

    List<Connector> listConnectors();

    Connector createConnector(ConnectorCreateRequest request, String idempotencyKey);

    Connector getConnector(long connectionId);

    Connector updateConnector(long connectionId, ConnectorUpdateRequest request);

    void deleteConnector(long connectionId);

    ConnectorValidateResult validateConnector(ConnectorValidateRequest request);

    Task syncConnector(long connectionId, SyncRequest request, String idempotencyKey);

    SyncJob getSyncJob(long jobId);

    void cancelSyncJob(long jobId);
}
