package com.foggyframework.dataset.model.semantic.port;

import java.util.List;
import java.util.Map;

/**
 * Raw SQL execution capability required after a Compose plan is compiled.
 *
 * <p>Governance is deliberately absent here: authority and system slices are
 * applied by {@link ComposeSemanticPlanningPort} before this port receives the
 * final SQL and ordered bind parameters.</p>
 *
 * @since 9.4.0
 */
@FunctionalInterface
public interface ComposeSqlExecutionPort {

    List<Map<String, Object>> executeComposeSql(
            String sql, List<Object> params, String routeModel);
}
