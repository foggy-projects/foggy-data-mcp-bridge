package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpStatelessDiscoveryTest {
    @Test void statelessEndpointsDoNotAdvertiseAnUnimplementedSubscription() {
        var service = new McpService(mock(McpToolDispatcher.class),mock(ToolFilterService.class),mock(NamespaceToolPolicyService.class));
        var request = new McpRequest();
        for (var response : new com.foggyframework.dataset.mcp.schema.McpResponse[] {
                service.handleInitialize(request,UserRole.ANALYST),service.handleServerDiscover(request,UserRole.ANALYST)}) {
            var result = (Map<?,?>)response.getResult();
            var tools = (Map<?,?>)((Map<?,?>)result.get("capabilities")).get("tools");
            assertThat(tools.get("listChanged")).isEqualTo(false);
        }
    }
}
