package com.foggyframework.dataviewer.service.tabledefault;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfigRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesTableDefaultQueryConfigProviderTest {

    @Test
    void shouldResolveTenantBeforeRoleAndSystem() {
        DataViewerProperties properties = new DataViewerProperties();
        properties.getTableDefaults().getSystem().put("ticket-list", config("SYSTEM", "sysCol"));
        properties.getTableDefaults().getRoles().put("ops", Map.of("ticket-list", config("ROLE", "roleCol")));
        properties.getTableDefaults().getTenants().put("tenant-a", Map.of("ticket-list", config("TENANT", "tenantCol")));
        PropertiesTableDefaultQueryConfigProvider provider = new PropertiesTableDefaultQueryConfigProvider(properties);

        TableDefaultQueryConfig resolved = provider.resolve(request("tenant-a", List.of("ops"))).orElseThrow();

        assertEquals("TENANT", resolved.getSource());
        assertEquals(List.of("tenantCol"), resolved.getDefaultVisibleColumns());
    }

    @Test
    void shouldResolveFirstMatchingRoleBeforeSystem() {
        DataViewerProperties properties = new DataViewerProperties();
        properties.getTableDefaults().getSystem().put("ticket-list", config("SYSTEM", "sysCol"));
        properties.getTableDefaults().getRoles().put("viewer", Map.of("ticket-list", config("ROLE_VIEWER", "viewerCol")));
        properties.getTableDefaults().getRoles().put("ops", Map.of("ticket-list", config("ROLE_OPS", "opsCol")));
        PropertiesTableDefaultQueryConfigProvider provider = new PropertiesTableDefaultQueryConfigProvider(properties);

        TableDefaultQueryConfig resolved = provider.resolve(request(null, List.of("ops", "viewer"))).orElseThrow();

        assertEquals("ROLE_OPS", resolved.getSource());
        assertEquals(List.of("opsCol"), resolved.getDefaultVisibleColumns());
    }

    @Test
    void shouldFallbackToQueryModelKey() {
        DataViewerProperties properties = new DataViewerProperties();
        properties.getTableDefaults().getSystem().put("TicketModel", TableDefaultQueryConfig.builder()
                .queryModel("TicketModel")
                .defaultVisibleColumns(List.of("modelCol"))
                .build());
        PropertiesTableDefaultQueryConfigProvider provider = new PropertiesTableDefaultQueryConfigProvider(properties);

        TableDefaultQueryConfig resolved = provider.resolve(request(null, List.of())).orElseThrow();

        assertEquals("SYSTEM", resolved.getSource());
        assertEquals(List.of("modelCol"), resolved.getDefaultVisibleColumns());
    }

    private TableDefaultQueryConfig config(String source, String column) {
        return TableDefaultQueryConfig.builder()
                .tableInstanceId("ticket-list")
                .queryModel("TicketModel")
                .defaultVisibleColumns(List.of(column))
                .source(source)
                .build();
    }

    private TableDefaultQueryConfigRequest request(String tenantId, List<String> roleIds) {
        return TableDefaultQueryConfigRequest.builder()
                .queryModel("TicketModel")
                .tableInstanceId("ticket-list")
                .tenantId(tenantId)
                .roleIds(roleIds)
                .build();
    }
}
