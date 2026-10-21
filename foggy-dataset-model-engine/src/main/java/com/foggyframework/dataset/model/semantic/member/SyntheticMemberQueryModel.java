package com.foggyframework.dataset.model.semantic.member;

import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import java.util.List;

/** Keeps permission evaluation on the same source instance as the compiled member columns. */
final class SyntheticMemberQueryModel extends JdbcQueryModelImpl {
    private final QueryModel source;

    SyntheticMemberQueryModel(QueryModel source, TableModel runtimeTable, SqlFormulaService formulas) {
        super(List.of(runtimeTable), source.getDecorate(QueryModelSupport.class).getFsscript(), formulas,
                source.getDecorate(JdbcQueryModelImpl.class).getDataSource());
        this.source = source;
    }

    QueryModel source() { return source; }
}
