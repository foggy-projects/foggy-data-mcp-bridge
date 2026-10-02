package com.foggyframework.mcp.launcher.demopreview;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DemoPreviewSessionServiceTest {
    private final String auth="Bearer synthetic-test-credential";
    private DemoPreviewProperties properties() {
        var p=new DemoPreviewProperties(); var scope=new DemoPreviewProperties.DemoUser();
        scope.setStationCodes(new ArrayList<>(List.of("HZ-01","NB-01")));
        p.getUsers().put("north",scope); p.getCredentialPrincipals().put(DemoPreviewSessionService.fingerprint(auth),"north"); return p;
    }
    private CachedQueryContext query() { return CachedQueryContext.builder().namespace("harness_demo_logistics").model("DemoWaybillQueryModel").build(); }
    @Test void verifiesCredentialAndRechecksRevocationAndScopeChanges() {
        var p=properties(); var service=new DemoPreviewSessionService(p,null); var identity=service.verify(auth,query());
        assertThrows(SecurityException.class,()->service.verify("invalid",query()));
        assertDoesNotThrow(()->service.revalidate(identity,query()));
        p.getUsers().get("north").getStationCodes().remove("NB-01");
        assertThrows(SecurityException.class,()->service.revalidate(identity,query()));
        p.getCredentialPrincipals().clear(); assertThrows(SecurityException.class,()->service.verify(auth,query()));
    }
    @Test void signedRuntimeReferenceRetainsEngineScopeWithoutOriginalToken() {
        var p=properties(); var service=new DemoPreviewSessionService(p,null); var identity=service.verify(auth,query());
        String runtime=service.runtimeAuthorization(identity); assertFalse(runtime.contains(auth));
        assertTrue(service.resolveAuthorization(runtime,query().getNamespace(),query().getModel(),"EXECUTE").allow());
        assertEquals(List.of("HZ-01","NB-01"),service.resolveAuthorization(runtime,query().getNamespace(),query().getModel(),"DESCRIBE").stationCodes());
        assertFalse(service.resolveAuthorization(runtime+"forged",query().getNamespace(),query().getModel(),"EXECUTE").allow());
        assertFalse(service.resolveAuthorization(runtime,"other",query().getModel(),"EXECUTE").allow());
        assertFalse(service.resolveAuthorization(runtime,query().getNamespace(),"other","EXECUTE").allow());
        assertFalse(service.resolveAuthorization(runtime,query().getNamespace(),query().getModel(),"DELETE").allow());
        p.getCredentialPrincipals().clear(); assertFalse(service.resolveAuthorization(runtime,query().getNamespace(),query().getModel(),"EXECUTE").allow());
    }
}
