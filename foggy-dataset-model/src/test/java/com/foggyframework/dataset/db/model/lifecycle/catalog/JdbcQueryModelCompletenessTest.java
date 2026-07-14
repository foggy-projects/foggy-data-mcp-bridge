package com.foggyframework.dataset.db.model.lifecycle.catalog;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query_model.JdbcQueryModelBuilder;
import com.foggyframework.dataset.db.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.db.model.proxy.JoinBuilder;
import com.foggyframework.dataset.db.model.proxy.TableModelProxy;
import com.foggyframework.dataset.db.model.spi.DbModelType;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.persistence.criteria.JoinType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Regression coverage for fail-closed QM dependency completeness. */
class JdbcQueryModelCompletenessTest {

    private static final String NAMESPACE = "tenant-a";
    private static final String ROOT_MODEL = "RootModel";
    private static final String MISSING_JOIN_MODEL = "MissingJoinModel";

    @AfterEach
    void clearNamespace() {
        NamespaceContext.clear();
    }

    @Test
    void rootSuccessAndJoinedModelFailureMustRejectTheEntireQueryModel() {
        TableModelLoaderManager tableModelLoaderManager = mock(TableModelLoaderManager.class);
        TableModel rootModel = jdbcTableModel(ROOT_MODEL);
        when(tableModelLoaderManager.load(ROOT_MODEL, NAMESPACE)).thenReturn(rootModel);
        when(tableModelLoaderManager.load(MISSING_JOIN_MODEL, NAMESPACE))
                .thenThrow(new IllegalStateException("controlled missing joined table model"));

        JdbcQueryModelBuilder builder = new JdbcQueryModelBuilder();
        ReflectionTestUtils.setField(builder, "tableModelLoaderManager", tableModelLoaderManager);
        ReflectionTestUtils.setField(builder, "sqlFormulaService", mock(SqlFormulaService.class));
        ReflectionTestUtils.setField(builder, "defaultDataSource", mock(DataSource.class));
        ReflectionTestUtils.setField(builder, "queryExecutionStepExecutor", mock(QueryExecutionStepExecutor.class));

        TableModelProxy rootProxy = new TableModelProxy(ROOT_MODEL);
        TableModelProxy missingJoinProxy = new TableModelProxy(MISSING_JOIN_MODEL);
        DbQueryModelDef definition = new DbQueryModelDef();
        definition.setName("PartialQueryModelProbe");
        definition.setModel(rootProxy);
        definition.setJoins(List.of(new JoinBuilder(rootProxy, missingJoinProxy, JoinType.LEFT)));

        AtomicReference<QueryModelSupport> returnedModel = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        NamespaceContext.setNamespace(NAMESPACE);

        try {
            returnedModel.set(builder.build(definition, mock(Fsscript.class)));
        } catch (Throwable error) {
            failure.set(error);
        }

        assertAll(
                () -> assertNotNull(failure.get(),
                        "a failed joined TM dependency must fail the whole QM build"),
                () -> assertInstanceOf(RuntimeException.class, failure.get(),
                        "dependency failure must surface as a runtime model-build failure"),
                () -> assertNull(returnedModel.get(),
                        "the builder must not return a root-only partial QM")
        );
    }

    @Test
    void customLoaderMustRejectFreshInstanceForAnExistingTableModelSlot() {
        TableModelLoaderManager customManager = mock(TableModelLoaderManager.class);
        CatalogSnapshotStore store = new CatalogSnapshotStore("external-manager-boot");
        QueryModelLoaderImpl loader = new QueryModelLoaderImpl(
                customManager,
                mock(SystemBundlesContext.class),
                mock(FileFsscriptLoader.class),
                List.of(),
                store);
        TableModel firstTable = jdbcTableModel("SharedTableModel");
        TableModel freshTable = jdbcTableModel("SharedTableModel");
        JdbcQueryModelImpl firstQueryModel = new JdbcQueryModelImpl(
                List.of(firstTable), mock(Fsscript.class), null, null);
        firstQueryModel.setName("FirstQueryModel");
        JdbcQueryModelImpl secondQueryModel = new JdbcQueryModelImpl(
                List.of(freshTable), mock(Fsscript.class), null, null);
        secondQueryModel.setName("SecondQueryModel");

        CatalogSnapshot before;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(NAMESPACE)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of("FirstQueryModel", "SecondQueryModel"));
            ReflectionTestUtils.invokeMethod(
                    loader,
                    "stageQueryModel",
                    candidate,
                    "FirstQueryModel",
                    firstQueryModel,
                    false,
                    Set.of());
            before = scope.commit();
        }

        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(NAMESPACE)) {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> ReflectionTestUtils.invokeMethod(
                            loader,
                            "stageQueryModel",
                            scope.candidate(),
                            "SecondQueryModel",
                            secondQueryModel,
                            false,
                            Set.of()));
            assertEquals(
                    "query model references a different table model instance for SharedTableModel",
                    failure.getMessage());
        }

        CatalogSnapshot after = store.current(NAMESPACE).orElseThrow();
        assertSame(before, after);
        assertSame(firstTable, after.tableModels().get("SharedTableModel"));
        assertNull(after.queryModels().get("SecondQueryModel"));
    }

    private TableModel jdbcTableModel(String modelName) {
        QueryObject queryObject = mock(QueryObject.class);
        when(queryObject.getRoot()).thenReturn(queryObject);
        when(queryObject.getAlias()).thenReturn("root_table");

        TableModel tableModel = mock(TableModel.class);
        when(tableModel.getName()).thenReturn(modelName);
        when(tableModel.getModelType()).thenReturn(DbModelType.jdbc);
        when(tableModel.getIdColumn()).thenReturn("id");
        when(tableModel.getAlias()).thenReturn("root_table");
        when(tableModel.getQueryObject()).thenReturn(queryObject);
        return tableModel;
    }
}
