package com.foggyframework.dataset.model.semantic.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.validation.DefaultDetachedModelValidationFactory;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="member-policy.test-isolation=true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberPolicyRegressionTest extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource AdvancedQueryFacade queries;
    @Resource MemberBindingService binding;
    @TempDir static Path root;
    static final String BUNDLE="member-policy-regression";
    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE policy_artist(id INTEGER PRIMARY KEY,name VARCHAR(50),parent_id INTEGER)");
        jdbcTemplate.execute("CREATE TABLE policy_album(id INTEGER PRIMARY KEY,artist_id INTEGER,title VARCHAR(50))");
        jdbcTemplate.execute("CREATE TABLE policy_line(id INTEGER PRIMARY KEY,album_id INTEGER)");
        jdbcTemplate.execute("CREATE TABLE policy_closure(parent_id INTEGER,artist_id INTEGER,distance INTEGER)");
        jdbcTemplate.update("INSERT INTO policy_artist VALUES(1,'A',NULL),(2,'B',1),(3,'C',NULL)");
        jdbcTemplate.update("INSERT INTO policy_album VALUES(11,1,'Album A'),(12,2,'Album B')");
        jdbcTemplate.update("INSERT INTO policy_line VALUES(101,11),(102,12)");
        jdbcTemplate.update("INSERT INTO policy_closure VALUES(1,1,0),(1,2,1),(2,2,0),(3,3,0)");
        Files.createDirectories(root.resolve("model"));Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/PolicyModel.tm"),"""
            export const model={name:'PolicyModel',tableName:'policy_line',idColumn:'id',
              properties:[{name:'lineId',column:'id',type:'INTEGER'}],dimensions:[
              {name:'album',tableName:'policy_album',foreignKey:'album_id',primaryKey:'id',captionColumn:'title',dimensions:[
              {name:'artist',tableName:'policy_artist',foreignKey:'artist_id',primaryKey:'id',captionColumn:'name',
              closureTableName:'policy_closure',parentKey:'parent_id',childKey:'artist_id'}]}],measures:[]};
            """);
        qm("PolicyNames","");
        qm("PolicyBad",",memberPermissions:[{dimension:'artistKey',patch:{visibleColumns:['missing']}}]");
        qm("PolicyMixed",",memberPermissions:[{dimension:'artistKey',patch:{visibleColumns:['id','missing']}}]");
        qm("PolicyEmpty",",memberPermissions:[{dimension:'artistKey',patch:{visibleColumns:[]}}]");
        qm("PolicyNoHierarchy",",memberPermissions:[{dimension:'artistKey',patch:{hierarchyEnabled:false}}]");
        qm("PolicyAllowed",",memberPermissions:[{dimension:'artistKey',patch:{allowedHierarchyOps:['childrenOf']}}]");
        qm("PolicyChild",",memberPermissions:[{dimension:'artistKey',patch:{forcedSlice:[{field:'id',op:'!=',value:2}]}}]");
        qm("PolicySourceHidden",",fieldPermissions:{hiddenFields:[{fields:['artistName']}]},memberPermissions:[{dimension:'artistKey',patch:{forcedSlice:[{field:'caption',op:'!=',value:'B'}]}}]");
        qm("PolicyHidden",",memberPermissions:[{dimension:'artistKey',patch:{visibleColumns:['id']}}]");
        qm("PolicyBuilder",",memberPermissions:[{dimension:'artistKey',queryBuilder:(ctx)=>{}}]");
        assertTrue(bundles.addExternalBundle(BUNDLE,"",root.toString(),false));
    }
    void qm(String name,String extra) throws Exception {
        Files.writeString(root.resolve("query/"+name+".qm"),"const m=loadTableModel('PolicyModel');export const queryModel={name:'"+name+
          "',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:[{ref:m.lineId},{ref:m.album.artist$id,name:'artistKey',alias:'keyOut'},"+
          "{ref:m.album.artist$caption,name:'artistName',alias:'nameOut'}]}],accesses:[]"+extra+"};");
    }
    @AfterAll void cleanup() {
        if(bundles.containBundle(BUNDLE))bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");
        for(String table:List.of("closure","line","album","artist"))jdbcTemplate.execute("DROP TABLE IF EXISTS policy_"+table);
    }
    List<Map<String,Object>> query(String model,String json) throws Exception {
        var request=new ObjectMapper().readValue(json,DbQueryRequestDef.class);request.setQueryModel(model);
        var context=new ModelResultContext(PagingRequest.buildPagingRequest(request,100),null);
        return (List)queries.queryModelResult(context).getPagingResult().getItems();
    }
    Set<Integer> ids(List<Map<String,Object>> rows,String field) {
        Set<Integer> result=new TreeSet<>();rows.forEach(r->result.add(((Number)r.get(field)).intValue()));return result;
    }
    @ParameterizedTest @ValueSource(strings={"PolicyBad","PolicyMixed","PolicyEmpty"})
    void invalidWhitelistFailsClosed(String name) {
        assertThrows(RuntimeException.class,()->query(name+"#album$artist","{\"columns\":[\"id\",\"caption\"]}"));
    }
    @ParameterizedTest @ValueSource(strings={
      "{\"slice\":[{\"$and\":[{\"$or\":[{\"field\":\"id\",\"op\":\"selfAndDescendantsOf\",\"value\":1}]}]}]}",
      "{\"extData\":{\"syntheticMemberPatch\":{\"forcedSlice\":[{\"field\":\"id\",\"op\":\"selfAndDescendantsOf\",\"value\":1}]}}}"})
    void hierarchyCannotBypassFinalGuard(String request) {
        assertThrows(RuntimeException.class,()->query("PolicyNoHierarchy#album$artist",request));
        assertThrows(RuntimeException.class,()->query("PolicyAllowed#album$artist",request));
    }
    @ParameterizedTest @ValueSource(strings={"childrenOf","descendantsOf","ancestorsOf","selfAndDescendantsOf","selfAndAncestorsOf"})
    void nestedHierarchyUsesPhysicalOwnerInBothEntryPoints(String op) throws Exception {
        int value=op.contains("Ancestors")||op.equals("ancestorsOf")?2:1;
        Set<Integer> expected=op.equals("childrenOf")||op.equals("descendantsOf")?Set.of(2):op.equals("ancestorsOf")?Set.of(1):Set.of(1,2);
        String request="{\"columns\":[\"id\",\"caption\"],\"slice\":[{\"field\":\"id\",\"op\":\""+op+"\",\"value\":"+value+"}]}";
        assertEquals(expected,ids(query("PolicyNames#album$artist",request),"id"));
        assertEquals(expected,ids(query("PolicyNames",request.replace("\"id\"","\"artistKey\"").replace("\"caption\"","\"artistName\"")),"keyOut"));
    }
    @Test void ordinaryNestedHierarchyProjectionKeepsParentJoin() throws Exception {
        assertEquals(Set.of(1,2),ids(query("PolicyNames","{\"columns\":[\"artistKey\",\"artistName\"]}"),"keyOut"));
        assertFalse(query("PolicyNames","{\"columns\":[\"album.artist$hierarchy$id\"]}").isEmpty());
    }
    @Test void childRowPolicyActivatesOnlyWhenSubtreeIsUsed() throws Exception {
        assertEquals(Set.of(11,12),ids(query("PolicyChild#album","{\"columns\":[\"id\",\"caption\"]}"),"id"));
        assertEquals(Set.of(11),ids(query("PolicyChild#album","{\"columns\":[\"id\",\"artist$id\"]}"),"id"));
        assertEquals(Set.of(11),ids(query("PolicyChild#album","{\"columns\":[\"id\"],\"slice\":[{\"field\":\"artist$id\",\"op\":\"in\",\"value\":[1,2]}]}"),"id"));
        assertEquals(Set.of(1,3),ids(query("PolicyChild#album$artist","{\"columns\":[\"id\"]}"),"id"));
    }
    @ParameterizedTest @ValueSource(strings={
      "{\"columns\":[\"artist$caption AS label\"]}",
      "{\"columns\":[\"id\"],\"orderBy\":[{\"field\":\"artist$caption\"}]}",
      "{\"columns\":[\"id\"],\"slice\":[{\"$expr\":\"artist$caption == 'A'\"}]}",
      "{\"columns\":[\"id\"],\"calculatedFields\":[{\"name\":\"x\",\"expression\":\"LENGTH(artist$caption)\"}]}",
      "{\"columns\":[\"id\"],\"groupBy\":[{\"field\":\"artist$caption\"}]}"})
    void childFieldPolicyCoversDependencies(String request) {
        assertThrows(RuntimeException.class,()->query("PolicyHidden#album",request));
    }

    @Test void hiddenSourceDependenciesInExpressionAndFieldReferenceAreDenied() throws Exception {
        assertEquals(Set.of(1),ids(query("PolicyNames#album$artist","{\"columns\":[\"id\"],\"slice\":[{\"$expr\":\"caption == 'A'\"}]}"),"id"));
        for(String condition:List.of("{\"$expr\":\"caption == 'A'\"}",
                "{\"field\":\"id\",\"op\":\"!=\",\"value\":{\"$field\":\"caption\"}}")) {
            var error=assertThrows(RuntimeException.class,()->query("PolicySourceHidden#album$artist","{\"columns\":[\"id\"],\"slice\":["+condition+"]}"));
            assertTrue(error.getMessage().contains("访问被拒绝"),error.toString());
        }
    }

    @Test void externalPatchCannotFilterHiddenSourceFieldsButTrustedPolicyCan() throws Exception {
        assertEquals(Set.of(1,3),ids(query("PolicySourceHidden#album$artist","{\"columns\":[\"id\"]}"),"id"));
        for (String patch : List.of("{\"forcedSlice\":[{\"field\":\"caption\",\"op\":\"=\",\"value\":\"A\"}]}",
                "{\"forcedOrderBy\":[{\"field\":\"caption\"}]}")) {
            var error=assertThrows(RuntimeException.class,()->query("PolicySourceHidden#album$artist","{\"columns\":[\"id\"],\"extData\":"+patch+"}"));
            assertTrue(error.getMessage().contains("external patch field access denied"),error.toString());
        }
    }

    @Test void excludedChildProjectionDoesNotActivateChildRowPolicy() throws Exception {
        var model=queryModelLoader.getJdbcQueryModel("PolicyChild#album","");
        var excluded=model.getJdbcQueryColumns().stream().map(c->c.getName()).filter(n->n.startsWith("artist$")).toList();
        assertFalse(excluded.isEmpty());
        String json=new ObjectMapper().writeValueAsString(Map.of("exColumns",excluded));
        assertEquals(Set.of(11,12),ids(query("PolicyChild#album",json),"id"));
    }

    @Test void childWindowAndExternalPatchDependenciesAreGuarded() throws Exception {
        for(String dependency:List.of("partitionBy","windowOrderBy")) {
            String value=dependency.equals("partitionBy")?"[\"artist$caption\"]":"[{\"field\":\"artist$caption\",\"dir\":\"ASC\"}]";
            String json="{\"columns\":[\"id\",\"rank\"],\"calculatedFields\":[{\"name\":\"rank\",\"expression\":\"ROW_NUMBER()\",\""+dependency+"\":"+value+"}]}";
            var error=assertThrows(RuntimeException.class,()->query("PolicyHidden#album",json));
            assertTrue(error.getMessage().contains("child member field access denied"),error.toString());
        }
        var rows=query("PolicyChild#album","{\"columns\":[\"id\"],\"extData\":{\"forcedSlice\":[{\"field\":\"artist$id\",\"op\":\"in\",\"value\":[1,2]}]}}");
        assertEquals(Set.of(11),ids(rows,"id"));
        var error=assertThrows(RuntimeException.class,()->query("PolicyNoHierarchy#album","{\"columns\":[\"id\"],\"slice\":[{\"$and\":[{\"field\":\"artist$id\",\"op\":\"childrenOf\",\"value\":1}]}]}"));
        assertTrue(error.getMessage().contains("层级"),error.toString());
    }

    @Test void childSqlBuilderCannotBeSkippedThroughParent() throws Exception {
        assertEquals(2,query("PolicyBuilder#album","{\"columns\":[\"id\"]}").size());
        assertThrows(RuntimeException.class,()->query("PolicyBuilder#album","{\"columns\":[\"artist$id\"]}"));
    }
    @Test void bindingReturnsActualQueryNamesAndHierarchyPolicy() {
        var descriptor=binding.resolve("PolicyNames","album$artist$id","");
        assertEquals("PolicyNames#album$artist",descriptor.modelName());
        assertEquals("artistKey",descriptor.selectionFieldName());assertEquals("artistName",descriptor.displayFieldName());
        assertTrue(descriptor.hierarchical());assertFalse(binding.resolve("PolicyNoHierarchy","artistKey","").hierarchical());
        assertEquals(List.of("childrenOf"),binding.resolve("PolicyAllowed","artistKey","").hierarchyOps());
        assertThrows(RuntimeException.class,()->binding.resolve("PolicyNames","keyOut",""));
    }
    @Test void subdirectoryRetainsParentDependenciesWithoutDuplicatingResources() {
        var factory=new DefaultDetachedModelValidationFactory(bundles,tableModelLoaderManager,queryModelLoader);
        try(var session=factory.open("validation","",root.resolve("query").toString())) {
            assertNotNull(session.resolveQueryModel("PolicyNames","").model());
        }
    }
    @Test void tmConflictAndBundleNameCollisionCannotImplicitlyReplace(@TempDir Path other) throws Exception {
        Files.copy(root.resolve("model/PolicyModel.tm"),other.resolve("PolicyModel.tm"));
        var factory=new DefaultDetachedModelValidationFactory(bundles,tableModelLoaderManager,queryModelLoader);
        try(var session=factory.open(BUNDLE,"",other.toString())) {
            assertThrows(RuntimeException.class,()->session.validateTableModel(session.sourceBundle().findBundleResource("PolicyModel.tm",true),""));
        }
        Files.copy(root.resolve("query/PolicyNames.qm"),other.resolve("PolicyNames.qm"));
        try(var session=factory.open(BUNDLE,"",other.toString())) {
            assertThrows(RuntimeException.class,()->session.resolveQueryModel("PolicyNames",""));
        }
        try(var session=factory.openReplacement(BUNDLE,"",other.toString())) {
            assertNotNull(session.resolveQueryModel("PolicyNames","").model());
        }
    }

    @Test void bindingReflectsCommittedRefreshAndFailedCandidateKeepsPreviousBinding() throws Exception {
        qm("PolicyRefresh",",memberPermissions:[{dimension:'artistKey',patch:{hierarchyEnabled:false}}]");
        var first=binding.resolve("PolicyRefresh","album$artist$id","");
        assertFalse(first.hierarchical());assertEquals("artistKey",first.selectionFieldName());
        var coordinator=appCtx.getBean(com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator.class);
        var refresh=com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest.models("",
            Set.of(com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey.query("PolicyRefresh")),
            com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger.EXPLICIT_RECOVERY);
        appCtx.getBean(com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler.class).removeFilesUnderRoot(root.toString());
        Path file=root.resolve("query/PolicyRefresh.qm");String original=Files.readString(file);
        try {
            Files.writeString(file,original.replace("artistKey","newArtistKey").replace("artistName","newArtistName").replace("hierarchyEnabled:false","hierarchyEnabled:true"));
            appCtx.getBean(com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler.class).clean(file.toFile());
            coordinator.refresh(refresh);
            var current=binding.resolve("PolicyRefresh","album$artist$id","");
            assertTrue(current.hierarchical());assertEquals("newArtistKey",current.selectionFieldName());assertEquals("newArtistName",current.displayFieldName());
            Path invalid=Files.createTempDirectory(root.getParent(),"invalid-member-refresh");
            try {
                Files.writeString(invalid.resolve("PolicyRefresh.qm"),"export const queryModel={name: ;};");
                var factory=new DefaultDetachedModelValidationFactory(bundles,tableModelLoaderManager,queryModelLoader);
                try(var session=factory.openReplacement(BUNDLE,"",invalid.toString())) {
                    assertThrows(RuntimeException.class,()->session.resolveQueryModel("PolicyRefresh",""));
                }
                assertEquals(current,binding.resolve("PolicyRefresh","album$artist$id",""));
            } finally { Files.deleteIfExists(invalid.resolve("PolicyRefresh.qm"));Files.deleteIfExists(invalid); }
        } finally {
            Files.writeString(file,original);
            appCtx.getBean(com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler.class).clean(file.toFile());
            coordinator.refresh(refresh);
        }
    }
}
