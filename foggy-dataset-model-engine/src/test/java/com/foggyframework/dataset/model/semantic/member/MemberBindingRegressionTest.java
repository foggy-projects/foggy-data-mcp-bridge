package com.foggyframework.dataset.model.semantic.member;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.service.JdbcService;
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

@TestPropertySource(properties = "issue148.test-isolation=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberBindingRegressionTest extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource JdbcService members;
    @Resource AdvancedQueryFacade queries;
    @TempDir static Path root;
    static final String BUNDLE = "issue148-members";
    static final String MODEL = "MemberNames";
    static final String PATH = "track$album$artist";

    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE issue148_artist(id INTEGER PRIMARY KEY, name VARCHAR(50))");
        jdbcTemplate.execute("CREATE TABLE issue148_album(id INTEGER PRIMARY KEY, artist_id INTEGER, title VARCHAR(50))");
        jdbcTemplate.execute("CREATE TABLE issue148_track(id INTEGER PRIMARY KEY, album_id INTEGER, title VARCHAR(50))");
        jdbcTemplate.execute("CREATE TABLE issue148_line(id INTEGER PRIMARY KEY, track_id INTEGER)");
        jdbcTemplate.update("INSERT INTO issue148_artist VALUES(1,'First artist'),(2,'Second artist'),(3,'Unreferenced artist')");
        jdbcTemplate.update("INSERT INTO issue148_album VALUES(11,1,'First album'),(12,2,'Second album')");
        jdbcTemplate.update("INSERT INTO issue148_track VALUES(21,11,'Track A'),(22,11,'Track B'),(23,12,'Track C'),(24,12,'Track D')");
        jdbcTemplate.update("INSERT INTO issue148_line VALUES(1,21),(2,21)");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/MemberModel.tm"), """
          export const model={name:'MemberModel',tableName:'issue148_line',idColumn:'id',
            properties:[{name:'lineId',column:'id',type:'INTEGER'}],dimensions:[
              {name:'track',tableName:'issue148_track',foreignKey:'track_id',primaryKey:'id',captionColumn:'title',dimensions:[
                {name:'album',tableName:'issue148_album',foreignKey:'album_id',primaryKey:'id',captionColumn:'title',dimensions:[
                  {name:'artist',tableName:'issue148_artist',foreignKey:'artist_id',primaryKey:'id',captionColumn:'name'}]}]}],measures:[]};
          """);
        qm(MODEL, "");
        qm("MemberHidden", ",fieldPermissions:{hiddenFields:[{fields:['artistName']}]}");
        qm("MemberPatch", ",memberPermissions:[{dimension:'artistKey',patch:{forcedSlice:[{field:'id',op:'!=',value:2}]}}]");
        qm("MemberRows", ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:true,rowPredicates:[ctx.predicate.eq('artistKey',2)]};}}");
        qm("MemberFactRows", ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:true,rowPredicates:[ctx.predicate.eq('lineId',1)]};}}");
        qm("MemberDenied", ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:false};}}");
        Files.writeString(root.resolve("model/MemberGuard.tm"),Files.readString(root.resolve("model/MemberModel.tm"))
                .replace("name:'MemberModel'","name:'MemberGuard'")
                .replace("captionColumn:'name'", "captionColumn:'name',memberPermission:{patch:{forcedSlice:[{field:'id',op:'!=',value:1}]}}"));
        qm("MemberTmPatch", "");
        Path guard=root.resolve("query/MemberTmPatch.qm");Files.writeString(guard,Files.readString(guard).replace("loadTableModel('MemberModel')","loadTableModel('MemberGuard')"));
        assertTrue(bundles.addExternalBundle(BUNDLE, "", root.toString(), false));
    }

    private void qm(String name, String extra) throws Exception {
        Files.writeString(root.resolve("query/"+name+".qm"), "const m=loadTableModel('MemberModel');export const queryModel={name:'"
                +name+"',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:[{ref:m.lineId},"
                +"{ref:m.track.album.artist$id,name:'artistKey',alias:'idOut'},"
                +"{ref:m.track.album.artist$caption,name:'artistName',alias:'nameOut'}]}],accesses:[]"+extra+"};");
    }

    @AfterAll void cleanup() {
        if (bundles.containBundle(BUNDLE)) bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");
        for (String table : List.of("line","track","album","artist")) jdbcTemplate.execute("DROP TABLE IF EXISTS issue148_"+table);
    }

    private Map<Integer,String> memberRows(String model, String field) {
        var result = members.queryDimensionData(PagingRequest.buildPagingRequest(new DimensionDataQueryForm(model,field),100));
        Map<Integer,String> rows = new TreeMap<>();
        for (var item : result.getItems()) rows.put(Integer.parseInt(item.getId().toString()),item.getCaption());
        assertEquals(rows.size(),result.getTotal());
        return rows;
    }

    @ParameterizedTest @ValueSource(strings={"artist","artist$id","artist$caption","artistKey","artistName",
            "track.album.artist","track$album$artist","track_album_artist","track$album$artist$id"})
    void selectorsReturnTargetTableIncludingUnreferencedMembers(String field) {
        Map<Integer,String> oracle = new TreeMap<>();
        jdbcTemplate.query("SELECT id,name FROM issue148_artist", rs -> { oracle.put(rs.getInt(1),rs.getString(2)); });
        assertEquals(oracle,memberRows(MODEL,field));
        var bound = new SyntheticMemberQueryModelResolver().resolve(queryModelLoader,MODEL,field,"");
        assertEquals(MODEL+"#"+PATH,bound.syntheticModelName());
        assertEquals(bound.syntheticModelName(),queryModelLoader.getJdbcQueryModel(bound.syntheticModelName(),"").getName());
    }

    @Test void memberPermissionsUseTheSourceInstancePinnedAtBuildTime() {
        var source=queryModelLoader.getJdbcQueryModel(MODEL,"");
        var synthetic=queryModelLoader.getJdbcQueryModel(MODEL+"#"+PATH,"");
        var unrelatedLoader=org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.QueryModelLoader.class);
        assertSame(source,MemberFieldBinding.sourceModel(synthetic,unrelatedLoader,""));
        org.mockito.Mockito.verifyNoInteractions(unrelatedLoader);
    }

    @Test void albumAndRootKeepIndependentCatalogs() {
        assertEquals(Map.of(11,"First album",12,"Second album"),memberRows(MODEL,"album$id"));
        assertEquals(4,memberRows(MODEL,"track").size());
        assertEquals(MODEL+"#track",queryModelLoader.getJdbcQueryModel(MODEL+"#track","").getName());
    }

    @ParameterizedTest @ValueSource(strings={"idOut","nameOut","artist$missing","lineId"})
    void outputAliasesAndUnknownFieldsAreNotDimensionInputs(String field) {
        assertThrows(IllegalArgumentException.class,()->memberRows(MODEL,field));
    }

    @ParameterizedTest @ValueSource(strings={"artist","artist$id","artistKey","artistName", "track.album.artist"})
    void hiddenCaptionCannotBeBypassedByAlternativeSelectors(String field) {
        assertThrows(RuntimeException.class,()->memberRows("MemberHidden",field));
    }

    @Test void hiddenBindingMatchesSourcePermissionResolution() {
        var source=queryModelLoader.getJdbcQueryModel("MemberHidden","");
        var allowed=appCtx.getBean(com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolver.class)
                .resolve(source,"",null,null,null).getEffectiveFieldAccess();
        assertNotNull(allowed);
        assertFalse(allowed.contains("artistName"),allowed.toString());
        var mapped=MemberFieldBinding.allowedFields(source,PATH,allowed,Set.of("id","caption"));
        assertFalse(mapped.contains("caption"),()->"allowed="+allowed+" mapped="+mapped+" columns="+
                source.getJdbcQueryColumns().stream().map(c->c.getName()+"=>"+MemberFieldBinding.mapColumn(source,PATH,c)).toList());
    }

    @Test void renamedMemberPermissionTargetsArtistNotTrack() {
        assertEquals(Map.of(1,"First artist",3,"Unreferenced artist"),memberRows("MemberPatch","artistName"));
    }

    @Test void sourceRowPermissionsApplyToRenamedMemberFields() {
        assertEquals(Map.of(2,"Second artist"),memberRows("MemberRows","artistKey"));
        assertEquals(Map.of(2,"Second artist"),memberRows("MemberRows","artist"));
        assertThrows(RuntimeException.class,()->memberRows("MemberFactRows","artist"));
        assertThrows(RuntimeException.class,()->memberRows("MemberDenied","artistKey"));
    }

    @Test void tmMemberPermissionUsesSelectedNestedDimension() {
        assertEquals(Map.of(2,"Second artist",3,"Unreferenced artist"),memberRows("MemberTmPatch","artistKey"));
    }
    @Test void ambiguousShortNamesRequireFullPathAndFlattenedChildrenAreNotCountedTwice() {
        var left=org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.DbDimension.class);
        var right=org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.DbDimension.class);
        var first=org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.DbDimension.class);
        var second=org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.DbDimension.class);
        org.mockito.Mockito.when(left.getEffectiveName()).thenReturn("left");
        org.mockito.Mockito.when(right.getEffectiveName()).thenReturn("right");
        org.mockito.Mockito.when(first.getEffectiveName()).thenReturn("artist");
        org.mockito.Mockito.when(second.getEffectiveName()).thenReturn("artist");
        org.mockito.Mockito.when(first.getParentDimension()).thenReturn(left);
        org.mockito.Mockito.when(second.getParentDimension()).thenReturn(right);
        org.mockito.Mockito.when(left.getChildDimensions()).thenReturn(List.of(first));
        org.mockito.Mockito.when(right.getChildDimensions()).thenReturn(List.of(second));
        var tm=org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.TableModel.class);
        org.mockito.Mockito.when(tm.getDimensions()).thenReturn(List.of(left,first,right,second));
        var qm=org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.QueryModel.class);
        org.mockito.Mockito.when(qm.getJdbcModel()).thenReturn(tm);
        assertThrows(IllegalArgumentException.class,()->MemberFieldBinding.resolve(qm,"artist"));
        assertSame(first,MemberFieldBinding.resolve(qm,"left.artist"));
        assertSame(second,MemberFieldBinding.resolve(qm,"right$artist$id"));
        assertEquals(4,MemberFieldBinding.dimensions(tm).size());
    }

    private ModelResultContext context() {
        DbQueryRequestDef request = new DbQueryRequestDef();request.setQueryModel(MODEL+"#"+PATH);
        request.setColumns(List.of("id","caption"));
        return new ModelResultContext(PagingRequest.buildPagingRequest(request,100),null);
    }
    @Test void sourceFieldAllowlistMapsBothRenamedColumns() {
        var ctx=context();ctx.setFieldAccess(Set.of("artistKey","artistName"));
        assertEquals(3,queries.queryModelResult(ctx).getPagingResult().getItems().size());
        var denied=context();denied.setFieldAccess(Set.of("artistKey"));
        assertThrows(RuntimeException.class,()->queries.queryModelResult(denied));
    }
    @Test void rootSyntheticSubtreeKeepsExactFieldPermissions() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(MODEL+"#track");
        request.setColumns(List.of("album$artist$id", "album$artist$caption"));
        var allowed = new ModelResultContext(PagingRequest.buildPagingRequest(request,100),null);
        allowed.setFieldAccess(Set.of("artistKey", "artistName"));
        assertEquals(4,queries.queryModelResult(allowed).getPagingResult().getItems().size());
        var denied = new ModelResultContext(PagingRequest.buildPagingRequest(request,100),null);
        denied.setFieldAccess(Set.of("artistKey"));
        assertThrows(RuntimeException.class,()->queries.queryModelResult(denied));
        request.setQueryModel("MemberHidden#track");
        var hidden = new ModelResultContext(PagingRequest.buildPagingRequest(request,100),null);
        assertThrows(RuntimeException.class,()->queries.queryModelResult(hidden));
    }

    @Test void physicalCaptionDenialSurvivesMemberTranslation() {
        var ctx=context();ctx.setDeniedColumns(List.of(new DeniedPhysicalColumn(null,"issue148_artist","name")));
        assertThrows(RuntimeException.class,()->queries.queryModelResult(ctx));
    }
    @Test void rowPredicateUsesSourceIdentityAndRejectsUnrelatedFactField() {
        var source=queryModelLoader.getJdbcQueryModel(MODEL,"");
        assertEquals("id",MemberFieldBinding.mapField(source,PATH,"artistKey",Set.of("id","caption")));
        assertEquals("caption",MemberFieldBinding.mapField(source,PATH,"artistName",Set.of("id","caption")));
        assertThrows(IllegalArgumentException.class,()->MemberFieldBinding.mapField(source,PATH,"lineId",Set.of("id","caption")));
    }

    @Test void sameRegisteredDirectoryValidatesFreshEditsWithoutMutatingLiveModel() throws Exception {
        var live=queryModelLoader.getJdbcQueryModel(MODEL,"");
        var factory=new DefaultDetachedModelValidationFactory(bundles,tableModelLoaderManager,queryModelLoader);
        for (String path : List.of(root.toString(),root.resolve(".").toString(),root.toUri().toString())) {
            try (var session=factory.open("different-validation-name","",path)) {
                assertTrue(session.reusesRegisteredDirectory());
                var candidate=session.resolveQueryModel(MODEL,"").model();
                assertNotSame(live,candidate);
                assertEquals(1,session.executionBundlesContext().getBundleList().stream()
                        .filter(b->b.getRootPath().equals(session.sourceBundle().getRootPath())).count());
            }
        }
        // Isolate explicit validation from the independent source-file refresh watcher.
        appCtx.getBean(com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler.class)
                .removeFilesUnderRoot(root.toString());
        Path qm=root.resolve("query/"+MODEL+".qm");String original=Files.readString(qm);
        try {
            Files.writeString(qm,original.replace("artistName","editedName"));
            try(var session=factory.open("different-validation-name","",root.toString())) {
                assertNotNull(session.resolveQueryModel(MODEL,"").model().findJdbcColumnForSelectByName("editedName",false));
            }
            assertNotNull(live.findJdbcColumnForSelectByName("artistName",false));
            assertNull(live.findJdbcColumnForSelectByName("editedName",false));
            Files.writeString(qm,"export const queryModel={name: ;};");
            try(var session=factory.open("different-validation-name","",root.toString())) {
                assertThrows(RuntimeException.class,()->session.resolveQueryModel(MODEL,""));
            }
            assertSame(live,queryModelLoader.getJdbcQueryModel(MODEL,""));
            assertEquals(3,memberRows(MODEL,"artist").size());
        } finally { Files.writeString(qm,original); }
    }

    @Test void differentDirectoryWithSameQmNameRemainsAConflict(@TempDir Path other) throws Exception {
        Files.copy(root.resolve("query/"+MODEL+".qm"),other.resolve(MODEL+".qm"));
        var factory=new DefaultDetachedModelValidationFactory(bundles,tableModelLoaderManager,queryModelLoader);
        try(var session=factory.open("different-source","",other.toString())) {
            assertFalse(session.reusesRegisteredDirectory());
            var error=assertThrows(RuntimeException.class,()->session.resolveQueryModel(MODEL,""));
            assertTrue(error.getMessage().contains("duplicate query model resource"),error.toString());
        }
    }
}
