package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.expression.QueryCalculationException;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/** #162: same requests and independent numeric oracle on every supported JDBC database. */
@TestPropertySource(properties = "pivot.validation.regression=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PivotValidationRegressionIT extends EcommerceTestSupport {
    @Resource SemanticQueryServiceV3 service;
    @Resource SemanticQueryPayloadMapper mapper;
    @Resource SystemBundlesContext bundles;
    @Resource com.fasterxml.jackson.databind.ObjectMapper json;
    final AtomicInteger evidenceSequence = new AtomicInteger();
    @TempDir static Path root;
    static final String TABLE = "issue162_facts", NODES = "issue162_nodes", BUNDLE = "pivot-validation-regression";

    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE " + NODES + " (id INTEGER PRIMARY KEY, parent_id INTEGER, caption VARCHAR(30), title VARCHAR(30))");
        jdbcTemplate.execute("CREATE TABLE " + TABLE + " (id INTEGER PRIMARY KEY, node_id INTEGER, category VARCHAR(10), leaf VARCHAR(10), ym VARCHAR(10), amount DECIMAL(12,2), secret INTEGER)");
        jdbcTemplate.update("INSERT INTO " + NODES + " VALUES (1,NULL,'Root','Director'),(2,1,'Left','Manager'),(3,1,'Right','Manager'),(4,2,'Leaf','Staff')");
        jdbcTemplate.update("INSERT INTO " + TABLE + " VALUES (1,1,'P','a','2024-01',10,1),(2,2,'P','a','2024-01',20,1),(3,2,'P','b','2024-01',10,1),(4,2,'P','a','2024-02',10,1),(5,3,'P','b','2024-02',40,1),(6,3,'Q','c','2024-01',30,2),(7,3,'Q','c','2024-02',60,2),(8,4,'Q','d','2024-02',0,2),(9,4,'Z','z','2024-01',0,2),(10,4,'Z','z','2024-02',0,2)");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/PivotValidation.tm"), """
            export const model={name:'PivotValidation',tableName:'issue162_facts',idColumn:'id',
              properties:[{name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'},
                {name:'leaf',column:'leaf',type:'STRING'},{name:'month',column:'ym',type:'STRING'},
                {name:'secret',column:'secret',type:'INTEGER'}],
              dimensions:[{name:'node',tableName:'issue162_nodes',foreignKey:'node_id',primaryKey:'id',captionColumn:'caption',
                properties:[{name:'parentId',column:'parent_id',type:'INTEGER'},{name:'title',column:'title',type:'STRING'}]}],
              measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'},
                {name:'average',column:'amount',type:'NUMBER',aggregation:'avg'}]};
            """);
        String fields = "{ref:m.id},{ref:m.category},{ref:m.leaf},{ref:m.month},{ref:m.secret},{ref:m.node},{ref:m.amount},{ref:m.average}";
        qm("Base", fields, "");
        qm("Renamed", "{ref:m.id},{ref:m.category,name:'region',alias:'regionOut'},{ref:m.leaf},{ref:m.month},{ref:m.node},{ref:m.amount,name:'revenue',alias:'revenueOut'}", "");
        qm("Formula", fields + ",{name:'doubled',alias:'doubleOut',type:'NUMBER',formula:'sum(amount) * 2'}", "");
        qm("Denied", fields, ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:false};}}");
        qm("ValidateOnly", fields, ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:ctx.action === 'VALIDATE'};}}");
        qm("ExecuteOnly", fields, ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:ctx.action === 'EXECUTE'};}}");
        qm("Filtered", fields, ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:true,rowPredicates:[ctx.predicate.eq('category','P')]};}}");
        assertTrue(bundles.addExternalBundle(BUNDLE, "", root.toString(), false));
    }

    @AfterAll void cleanup() {
        if (bundles.containBundle(BUNDLE)) bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");
        jdbcTemplate.execute("DROP TABLE " + TABLE); jdbcTemplate.execute("DROP TABLE " + NODES);
    }

    void qm(String suffix, String fields, String extra) throws Exception {
        String name = "PivotValidation" + suffix;
        Files.writeString(root.resolve("query/" + name + ".qm"), "const m=loadTableModel('PivotValidation');export const queryModel={name:'" + name
                + "',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:[" + fields + "]}],accesses:[]" + extra + "};");
    }

    Map<String,Object> request(List<?> rows, List<?> columns, List<?> metrics, String format) {
        Map<String,Object> pivot = new LinkedHashMap<>(); pivot.put("rows", rows);
        if (columns != null) pivot.put("columns", columns);
        pivot.put("metrics", metrics); pivot.put("outputFormat", format);
        pivot.put("options", Map.of("grandTotal", false));
        return new LinkedHashMap<>(Map.of("pivot", pivot));
    }
    Map<String,Object> basic(String format) { return request(List.of("category"), List.of("month"), List.of("amount"), format); }
    @SuppressWarnings("unchecked") Map<String,Object> pivot(Map<String,Object> request) { return (Map<String,Object>) request.get("pivot"); }
    SemanticQueryResponse call(String model, Map<String,Object> request, String mode, SemanticRequestContext context) {
        var response = service.queryModel("PivotValidation" + model, mapper.toQueryRequest(request), mode, context);
        String evidence = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        if (evidence != null) {
            try {
                Path directory = Files.createDirectories(Path.of(evidence).resolve("pivot-validation-calls"));
                Files.writeString(directory.resolve(evidenceSequence.incrementAndGet() + "-" + model + "-" + mode + ".json"),
                        json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("model",model,"mode",mode,"request",request,"response",response)));
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }
        return response;
    }
    SemanticQueryResponse checked(String model, Map<String,Object> request, SemanticRequestContext context) {
        assertNotNull(call(model, request, "validate", context));
        assertNotNull(service.validateQuery("PivotValidation" + model, mapper.toQueryRequest(request), context));
        return call(model, request, "execute", context);
    }
    SemanticQueryResponse checked(Map<String,Object> request) { return checked("Base", request, SemanticRequestContext.empty()); }
    void rejected(String model, Map<String,Object> request, SemanticRequestContext context) {
        for (String mode : List.of("validate", "execute")) assertThrows(RuntimeException.class, () -> call(model, request, mode, context), mode + ": " + request);
        assertThrows(RuntimeException.class, () -> service.validateQuery("PivotValidation" + model, mapper.toQueryRequest(request), context));
    }
    void rejected(Map<String,Object> request) { rejected("Base", request, SemanticRequestContext.empty()); }
    SemanticRequestContext deny(String table, String column) {
        return SemanticRequestContext.ofDeniedColumns(null, null, List.of(new DeniedPhysicalColumn(null, table, column)));
    }
    void number(String expected, Object actual) {
        assertNotNull(actual); assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())), String.valueOf(actual));
    }
    Map<String,Map<String,Object>> index(List<Map<String,Object>> rows, String... fields) {
        Map<String,Map<String,Object>> result = new LinkedHashMap<>();
        for (var row : rows) {
            String key = String.join("/", Arrays.stream(fields).map(f -> String.valueOf(row.get(f))).toList());
            assertNull(result.put(key, row), "duplicate cell: " + key);
        }
        return result;
    }

    @Test void flatGridAndTotalsHaveTheSameValidatedCells() {
        var flat = index(checked(basic("flat")).getItems(), "category", "month");
        assertEquals(6, flat.size());
        for (var e : Map.of("P/2024-01", "40", "P/2024-02", "50", "Q/2024-01", "30", "Q/2024-02", "60", "Z/2024-01", "0", "Z/2024-02", "0").entrySet()) number(e.getValue(), flat.get(e.getKey()).get("amount"));
        var grid = checked(basic("grid")).getItems().get(0);
        verifyGridCells(grid, flat, false);
        var totals = basic("grid"); pivot(totals).put("options", Map.of("grandTotal", true));
        verifyGridCells(checked(totals).getItems().get(0), flat, true);
    }
    @SuppressWarnings("unchecked")
    void verifyGridCells(Map<String,Object> grid, Map<String,Map<String,Object>> flat, boolean totals) {
        var rows = (List<Map<String,Object>>) grid.get("rowHeaders");
        var columns = (List<Map<String,Object>>) grid.get("columnHeaders");
        var cells = (List<List<Object>>) grid.get("cells");
        assertEquals(totals ? 4 : 3, rows.size()); assertEquals(2, columns.size());
        Set<String> checked = new HashSet<>();
        for (int i=0;i<rows.size();i++) for (int j=0;j<columns.size();j++) {
            String category = String.valueOf(rows.get(i).get("category")), month = String.valueOf(columns.get(j).get("month"));
            String coordinate = category + "/" + month;
            assertTrue(checked.add(coordinate), "duplicate cell: " + coordinate);
            if ("GRAND_TOTAL".equals(category)) number(month.equals("2024-01") ? "70" : "110",cells.get(i).get(j));
            else number(flat.get(coordinate).get("amount").toString(),cells.get(i).get(j));
        }
        assertTrue(checked.containsAll(flat.keySet()));
    }
    String normalizeNumbers(Object value) {
        if (value instanceof Number) return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        if (value instanceof List<?> list) return "[" + String.join(", ", list.stream().map(this::normalizeNumbers).toList()) + "]";
        return String.valueOf(value);
    }

    @Test void inputNamesAndOutputAliasesRemainDistinctAcrossBothRoutes() {
        var p = request(List.of("region"), List.of("month"), List.of("revenue"), "flat");
        var rows = checked("Renamed", p, SemanticRequestContext.empty()).getItems();
        number("40", index(rows,"region","month").get("P/2024-01").get("revenue"));
        assertTrue(rows.stream().noneMatch(r -> r.containsKey("revenueOut") || r.containsKey("regionOut")));
        rejected("Renamed", request(List.of("regionOut"), null, List.of("revenue"), "flat"), SemanticRequestContext.empty());
        rejected("Renamed", request(List.of("region"), null, List.of("revenueOut"), "flat"), SemanticRequestContext.empty());
        rejected("Renamed", request(List.of("category"), null, List.of("amount"), "flat"), SemanticRequestContext.empty());
    }

    @Test void formulaBindingAndSourcePermissionsAreShared() {
        var p = request(List.of("category"), List.of("month"), List.of("doubled"), "flat");
        number("80", index(checked("Formula", p, SemanticRequestContext.empty()).getItems(),"category","month").get("P/2024-01").get("doubled"));
        rejected("Formula", p, deny(TABLE,"amount"));
        rejected("Formula", p, SemanticRequestContext.of(null, (ModelResultContext.SecurityContext) null, Set.of("category","month","doubled")));
        var inline = basic("flat"); pivot(inline).put("metrics",List.of("privateTotal"));
        inline.put("calculatedFields", List.of(Map.of("name","privateTotal","expression","sum(secret)","alias","privateOutput")));
        checked(inline);
        rejected("Base", inline, deny(TABLE,"secret"));
    }

    @Test void invalidAxesMetricsAndIgnoredPivotPayloadsAreRejectedConsistently() {
        rejected(request(List.of("missingAxis"), null, List.of("amount"), "flat"));
        rejected(request(List.of("category"), null, List.of("missingMetric"), "flat"));
        var p = basic("flat"); p.put("columns",List.of("id")); rejected(p);
        p = basic("flat"); p.put("timeWindow",Map.of("comparison","yoy")); rejected(p);
        p = basic("flat"); p.put("limit",1); rejected(p);
        p = basic("flat"); p.put("orderBy",List.of("amount")); rejected(p);
        rejected(request(List.of(), null, List.of("amount"), "flat"));
        rejected(request(List.of("category"), null, List.of(), "flat"));
        rejected(request(List.of(Map.of("field","category","limit",1,"orderBy",List.of("-missing"))), null, List.of("amount"), "flat"));
    }

    @Test void ordinaryQueriesStillRequireColumns() {
        rejected(new LinkedHashMap<>());
        checked("Base", Map.of("columns",List.of("id"),"limit",1), SemanticRequestContext.empty());
    }

    @Test void axisControlDiagnosticsPointAtThePublicPivotReference() {
        var p = request(List.of(Map.of("field","category","limit",1,"orderBy",List.of("-missing"))), null, List.of("amount"), "flat");
        for (String mode : List.of("validate","execute")) {
            var error = assertThrows(RuntimeException.class, () -> call("Base",p,mode,SemanticRequestContext.empty()));
            var diagnostic = QueryCalculationException.find(error); assertNotNull(diagnostic);
            assertEquals("pivot.rows[0].orderBy[0]", diagnostic.getPath());
        }
    }

    @Test void topNAndDomainWindowPreserveValidatedValues() {
        var p = request(List.of(Map.of("field","category","limit",1,"orderBy",List.of("-amount"))), List.of("month"), List.of("amount"), "flat");
        var rows = checked(p).getItems(); assertEquals(2, rows.size()); assertTrue(rows.stream().allMatch(r -> "Q".equals(r.get("category")) || "P".equals(r.get("category")))) ;
        // P and Q both total 90; select deterministically through a domain predicate for the window oracle.
        p = request(List.of(Map.of("field","category","domainSlice",List.of(Map.of("field","category","op","=","value","P")),"limit",1)), List.of("month"), List.of("amount"), "flat");
        var byMonth = index(checked(p).getItems(),"month"); assertEquals(2,byMonth.size());
        number("40", byMonth.get("2024-01").get("amount")); number("50",byMonth.get("2024-02").get("amount"));
    }

    Map<String,Object> tree() { return request(List.of(Map.of("field","node$caption","hierarchyMode","tree")), null, List.of("amount"), "tree"); }
    @Test void treeValuesAreDirectNodeAggregatesAndChildrenAreNotDoubleCounted() {
        var p = tree(); pivot(p).put("options",Map.of("grandTotal",true,"rowSubtotals",true));
        var result = checked(p).getItems().get(0);
        @SuppressWarnings("unchecked") var roots = (List<PivotResult.TreeNode>) result.get("data");
        assertEquals(1,roots.size()); var rootNode = roots.get(0); assertEquals("Root",rootNode.getNode().get("node$caption"));
        number("10",rootNode.getCells().get("amount"));
        Map<String,PivotResult.TreeNode> children = new HashMap<>(); for (var n:rootNode.getChildren()) children.put((String)n.getNode().get("node$caption"),n);
        assertEquals(Set.of("Left","Right"),children.keySet());
        number("40",children.get("Left").getCells().get("amount")); number("130",children.get("Right").getCells().get("amount"));
        assertEquals("Leaf",children.get("Left").getChildren().get(0).getNode().get("node$caption"));
        number("0",children.get("Left").getChildren().get(0).getCells().get("amount"));
    }

    @Test void treeSkeletonFieldsAndPropertiesRespectPermissions() {
        rejected("Base",tree(),deny(NODES,"parent_id"));
        // Ordinary QM dimension permissions intentionally grant the dimension group, including parentId.
        checked("Base",tree(),SemanticRequestContext.of(null,(ModelResultContext.SecurityContext)null,Set.of("node$caption","node$id","amount")));
        var p = request(List.of("node$id"),null,List.of("amount"),"flat"); pivot(p).put("properties",List.of("node$title"));
        var rows = checked(p).getItems(); assertEquals(4,rows.size()); assertTrue(rows.stream().allMatch(r -> r.containsKey("node$title")));
        rejected("Base",p,deny(NODES,"title"));
        pivot(p).put("rows",List.of("node$caption")); rejected(p);
    }

    @Test void modelAndFieldPermissionsAndFilterDependenciesMatch() {
        rejected("Denied",basic("flat"),SemanticRequestContext.empty());
        rejected("Base",basic("flat"),deny(TABLE,"amount"));
        rejected("Base",basic("flat"),SemanticRequestContext.of(null,(ModelResultContext.SecurityContext)null,Set.of("category","month")));
        var p = basic("flat"); p.put("slice",List.of(Map.of("field","secret","op","=","value",1)));
        rejected("Base",p,deny(TABLE,"secret"));
        p = request(List.of(Map.of("field","category","domainSlice",List.of(Map.of("field","secret","op","=","value",1)))),null,List.of("amount"),"flat");
        rejected("Base",p,deny(TABLE,"secret"));
    }

    @Test void validateAndExecuteKeepTheirDistinctPermissionActions() {
        assertNotNull(call("ValidateOnly",basic("flat"),"validate",SemanticRequestContext.empty()));
        assertNotNull(service.validateQuery("PivotValidationValidateOnly",mapper.toQueryRequest(basic("flat")),SemanticRequestContext.empty()));
        assertThrows(RuntimeException.class,()->call("ValidateOnly",basic("flat"),"execute",SemanticRequestContext.empty()));
        assertThrows(RuntimeException.class,()->call("ExecuteOnly",basic("flat"),"validate",SemanticRequestContext.empty()));
        assertNotNull(call("ExecuteOnly",basic("flat"),"execute",SemanticRequestContext.empty()));
    }

    @Test void modelRowPredicatesAndSystemSlicesKeepTheSameAggregationScope() {
        var rows = checked("Filtered",basic("flat"),SemanticRequestContext.empty()).getItems();
        assertEquals(2,rows.size()); assertTrue(rows.stream().allMatch(r -> "P".equals(r.get("category"))));
        SliceRequestDef slice = new SliceRequestDef(); slice.setField("category");slice.setOp("=");slice.setValue("P");
        var context = SemanticRequestContext.of(null,null,null,null,List.of(slice));
        var actual = checked("Base",basic("flat"),context).getItems();
        assertEquals(normalizeNumbers(rows.stream().map(r -> r.get("amount")).toList()),normalizeNumbers(actual.stream().map(r -> r.get("amount")).toList()));
    }

    Map<String,Object> parentShare(boolean paged) {
        var share = new LinkedHashMap<String,Object>(Map.of("name","share","type","parentShare","of","amount","axis","rows","level","leaf","parentLevel","category"));
        if (paged) share.put("denominatorScope","prePageParent");
        Object child = paged ? Map.of("field","leaf","limit",1,"orderBy",List.of("-amount")) : "leaf";
        return request(List.of("category",child),List.of("month"),List.of("amount",share),"flat");
    }
    @Test void parentShareUsesEachParentAndColumnDenominatorAndZeroReturnsNull() {
        var rows = index(checked(parentShare(false)).getItems(),"category","leaf","month");
        number("0.75",rows.get("P/a/2024-01").get("share")); number("0.25",rows.get("P/b/2024-01").get("share"));
        number("0.2",rows.get("P/a/2024-02").get("share")); number("0.8",rows.get("P/b/2024-02").get("share"));
        number("1",rows.get("Q/c/2024-01").get("share")); assertNull(rows.get("Z/z/2024-01").get("share"));
        var paged = index(checked(parentShare(true)).getItems(),"category","leaf","month");
        assertEquals(Set.of("P/b/2024-01","P/b/2024-02","Q/c/2024-01","Q/c/2024-02","Z/z/2024-01","Z/z/2024-02"),paged.keySet(),
                "A selected child retains all month cells; TopN is a member window, not a record limit");
        for (var e:paged.entrySet()) {
            Object expected = rows.get(e.getKey()).get("share");
            if (expected == null) assertNull(e.getValue().get("share")); else number(expected.toString(),e.getValue().get("share"));
        }
        assertTrue(paged.size()<rows.size());
    }

    Map<String,Object> baseline(String firstOrLast, boolean paged) {
        var ratio = new LinkedHashMap<String,Object>(Map.of("name","index","type","baselineRatio","of","amount","axis","columns","baseline",firstOrLast));
        if (paged) ratio.put("baselineScope","prePageAxisDomain");
        Object column = paged ? Map.of("field","month","start",1,"limit",1) : "month";
        return request(List.of("category"),List.of(column),List.of("amount",ratio),"flat");
    }
    @Test void baselineFirstLastAndPrePageDomainAreNumericallyVerified() {
        for (String baseline:List.of("first","last")) {
            var full = index(checked(baseline(baseline,false)).getItems(),"category","month");
            number(baseline.equals("first")?"1":"0.8",full.get("P/2024-01").get("index"));
            number(baseline.equals("first")?"1.25":"1",full.get("P/2024-02").get("index"));
            number(baseline.equals("first")?"1":"0.5",full.get("Q/2024-01").get("index"));
            number(baseline.equals("first")?"2":"1",full.get("Q/2024-02").get("index"));
            assertNull(full.get("Z/2024-01").get("index")); assertNull(full.get("Z/2024-02").get("index"));
            var page = index(checked(baseline(baseline,true)).getItems(),"category","month");
            assertEquals(3,page.size()); assertTrue(page.keySet().stream().allMatch(k->k.endsWith("2024-02")));
            number(full.get("P/2024-02").get("index").toString(),page.get("P/2024-02").get("index"));
            number(full.get("Q/2024-02").get("index").toString(),page.get("Q/2024-02").get("index"));
            assertNull(page.get("Z/2024-02").get("index"));
        }
    }

    @Test void unsupportedDerivedAndHierarchyCombinationsRejectOnBothRoutes() {
        var p = tree(); pivot(p).put("outputFormat","grid"); rejected(p);
        p = tree(); pivot(p).put("options",Map.of("crossjoin",true)); rejected(p);
        p = baseline("first",false); pivot(p).put("rows",List.of(Map.of("field","node$caption","hierarchyMode","tree"))); pivot(p).put("outputFormat","tree"); rejected(p);
        p = parentShare(false); pivot(p).put("metrics",List.of("average",Map.of("name","share","type","parentShare","of","average"))); rejected(p);
        p = baseline("first",false); pivot(p).put("metrics",List.of("amount",Map.of("name","index","type","baselineRatio","of","missing","axis","columns","baseline","first"))); rejected(p);
    }

    @Test void validatePreparesAndExecutesNoBusinessSqlIncludingAuxiliaryQueries() throws Exception {
        var model = (JdbcQueryModelImpl) queryModelLoader.getJdbcQueryModel("PivotValidationBase",null);
        DataSource original = model.getDataSource(); AtomicInteger prepared = new AtomicInteger(), executed = new AtomicInteger();
        var observed = new DelegatingDataSource(original) {
            @Override public Connection getConnection() throws SQLException { return observe(super.getConnection(),prepared,executed); }
        };
        model.setDataSource(observed);
        try {
            var properties = request(List.of("node$id"),null,List.of("amount"),"flat"); pivot(properties).put("properties",List.of("node$title"));
            var top = request(List.of(Map.of("field","category","limit",1,"orderBy",List.of("-amount"))),List.of("month"),List.of("amount"),"grid");
            var domain = request(List.of(Map.of("field","category","start",1,"limit",1)),null,List.of("amount"),"flat");
            for (var p:List.of(basic("flat"),basic("grid"),top,domain,tree(),properties,parentShare(true),baseline("first",true),baseline("last",true))) {
                assertNotNull(call("Base",p,"validate",SemanticRequestContext.empty()));
                assertNotNull(service.validateQuery("PivotValidationBase",mapper.toQueryRequest(p),SemanticRequestContext.empty()));
            }
            assertEquals(0,prepared.get(),"validate may compile but must not prepare any fact/skeleton/property SQL");
            assertEquals(0,executed.get(),"validate must not execute business JDBC statements");
            call("Base",tree(),"execute",SemanticRequestContext.empty());
            assertTrue(prepared.get()>0); assertTrue(executed.get()>0,"positive control verifies the JDBC observer");
        } finally { model.setDataSource(original); }
    }

    Connection observe(Connection connection, AtomicInteger prepared, AtomicInteger executed) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            try {
                Object result = method.invoke(connection,args);
                if (result instanceof Statement statement) {
                    String sql = args != null && args.length>0 && args[0] instanceof String text ? text : "";
                    if (businessSql(sql)) prepared.incrementAndGet();
                    Class<?> type = result instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{type},(p,m,a)->{
                        String statementSql = a != null && a.length>0 && a[0] instanceof String text ? text : sql;
                        if (m.getName().startsWith("execute") && businessSql(statementSql)) executed.incrementAndGet();
                        try { return m.invoke(statement,a); } catch (InvocationTargetException error) { throw error.getCause(); }
                    });
                }
                return result;
            } catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }
    boolean businessSql(String sql) { return sql.toLowerCase(Locale.ROOT).contains(TABLE) || sql.toLowerCase(Locale.ROOT).contains(NODES); }
}
