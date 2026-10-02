package com.foggyframework.mcp.launcher.demopreview;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class DemoPreviewControllerTest {
    @Test void callbackRejectsRemoteHostAndUnknownCredential() {
        var p=new DemoPreviewProperties(); var controller=new DemoPreviewController(new DemoPreviewSessionService(p,null),p);
        var r=new MockHttpServletRequest(); r.setRemoteAddr("10.0.0.1");
        var body=new DemoPreviewController.PermissionRequest(p.getNamespace(),p.getModel(),"EXECUTE");
        assertEquals(403,controller.resolveModelPermissions(body,"invalid",r).getStatusCode().value());
        r.setRemoteAddr("127.0.0.1"); assertFalse(controller.resolveModelPermissions(body,"invalid",r).getBody().allow());
    }
}
