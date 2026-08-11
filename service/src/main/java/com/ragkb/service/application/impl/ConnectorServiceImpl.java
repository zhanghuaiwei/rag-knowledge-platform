package com.ragkb.service.application.impl;

import com.ragkb.service.application.ConnectorService;
import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.ConnectorDtos.Connector;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorCreateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorUpdateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorValidateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorValidateResult;
import com.ragkb.service.interfaces.dto.ConnectorDtos.SyncJob;
import com.ragkb.service.interfaces.dto.ConnectorDtos.SyncRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 连接器用例桩实现（实现点由人工替换）。
 */
@Service
public class ConnectorServiceImpl implements ConnectorService {

    @Override
    public List<Connector> listConnectors() {
        return NotYetImplemented.stub("ConnectorService#listConnectors");
    }

    @Override
    public Connector createConnector(ConnectorCreateRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("ConnectorService#createConnector");
    }

    @Override
    public Connector getConnector(long connectionId) {
        return NotYetImplemented.stub("ConnectorService#getConnector");
    }

    @Override
    public Connector updateConnector(long connectionId, ConnectorUpdateRequest request) {
        return NotYetImplemented.stub("ConnectorService#updateConnector");
    }

    @Override
    public void deleteConnector(long connectionId) {
        NotYetImplemented.stub("ConnectorService#deleteConnector");
    }

    @Override
    public ConnectorValidateResult validateConnector(ConnectorValidateRequest request) {
        return NotYetImplemented.stub("ConnectorService#validateConnector");
    }

    @Override
    public Task syncConnector(long connectionId, SyncRequest request, String idempotencyKey) {
        return NotYetImplemented.stub("ConnectorService#syncConnector");
    }

    @Override
    public SyncJob getSyncJob(long jobId) {
        return NotYetImplemented.stub("ConnectorService#getSyncJob");
    }

    @Override
    public void cancelSyncJob(long jobId) {
        NotYetImplemented.stub("ConnectorService#cancelSyncJob");
    }
}
