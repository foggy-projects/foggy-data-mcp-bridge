package com.foggyframework.dataviewer.service.tabledefault;

import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfigRequest;

import java.util.Optional;

/**
 * 业务项目可实现该接口，提供租户、角色或系统级表格默认配置。
 */
public interface TableDefaultQueryConfigProvider {

    Optional<TableDefaultQueryConfig> resolve(TableDefaultQueryConfigRequest request);
}
