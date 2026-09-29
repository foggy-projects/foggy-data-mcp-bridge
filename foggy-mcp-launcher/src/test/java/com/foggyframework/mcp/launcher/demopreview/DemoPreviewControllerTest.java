package com.foggyframework.mcp.launcher.demopreview;

import com.foggyframework.dataviewer.service.QueryCacheService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DemoPreviewControllerTest {

    @Test
    void loginPagePreservesCssPercentSignsAndReplacesOnlyNoncePlaceholder() {
        DemoPreviewProperties properties = new DemoPreviewProperties();
        DemoPreviewController controller = new DemoPreviewController(
                new DemoPreviewSessionService(properties, mock(QueryCacheService.class)), properties);

        var response = controller.loginPage();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("width:100%;"));
        assertTrue(response.getBody().contains("<script nonce=\""));
        assertFalse(response.getBody().contains("%s"));
    }
}
