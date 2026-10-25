package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComposeResultGovernanceContractTest {
    static class Mask implements DataSetResultStep {
        public int process(ModelResultContext context) { return CONTINUE; }
        public boolean pivotResultProcessingIsIdentity(ModelResultContext context) { return true; }
    }
    static class Identity extends Mask {
        public boolean composeResultProcessingIsIdentity(ModelResultContext context) { return context != null; }
    }
    static class Inherited extends Identity { }
    static class Broken extends Mask {
        public boolean composeResultProcessingIsIdentity(ModelResultContext context) { throw new IllegalStateException("unproven"); }
    }
    static class BeforeOnly implements DataSetResultStep {
        public int beforeQuery(ModelResultContext context) { return CONTINUE; }
    }
    static class ManagerExtension extends DefaultDataSetResultFilterManagerImpl {
        ManagerExtension() { super(List.of()); }
        public void process(ModelResultContext context) { }
    }
    @Test void unknownAndPivotOnlyDeclarationsFailClosed() {
        assertFalse(new DataSetResultStepExecutor(List.of(new Mask())).composeResultProcessingIsIdentity(new ModelResultContext()));
        assertFalse(new DataSetResultStepExecutor(List.of(new Broken())).composeResultProcessingIsIdentity(new ModelResultContext()));
        assertFalse(new DataSetResultStepExecutor(List.of(new Inherited())).composeResultProcessingIsIdentity(new ModelResultContext()));
        assertFalse(new ManagerExtension().composeResultProcessingIsIdentity(new ModelResultContext()));
    }
    @Test void explicitConcreteIdentityAndBeforeOnlyExtensionsAreSupported() {
        var context=new ModelResultContext();
        assertTrue(new DataSetResultStepExecutor(List.of(new BeforeOnly())).composeResultProcessingIsIdentity(context));
        assertTrue(new DefaultDataSetResultFilterManagerImpl(List.of(new Identity())).composeResultProcessingIsIdentity(context));
        assertFalse(new DefaultDataSetResultFilterManagerImpl(List.of(new Identity())).composeResultProcessingIsIdentity(null));
    }
    @Test void engineOwnedMarkerSurvivesContextCopies() {
        var original=SemanticRequestContext.empty();
        var marked=original.requiringComposeResultIdentity();
        assertFalse(original.isComposeResultIdentityRequired());
        assertTrue(marked.isComposeResultIdentityRequired());
        assertTrue(marked.withGovernance(null,null,null).isComposeResultIdentityRequired());
        assertTrue(marked.withPermissionAction(com.foggyframework.dataset.model.semantic.permission.PermissionAction.EXECUTE).isComposeResultIdentityRequired());
    }
}
