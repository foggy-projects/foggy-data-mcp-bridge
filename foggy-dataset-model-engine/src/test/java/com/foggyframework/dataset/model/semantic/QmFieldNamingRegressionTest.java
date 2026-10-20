package com.foggyframework.dataset.model.semantic;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.model.def.permission.FieldPermissionRuleDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.service.*;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.TestPropertySource(properties = "issue146.test-isolation=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QmFieldNamingRegressionTest extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource SemanticServiceV3 metadata;
    @Resource SemanticQueryServiceV3 queries;
    @Resource SemanticQueryPayloadMapper mapper;
    @TempDir static Path root;
    private static final String BUNDLE = "issue146-names";

    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE issue146_artist (id INTEGER PRIMARY KEY, name VARCHAR(50))");
        jdbcTemplate.execute("CREATE TABLE issue146_album (id INTEGER PRIMARY KEY, artist_id INTEGER)");
        jdbcTemplate.execute("CREATE TABLE issue146_line (id INTEGER PRIMARY KEY, album_id INTEGER, guest_album_id INTEGER)");
        jdbcTemplate.update("INSERT INTO issue146_artist VALUES (1, 'Zebra'), (2, 'Alpha')");
        jdbcTemplate.update("INSERT INTO issue146_album VALUES (1, 1), (2, 2)");
        jdbcTemplate.update("INSERT INTO issue146_line VALUES (1, 1, 2), (2, 1, 2), (3, 2, 1)");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/NamingModel.tm"), """
            export const model={name:'NamingModel',tableName:'issue146_line',idColumn:'id',
              properties:[{column:'id',name:'lineId',type:'INTEGER'}],dimensions:[
              {name:'album',tableName:'issue146_album',foreignKey:'album_id',primaryKey:'id',captionColumn:'id',
                dimensions:[{name:'artist',tableName:'issue146_artist',foreignKey:'artist_id',primaryKey:'id',captionColumn:'name'}]},
              {name:'guestAlbum',tableName:'issue146_album',foreignKey:'guest_album_id',primaryKey:'id',captionColumn:'id',
                dimensions:[{name:'guestArtist',tableName:'issue146_artist',foreignKey:'artist_id',primaryKey:'id',captionColumn:'name'}]}],measures:[]};
            """);
        qm("Names", "{ref:m.lineId},{ref:m.album.artist$id,name:'artistId',alias:'artistId'},"
                + "{ref:m.album.artist$caption,name:'artistName',alias:'artistName'}");
        qm("Split", "{ref:m.lineId},{ref:m.album.artist$id,name:'artistId',alias:'idOut'},"
                + "{ref:m.album.artist$caption,name:'artistName',alias:'nameOut'}");
        qm("IdOnly", "{ref:m.album.artist$id,name:'artistId',alias:'idOut'}");
        qm("AliasOnly", "{ref:m.album.artist$caption,alias:'nameOut'}");
        qm("NameOnly", "{ref:m.album.artist$caption,name:'artistName'}");
        qm("Property", "{ref:m.lineId,name:'queryId',alias:'idOut'}");
        qm("PropertyBoth", "{ref:m.lineId},{ref:m.lineId,name:'queryId',alias:'idOut'}");
        qm("TwoPaths", "{ref:m.album.artist$id,name:'artistId',alias:'artistId'},"
                + "{ref:m.album.artist$caption,name:'artistName',alias:'artistName'},"
                + "{ref:m.guestAlbum.guestArtist$id,name:'guestId',alias:'guestId'},"
                + "{ref:m.guestAlbum.guestArtist$caption,name:'guestName',alias:'guestName'}");
        qm("Collision", "{ref:m.album.artist$id,name:'sameName',alias:'first'},"
                + "{ref:m.guestAlbum.guestArtist$id,name:'sameName',alias:'second'}");
        qm("Legacy", "{ref:m.lineId},{ref:m.album.artist}");
        qm("Whole", "{ref:m.album.artist,alias:'ignoredPrefix'}");
        Files.writeString(root.resolve("query/NamingSelfJoin.qm"), """
            const left=loadTableModel('NamingModel','leftSide');
            const right=loadTableModel('NamingModel','rightSide');
            export const queryModel={name:'NamingSelfJoin',loader:'v2',model:left,
              joins:[left.leftJoin(right).on(left.guestAlbum$id,right.lineId)],
              columnGroups:[{caption:'Fields',items:[
                {ref:left.lineId,name:'lineId',alias:'lineId'},
                {ref:left.album.artist$caption,name:'leftArtist',alias:'leftArtist'},
                {ref:right.album.artist$caption,name:'rightArtist',alias:'rightArtist'}]}],accesses:[]};
            """);
        assertTrue(bundles.addExternalBundle(BUNDLE,"",root.toString(),false));
    }
    private void qm(String suffix,String items) throws Exception {
        Files.writeString(root.resolve("query/Naming"+suffix+".qm"),
                "const m=loadTableModel('NamingModel'); export const queryModel={name:'Naming"+suffix
                +"',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:["+items+"]}],accesses:[]};");
    }
    @AfterAll void cleanup() {
        if(bundles.containBundle(BUNDLE)) bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");
        for(String table:List.of("line","album","artist")) jdbcTemplate.execute("DROP TABLE IF EXISTS issue146_"+table);
    }
    private SemanticQueryResponse execute(String suffix, Map<String,Object> payload) {
        return queries.queryModel("Naming"+suffix,mapper.toQueryRequest(payload),"execute",SemanticRequestContext.empty());
    }
    @ParameterizedTest @ValueSource(strings={"Names","Split"})
    void namedDimensionsWorkAcrossCompilationAndExecution(String suffix) {
        var payload=new LinkedHashMap<String,Object>();
        payload.put("columns",List.of("artistId","artistName","count(lineId) AS lineCount"));
        payload.put("slice",List.of(Map.of("field","artistId","op","=","value",1)));
        payload.put("groupBy",List.of("artistId","artistName"));
        payload.put("orderBy",List.of("artistId"));payload.put("limit",10);
        queries.queryModel("Naming"+suffix,mapper.toQueryRequest(payload),"validate",SemanticRequestContext.empty());
        assertNotNull(queries.compileForExplain("Naming"+suffix,mapper.toQueryRequest(payload),SemanticRequestContext.empty()));
        var rows=execute(suffix,payload).getItems(); assertEquals(1,rows.size());
        var row=(Map<?,?>)rows.get(0);
        assertEquals(1,((Number)row.get(suffix.equals("Split")?"idOut":"artistId")).intValue());
        assertEquals("Zebra",row.get(suffix.equals("Split")?"nameOut":"artistName"));
        assertEquals(2,((Number)row.get("lineCount")).intValue());
    }
    @Test void idOnlyDeclarationIsQueryable() {
        var row=(Map<?,?>)execute("IdOnly",Map.of("columns",List.of("artistId"),"limit",1)).getItems().get(0);
        assertTrue(row.containsKey("idOut"));
    }
    @Test void nameOnlyKeepsDefaultOutputAlias() {
        var row=(Map<?,?>)execute("NameOnly",Map.of("columns",List.of("artistName"),"limit",1)).getItems().get(0);
        assertTrue(row.containsKey("album_artist$caption"));
    }
    @Test void aliasOnlyKeepsQueryNameAndChangesOutputKey() {
        var row=(Map<?,?>)execute("AliasOnly",Map.of("columns",List.of("album_artist$caption"),"limit",1)).getItems().get(0);
        assertTrue(row.containsKey("nameOut"));
        assertThrows(RuntimeException.class,()->execute("AliasOnly",Map.of("columns",List.of("nameOut"))));
    }
    @Test void propertyNameAndAliasStaySeparate() {
        var row=(Map<?,?>)execute("Property",Map.of("columns",List.of("queryId"),"limit",1)).getItems().get(0);
        assertTrue(row.containsKey("idOut"));
        assertThrows(RuntimeException.class,()->execute("Property",Map.of("columns",List.of("idOut"))));
    }
    @Test void originalAndRenamedPropertyCanBothBeAdvertised() {
        var row=(Map<?,?>)execute("PropertyBoth",Map.of("columns",List.of("lineId","queryId"),"limit",1))
                .getItems().get(0);
        assertEquals(row.get("lineId"),row.get("idOut"));
        SemanticMetadataRequest req=new SemanticMetadataRequest();req.setQmModels(List.of("NamingPropertyBoth"));
        var fields=(Map<?,?>)metadata.getMetadata(req,"json",SemanticRequestContext.empty()).getData().get("fields");
        assertTrue(fields.containsKey("lineId"));assertTrue(fields.containsKey("queryId"));
        String markdown=metadata.getMetadata(req,"markdown",SemanticRequestContext.empty()).getContent();
        assertTrue(markdown.contains("lineId"));assertTrue(markdown.contains("queryId"));
        req.setQmModels(List.of("NamingPropertyBoth","NamingLegacy"));
        markdown=metadata.getMetadata(req,"markdown",SemanticRequestContext.empty()).getContent();
        assertTrue(markdown.contains("lineId"));assertTrue(markdown.contains("queryId"));
    }
    @Test void twoNestedPathsKeepTheirOwners() {
        var rows=execute("TwoPaths",Map.of("columns",List.of("artistId","artistName","guestId","guestName"),
                "slice",List.of(Map.of("field","artistId","op","=","value",1)),"limit",10)).getItems();
        assertEquals(2,rows.size());
        for(Object item:rows){var row=(Map<?,?>)item;assertEquals("Zebra",row.get("artistName"));assertEquals("Alpha",row.get("guestName"));}
    }
    @ParameterizedTest @ValueSource(strings={"Names","Split","Property","TwoPaths"})
    void jsonAndMarkdownExposeQueryableNames(String suffix) {
        SemanticMetadataRequest req=new SemanticMetadataRequest();req.setQmModels(List.of("Naming"+suffix));
        var json=metadata.getMetadata(req,"json",SemanticRequestContext.empty()).getData();
        var fields=(Map<?,?>)json.get("fields");
        String field=suffix.equals("Property")?"queryId":"artistName";
        assertTrue(fields.containsKey(field),fields.toString());
        assertEquals(field,((Map<?,?>)fields.get(field)).get("fieldName"));
        assertTrue(metadata.getMetadata(req,"markdown",SemanticRequestContext.empty()).getContent().contains("| "+field+" |"));
        req.setQmModels(List.of("Naming"+suffix,"NamingLegacy"));
        assertTrue(metadata.getMetadata(req,"markdown",SemanticRequestContext.empty()).getContent().contains(field));
        if(suffix.equals("Property")) assertFalse(fields.containsKey("lineId"),"Do not advertise an unselectable source name");
    }
    @Test void selectedOutputAliasCanStillOrderWithoutBecomingAQueryName() {
        var rows=execute("Split",Map.of("columns",List.of("artistId","artistName"),"distinct",true,
                "orderBy",List.of("nameOut"),"limit",10)).getItems();
        assertEquals("Alpha",((Map<?,?>)rows.get(0)).get("nameOut"));
    }
    @ParameterizedTest @ValueSource(strings={"Legacy","Whole","Names"})
    void legacyDimensionNamesRemainUsable(String suffix) {
        assertFalse(execute(suffix,Map.of("columns",List.of("artist$id","artist$caption"),"limit",1)).getItems().isEmpty());
    }
    @Test void duplicateExplicitQueryNamesFailAtLoad() {
        assertThrows(RuntimeException.class, () -> queryModelLoader.getJdbcQueryModel("NamingCollision", null));
    }
    @Test void runtimePermissionsAndMetadataUseQueryNames() {
        var allowed = SemanticRequestContext.of(null, (ModelResultContext.SecurityContext)null, Set.of("artistId"));
        assertFalse(queries.queryModel("NamingNames", mapper.toQueryRequest(Map.of("columns",List.of("artistId"),"limit",1)),
                "execute", allowed).getItems().isEmpty());
        assertThrows(RuntimeException.class, () -> queries.queryModel("NamingNames",
                mapper.toQueryRequest(Map.of("columns",List.of("artistName"))), "execute", allowed));
        SemanticMetadataRequest req = new SemanticMetadataRequest(); req.setQmModels(List.of("NamingNames"));
        var fields = (Map<?,?>)metadata.getMetadata(req,"json",allowed).getData().get("fields");
        assertTrue(fields.containsKey("artistId")); assertFalse(fields.containsKey("artistName"));
        assertFalse(metadata.getMetadata(req,"markdown",allowed).getContent().contains("| artistName |"));
    }
    @Test void tmHiddenSourceCannotBeExposedByRenaming() {
        var qm=(QueryModelSupport)queryModelLoader.getJdbcQueryModel("NamingNames",null);
        var tm=(TableModelSupport)tableModelLoaderManager.load("NamingModel"); var original=tm.getFieldPermissions();
        try {
            FieldPermissionsDef permissions=new FieldPermissionsDef();
            FieldPermissionRuleDef hidden=new FieldPermissionRuleDef(); hidden.setFields(List.of("artist"));
            permissions.setHiddenFields(List.of(hidden));tm.setFieldPermissions(permissions);
            assertThrows(RuntimeException.class, () -> execute("Names",Map.of("columns",List.of("artistName"))));
            SemanticMetadataRequest req=new SemanticMetadataRequest();req.setQmModels(List.of("NamingNames"));
            var fields=(Map<?,?>)metadata.getMetadata(req,"json",SemanticRequestContext.empty()).getData().get("fields");
            assertFalse(fields.containsKey("artistName"));assertFalse(fields.containsKey("artistId"));
        } finally {tm.setFieldPermissions(original);}
    }
    @ParameterizedTest @ValueSource(strings={"artistName","artist"})
    void qmHiddenRenamedFieldIsNotAdvertisedOrExecutable(String hiddenName) {
        var qm=(QueryModelSupport)queryModelLoader.getJdbcQueryModel("NamingNames",null);
        var original=qm.getFieldPermissions();
        try {
            FieldPermissionsDef permissions=new FieldPermissionsDef();
            FieldPermissionRuleDef hidden=new FieldPermissionRuleDef();hidden.setFields(List.of(hiddenName));
            permissions.setHiddenFields(List.of(hidden));qm.setFieldPermissions(permissions);
            assertThrows(RuntimeException.class, () -> execute("Names",Map.of("columns",List.of("artistName"))));
            SemanticMetadataRequest req=new SemanticMetadataRequest();req.setQmModels(List.of("NamingNames"));
            assertFalse(((Map<?,?>)metadata.getMetadata(req,"json",SemanticRequestContext.empty()).getData().get("fields")).containsKey("artistName"));
        } finally {qm.setFieldPermissions(original);}
    }
    @Test void physicalColumnDenialSurvivesNaming() {
        var context=SemanticRequestContext.of(null,(ModelResultContext.SecurityContext)null,null,
                List.of(new DeniedPhysicalColumn(null,"issue146_artist","name")));
        assertThrows(RuntimeException.class, () -> queries.queryModel("NamingNames",
                mapper.toQueryRequest(Map.of("columns",List.of("artistName"))),"execute",context));
        SemanticMetadataRequest req=new SemanticMetadataRequest();req.setQmModels(List.of("NamingNames"));
        assertFalse(((Map<?,?>)metadata.getMetadata(req,"json",context).getData().get("fields")).containsKey("artistName"));
    }
    @Test void metadataFilterAndOutputNameAgreeAcrossViews() {
        SemanticMetadataRequest req=new SemanticMetadataRequest(); req.setQmModels(List.of("NamingSplit"));
        req.setFields(List.of("artistName"));
        var fields=(Map<?,?>)metadata.getMetadata(req,"json",SemanticRequestContext.empty()).getData().get("fields");
        assertEquals(Set.of("artistName"),fields.keySet());
        assertEquals("nameOut",((Map<?,?>)fields.get("artistName")).get("outputName"));
        assertTrue(metadata.getMetadata(req,"markdown",SemanticRequestContext.empty()).getContent().contains("| artistName | nameOut |"));
    }

    @Test void namedColumnsInSelfJoinKeepDistinctOwners() {
        var rows=execute("SelfJoin",Map.of("columns",List.of("lineId","leftArtist","rightArtist"),
                "slice",List.of(Map.of("field","lineId","op","=","value",3)),"limit",10)).getItems();
        assertEquals(1,rows.size());
        var row=(Map<?,?>)rows.get(0);
        assertEquals("Alpha",row.get("leftArtist")); assertEquals("Zebra",row.get("rightArtist"));
    }
    @Test void captionFilterKeepsItsExistingConditionBinding() {
        var oldRows=execute("Legacy",Map.of("columns",List.of("artist$caption"),
                "slice",List.of(Map.of("field","artist$caption","op","=","value","Zebra")),"limit",10)).getItems();
        var newRows=execute("Names",Map.of("columns",List.of("artistName"),
                "slice",List.of(Map.of("field","artistName","op","=","value","Zebra")),"limit",10)).getItems();
        assertFalse(oldRows.isEmpty());assertEquals(oldRows.size(),newRows.size());
        assertEquals(((Map<?,?>)oldRows.get(0)).get("artist$caption"),((Map<?,?>)newRows.get(0)).get("artistName"));
    }

    @Test void multiModelJsonRetainsEachModelsOutputAlias() {
        SemanticMetadataRequest req=new SemanticMetadataRequest();req.setQmModels(List.of("NamingNames","NamingSplit"));
        var fields=(Map<?,?>)metadata.getMetadata(req,"json",SemanticRequestContext.empty()).getData().get("fields");
        var usages=(Map<?,?>)((Map<?,?>)fields.get("artistName")).get("models");
        assertEquals("artistName",((Map<?,?>)usages.get("NamingNames")).get("outputName"));
        assertEquals("nameOut",((Map<?,?>)usages.get("NamingSplit")).get("outputName"));
    }

}
