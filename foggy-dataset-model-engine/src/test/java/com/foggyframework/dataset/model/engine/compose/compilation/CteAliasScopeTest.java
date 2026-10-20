package com.foggyframework.dataset.model.engine.compose.compilation;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CteAliasScopeTest {
    @Test void scopesRelationReferencesWithoutChangingLiteralsCommentsOrParameterOrder() {
        assertEquals("SELECT scoped_stage.\"value\", 'FROM stage1', ? FROM scoped_stage -- FROM stage1\nWHERE ? = ?",
                CteAliasScope.rewrite("SELECT stage1.\"value\", 'FROM stage1', ? FROM stage1 -- FROM stage1\nWHERE ? = ?",
                        Map.of("stage1", "scoped_stage")));
    }
    @Test void scopesQuotedRelationsAndJoinsButLeavesPhysicalSchemaQualifiedNames() {
        assertEquals("SELECT x.a FROM \"scoped\" x JOIN [scoped] y ON x.a=y.a JOIN public.stage1 z ON 1=1",
                CteAliasScope.rewrite("SELECT x.a FROM \"stage1\" x JOIN [stage1] y ON x.a=y.a JOIN public.stage1 z ON 1=1",
                        Map.of("stage1", "scoped")));
    }
}
