package com.foggyframework.dataset.model.plugins.query_execution;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ComposeExecutionGovernanceContractTest {
    QueryExecutionContext context() {var c=new QueryExecutionContext();c.setSql("SELECT ? AS value");c.setParams(new ArrayList<>(List.of(1)));return c;}
    @Test void unknownBeforeAndAfterHooksFailClosedBeforeTheyRun() {
        AtomicInteger calls=new AtomicInteger();
        for(QueryExecutionStep step:List.of(new QueryExecutionStep(){public int beforeExecute(QueryExecutionContext c){calls.incrementAndGet();return CONTINUE;}},
                new QueryExecutionStep(){public int afterExecute(QueryExecutionContext c){calls.incrementAndGet();return CONTINUE;}},
                new QueryExecutionStep(){public int afterExecute(QueryExecutionPhase p,QueryExecutionContext c){calls.incrementAndGet();return CONTINUE;}})) {
            assertTrue(assertThrows(IllegalArgumentException.class,()->new QueryExecutionStepExecutor(List.of(step)).prepareComposeSql(context())).getMessage().contains("COMPOSE_EXECUTION_GOVERNANCE_UNPROVEN"));
        }
        assertEquals(0,calls.get());
    }
    static class Safe implements QueryExecutionStep {
        public boolean composeSqlOnlyIsSafe(QueryExecutionContext c){return c!=null;}

        public int prepareComposeSql(QueryExecutionContext c){c.setExtData("checked",true);return CONTINUE;}
    }
    @Test void pureOptInAuthorizationRunsIncludingAnAopProxy() {
        for(QueryExecutionStep step:List.of(new Safe(),(QueryExecutionStep)new ProxyFactory(new Safe()).getProxy())) {
            var c=context();new QueryExecutionStepExecutor(List.of(step)).prepareComposeSql(c);assertEquals(true,c.getExtData("checked"));assertEquals("SELECT ? AS value",c.getSql());
        }
    }
    @Test void subclassesCannotInheritAnAttestationForChangedBehavior() {
        assertThrows(IllegalArgumentException.class,()->new QueryExecutionStepExecutor(List.of(new Safe(){public int prepareComposeSql(QueryExecutionContext c){throw new AssertionError("must not run");}})).prepareComposeSql(context()));
    }
    @Test void optInDenialIsNotSkipped() {
        QueryExecutionStep deny=new QueryExecutionStep(){
            public boolean composeSqlOnlyIsSafe(QueryExecutionContext c){return true;}

            public int prepareComposeSql(QueryExecutionContext c){throw new IllegalStateException("PURE_POLICY_DENIED");}};
        assertEquals("PURE_POLICY_DENIED",assertThrows(IllegalStateException.class,()->new QueryExecutionStepExecutor(List.of(deny)).prepareComposeSql(context())).getMessage());
    }
    @Test void prepareCannotRewriteOrShortCircuitTheRelation() {
        for(String mutation:List.of("sql","params","cached","skip","abort")) {
            QueryExecutionStep bad=new QueryExecutionStep(){
                public boolean composeSqlOnlyIsSafe(QueryExecutionContext c){return true;}

                public int prepareComposeSql(QueryExecutionContext c){
                    switch(mutation){case "sql"->c.setSql("SELECT 2");case "params"->c.getParams().set(0,2);
                        case "cached"->c.setCachedResult(new com.foggyframework.dataset.model.PagingResultImpl());case "skip"->c.setSkipExecution(true);case "abort"->{return ABORT;}}
                    return CONTINUE;}};
            assertThrows(IllegalArgumentException.class,()->new QueryExecutionStepExecutor(List.of(bad)).prepareComposeSql(context()),mutation);
        }
    }
    @Test void exceptionAndCustomExecutorFailClosed() {
        QueryExecutionStep bad=new QueryExecutionStep(){public boolean composeSqlOnlyIsSafe(QueryExecutionContext c){throw new IllegalStateException("unknown policy");}};
        assertThrows(IllegalArgumentException.class,()->new QueryExecutionStepExecutor(List.of(bad)).prepareComposeSql(context()));
        assertThrows(IllegalArgumentException.class,()->new QueryExecutionStepExecutor(List.of()){}.prepareComposeSql(context()));
    }
    @Test void builtInPhysicalCheckRunsWhileCacheAndOptimizationAreOmitted() {
        var c=context();new QueryExecutionStepExecutor(List.of(new PhysicalColumnPermissionStep(),new L2CacheStep(),new PreAggRewriteStep(null))).prepareComposeSql(c);
        assertFalse(c.isSkipExecution());assertEquals(List.of(1),c.getParams());assertEquals("SELECT ? AS value",c.getSql());
    }
}
