package com.ragkb.service.modules.connector.service.impl;

import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.connector.vo.ConnectorVo;
import com.ragkb.service.modules.connector.dto.ConnectorCreateDto;
import com.ragkb.service.modules.connector.dto.ConnectorUpdateDto;
import com.ragkb.service.modules.connector.dto.ConnectorValidateDto;
import com.ragkb.service.modules.connector.vo.ConnectorValidateResultVo;
import com.ragkb.service.modules.connector.vo.SyncJobVo;
import com.ragkb.service.modules.connector.dto.SyncDto;
import com.ragkb.service.modules.connector.service.ConnectorService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 连接器用例桩实现（实现点由人工替换）。
 */
@Service
public class ConnectorServiceImpl implements ConnectorService {

    @Override
    public List<ConnectorVo> listConnectors() {
        return TodoSupport.notImplemented("ConnectorService#listConnectors");
    }

    @Override
    public ConnectorVo createConnector(ConnectorCreateDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("ConnectorService#createConnector");
    }

    @Override
    public ConnectorVo getConnector(long connectionId) {
        return TodoSupport.notImplemented("ConnectorService#getConnector");
    }

    @Override
    public ConnectorVo updateConnector(long connectionId, ConnectorUpdateDto request) {
        return TodoSupport.notImplemented("ConnectorService#updateConnector");
    }

    @Override
    public void deleteConnector(long connectionId) {
        TodoSupport.notImplemented("ConnectorService#deleteConnector");
    }

    @Override
    public ConnectorValidateResultVo validateConnector(ConnectorValidateDto request) {
        return TodoSupport.notImplemented("ConnectorService#validateConnector");
    }

    @Override
    public Task syncConnector(long connectionId, SyncDto request, String idempotencyKey) {
        return TodoSupport.notImplemented("ConnectorService#syncConnector");
    }

    @Override
    public SyncJobVo getSyncJob(long jobId) {
        return TodoSupport.notImplemented("ConnectorService#getSyncJob");
    }

    @Override
    public void cancelSyncJob(long jobId) {
        TodoSupport.notImplemented("ConnectorService#cancelSyncJob");
    }
}
