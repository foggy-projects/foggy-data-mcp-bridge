package com.foggyframework.dataset.model.semantic.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.plugins.result_set_filter.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class PivotCacheProofSessionTest {
    private PivotCacheProofSession session() {
        var signatures = mock(AuthorizationSignatureService.class);
        when(signatures.compute(any()))
                .thenReturn(Optional.of(new AuthorizationSignature("PUBLIC:test", true, null)));
        return new PivotCacheProofSession(signatures);
    }

    private ModelResultContext context() {
        var request = new DbQueryRequestDef();
        request.setQueryModel("SalesQM");
        request.setColumns(List.of("region", "amount"));
        request.setSlice(new ArrayList<>(List.of(new SliceRequestDef("scenario", "=", "sales"))));
        var paging = new PagingRequest<DbQueryRequestDef>();
        paging.setParam(request);
        var context = new ModelResultContext();
        context.setRequest(paging);
        context.setNamespace("tenant-a");
        return context;
    }

    @Test
    void compilationAloneCannotProveDataExecution() {
        var proof = session();
        var context = context();
        var original = PivotCacheProofSession.conditions(context);
        assertTrue(proof.admit(context, original, "v1").isPresent());
        proof.observe(context, original, "v1");
        assertFalse(proof.canStore());
        proof.markExecuted();
        assertTrue(proof.canStore());
    }

    @Test
    void requestPredicateMutationIsBoundToTheActualExecution() {
        var proof = session();
        var prepared = context();
        var original = PivotCacheProofSession.conditions(prepared);
        prepared.getRequest().getParam().getSlice().add(new SliceRequestDef("tenant", "=", "A"));
        assertTrue(proof.admit(prepared, original, "v1").isPresent());
        var actual = context();
        var actualOriginal = PivotCacheProofSession.conditions(actual);
        actual.getRequest().getParam().getSlice().add(new SliceRequestDef("tenant", "=", "B"));
        proof.observe(actual, actualOriginal, "v1");
        proof.markExecuted();
        assertFalse(proof.canStore());
    }

    @Test
    void ambiguousAdmissionAndChangedResultScopeFailClosed() {
        var proof = session();
        var ctx = context();
        var original = PivotCacheProofSession.conditions(ctx);
        assertTrue(proof.admit(ctx, original, "mask:v1:A").isPresent());
        assertTrue(proof.admit(ctx, original, "mask:v1:B").isEmpty());
        proof.observe(ctx, original, "mask:v1:A");
        proof.markExecuted();
        assertFalse(proof.canStore());
        var process = session();
        process.admit(ctx, original, "mask:v1:A");
        process.observe(ctx, original, "mask:v1:A");
        process.markExecuted();
        assertTrue(process.canStore());
        process.observe(ctx, original, "mask:v2:A");
        assertFalse(process.canStore());
    }

    @Test
    void unsupportedValuesAndUnplannedProjectionCannotBeShared() {
        var proof = session();
        var ctx = context();
        var original = PivotCacheProofSession.conditions(ctx);
        proof.admit(ctx, original, "v1");
        ctx.getRequest().getParam().setColumns(List.of("secret"));
        proof.observe(ctx, original, "v1");
        proof.markExecuted();
        assertFalse(proof.canStore());
        ctx.getRequest()
                .getParam()
                .setSlice(List.of(new SliceRequestDef("scope", "=", new Object())));
        assertNull(PivotCacheProofSession.conditions(ctx));
    }

    @Test
    void unknownResultExtensionsAndInheritedAttestationsAreNotShared() {
        var ctx = context();
        assertTrue(
                new DefaultDataSetResultFilterManagerImpl(List.of(new DataSetResultStep() {}))
                        .pivotCacheScope(ctx)
                        .isEmpty());
        assertTrue(
                new DefaultDataSetResultFilterManagerImpl(List.of(new SafeStep()))
                        .pivotCacheScope(ctx)
                        .isPresent());
        assertTrue(
                new DefaultDataSetResultFilterManagerImpl(List.of(new DerivedStep()))
                        .pivotCacheScope(ctx)
                        .isEmpty());
        var inheritedManager =
                new DefaultDataSetResultFilterManagerImpl(List.of(new SafeStep())) {
                    @Override
                    public void process(ModelResultContext context) {
                        context.getExtData().put("masked", true);
                    }
                };
        assertTrue(inheritedManager.pivotCacheScope(ctx).isEmpty());
        assertFalse(inheritedManager.pivotResultProcessingIsIdentity(ctx));
        inheritedManager.process(ctx);
        assertEquals(true, ctx.getExtData().get("masked"));
    }

    @Test
    void brokenContributionDoesNotBreakOrdinaryProcessing() {
        var step =
                new DataSetResultStep() {
                    public Optional<String> pivotCacheScope(ModelResultContext context) {
                        throw new IllegalStateException("unavailable");
                    }

                    public int process(ModelResultContext context) {
                        context.getExtData().put("processed", true);
                        return CONTINUE;
                    }
                };
        var manager = new DefaultDataSetResultFilterManagerImpl(List.of(step));
        var ctx = context();
        assertTrue(manager.pivotCacheScope(ctx).isEmpty());
        manager.process(ctx);
        assertEquals(true, ctx.getExtData().get("processed"));
    }

    static class SafeStep implements DataSetResultStep {
        public Optional<String> pivotCacheScope(ModelResultContext context) {
            return Optional.of("safe-v1");
        }
    }

    static class DerivedStep extends SafeStep {
        public int process(ModelResultContext context) {
            return CONTINUE;
        }
    }
}
