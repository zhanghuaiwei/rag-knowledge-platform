package com.ragkb.service.modules.connector.service;

import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.connector.vo.ConnectorVo;
import com.ragkb.service.modules.connector.dto.ConnectorCreateDto;
import com.ragkb.service.modules.connector.dto.ConnectorUpdateDto;
import com.ragkb.service.modules.connector.dto.ConnectorValidateDto;
import com.ragkb.service.modules.connector.vo.ConnectorValidateResultVo;
import com.ragkb.service.modules.connector.vo.SyncJobVo;
import com.ragkb.service.modules.connector.dto.SyncDto;

import java.util.List;

/**
 * 内容源连接器用例（实现点由人工完成）。
 */
public interface ConnectorService {

    List<ConnectorVo> listConnectors();

    ConnectorVo createConnector(ConnectorCreateDto request, String idempotencyKey);

    ConnectorVo getConnector(long connectionId);

    ConnectorVo updateConnector(long connectionId, ConnectorUpdateDto request);

    void deleteConnector(long connectionId);

    ConnectorValidateResultVo validateConnector(ConnectorValidateDto request);

    Task syncConnector(long connectionId, SyncDto request, String idempotencyKey);

    SyncJobVo getSyncJob(long jobId);

    void cancelSyncJob(long jobId);
}
