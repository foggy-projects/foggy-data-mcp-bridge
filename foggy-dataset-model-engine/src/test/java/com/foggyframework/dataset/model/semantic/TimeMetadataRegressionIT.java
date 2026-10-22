package com.foggyframework.dataset.model.semantic;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** #155: real TM -> runtime property/dimension -> metadata and query contracts on every dialect. */
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TimeMetadataRegressionIT extends EcommerceTestSupport {
    @Resource SemanticServiceV3 metadata;
    @Resource SemanticQueryServiceV3 queries;
    @Resource SemanticQueryPayloadMapper mapper;
    @Resource SystemBundlesContext bundles;
    @Resource com.foggyframework.dataset.model.validation.DetachedModelValidationFactory validation;
    @TempDir Path directory;
    static final String TABLE="issue155_time", MODEL="Issue155Query", BUNDLE="issue155-time-metadata";

    @BeforeEach void setup() throws Exception {
        jdbcTemplate.execute("DROP TABLE IF EXISTS "+TABLE);
        String timestamp=getDialectKey().equals("sqlserver")?"DATETIME2 NULL":"TIMESTAMP NULL";
        jdbcTemplate.execute("CREATE TABLE "+TABLE+" (id INTEGER PRIMARY KEY, business_at "+timestamp+", loaded_at "+timestamp
                +", created_at "+timestamp+", year_no INTEGER, label VARCHAR(50), amount DECIMAL(10,2))");
        for(int i=1;i<=2;i++) {
            jdbcTemplate.update("INSERT INTO "+TABLE+" VALUES (?,?,?,?,?,?,?)",i,date("2012-11-"+(27+i)),
                    date("2012-12-0"+i),date("2012-11-"+(27+i)),2012,"row"+i,new BigDecimal(i));
        }
        Files.createDirectories(directory.resolve("model"));Files.createDirectories(directory.resolve("query"));
        writeModel("Issue155Table","system_time","technical_ingestion_only");
        writeModel("Issue155OtherTable","business_date","other_business_axis");
        writeQuery(MODEL,"Issue155Table","{ref:m.id},{ref:m.label},{ref:m.businessAt},{ref:m.businessDay},{ref:m.loadedAt},{ref:m.createdAt},"
                +"{ref:m.updatedAt},{ref:m.fallbackDate},{ref:m.customDate},{ref:m.loadedTime},{ref:m.loadedTime$year},{ref:m.loadedTime$createdAt},{ref:m.amount}");
        writeQuery("Issue155OnlyDimension","Issue155Table","{ref:m.loadedTime}");
        writeQuery("Issue155Renamed","Issue155Table","{ref:m.loadedAt,name:'technicalAt',alias:'technicalOut'},"
                +"{ref:m.loadedTime$id,name:'ingestionKey',alias:'ingestionOut'}");
        writeQuery("Issue155OtherQuery","Issue155OtherTable","{ref:m.loadedAt}");
        assertTrue(bundles.addExternalBundle(BUNDLE,"",directory.toString(),false));
    }
    private Object date(String day) {
        return getDialectKey().equals("sqlite")?day+" 00:00:00":java.time.LocalDateTime.parse(day+"T00:00:00");
    }
    private void writeModel(String name,String loadedRole,String loadedUse) throws Exception {
        Files.writeString(directory.resolve("model/"+name+".tm"),"""
            export const model={name:'%s',tableName:'issue155_time',idColumn:'id',properties:[
              {column:'id',name:'id',type:'INTEGER'},
              {column:'label',name:'label',type:'STRING',recommendedUse:'label_filter_only'},
              {column:'business_at',name:'businessAt',type:'DATETIME',timeRole:'business_date',recommendedUse:'business_axis_control'},
              {column:'business_at',name:'businessDay',type:'DAY',timeRole:'event_time',recommendedUse:`day | event
            axis`},
              {column:'loaded_at',name:'loadedAt',type:'DATETIME',timeRole:'%s',recommendedUse:'%s'},
              {column:'created_at',name:'createdAt',type:'DATETIME',timeRole:'business_date',recommendedUse:'explicit_business_axis'},
              {column:'created_at',name:'updatedAt',type:'DATETIME'},
              {column:'business_at',name:'fallbackDate',type:'DATETIME',timeRole:' ',recommendedUse:''},
              {column:'business_at',name:'customDate',type:'DATETIME',timeRole:'posting_date',recommendedUse:'posting_only'}],
              dimensions:[{name:'loadedTime',foreignKey:'loaded_at',primaryKey:'loaded_at',captionColumn:'loaded_at',
                type:'DATETIME',timeRole:'system_time',recommendedUse:'technical_dimension_only',properties:[
                  {column:'year_no',name:'year',type:'INTEGER'},
                  {column:'created_at',name:'createdAt',type:'DATETIME',timeRole:'event_time',recommendedUse:'dimension_event'}]}],
              measures:[{column:'amount',name:'amount',type:'NUMBER',aggregation:'sum'}]};
            """.formatted(name,loadedRole,loadedUse));
    }
    private void writeQuery(String name,String table,String items) throws Exception {
        Files.writeString(directory.resolve("query/"+name+".qm"),"const m=loadTableModel('"+table+"');export const queryModel={name:'"+name
                +"',loader:'v2',model:m,columnGroups:[{caption:'Times',items:["+items+"]}],accesses:[]};");
    }
    @AfterEach void clean() {
        if(bundles.containBundle(BUNDLE))bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");jdbcTemplate.execute("DROP TABLE IF EXISTS "+TABLE);
    }
    private SemanticMetadataResponse describe(String format,SemanticRequestContext context,String... names) {
        var request=new SemanticMetadataRequest();request.setQmModels(List.of(names));
        return metadata.getMetadata(request,format,context);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> fields(String... names) {
        var data=describe("json",SemanticRequestContext.empty(),names).getData();
        assertFalse(data.containsKey("modelErrors"),data.toString());
        return (Map<String,Object>)data.get("fields");
    }
    @SuppressWarnings("unchecked") private Map<String,Object> field(Map<String,Object> fields,String name) {
        assertTrue(fields.containsKey(name),fields.keySet().toString());return (Map<String,Object>)fields.get(name);
    }
    private void semantic(String name,String role,String use) {
        var field=field(fields(MODEL),name);
        assertEquals(role,field.get("timeRole"),name);assertEquals(use,field.get("recommendedUse"),name);
        String line=describe("markdown",SemanticRequestContext.empty(),MODEL).getContent().lines()
                .filter(s->s.startsWith("| "+name+" | ")).findFirst().orElseThrow();
        assertTrue(line.contains("| "+(role==null?"-":role)+" | "+(use==null?"-":use)+" |"),line);
    }
    @Test void explicitPropertyValuesSurviveLoadingAndOverrideOppositeNameInference() {
        semantic("loadedAt","system_time","technical_ingestion_only");
        semantic("createdAt","business_date","explicit_business_axis");
        semantic("businessAt","business_date","business_axis_control");
        var property=queryModelLoader.getJdbcQueryModel(MODEL,"").getJdbcModel().findJdbcPropertyByName("loadedAt");
        assertEquals("system_time",property.getTimeRole());assertEquals("technical_ingestion_only",property.getRecommendedUse());
    }
    @Test void unspecifiedAndBlankAnnotationsKeepFallbackAndCustomRolesArePreserved() {
        semantic("updatedAt","system_time",null);semantic("fallbackDate","business_date",null);
        semantic("customDate","posting_date","posting_only");
        assertEquals("label_filter_only",field(fields(MODEL),"label").get("recommendedUse"));
        assertTrue(describe("markdown",SemanticRequestContext.empty(),MODEL).getContent().contains("recommendedUse=label_filter_only"));
    }
    @Test void dimensionIdAndRootHaveAnnotationsAndCaptionDoesNotInherit() {
        semantic("loadedTime$id","system_time","technical_dimension_only");
        var f=fields(MODEL);
        assertEquals("system_time",field(f,"loadedTime").get("timeRole"));
        assertNull(field(f,"loadedTime$caption").get("timeRole"));
        assertNull(field(f,"loadedTime$caption").get("recommendedUse"));
    }
    @Test void dayTypedPropertyKeepsRawPurposeInJsonAndEscapesMarkdown() {
        var f=field(fields(MODEL),"businessDay");
        assertEquals("DAY",f.get("type"));assertEquals("event_time",f.get("timeRole"));
        assertEquals("day | event\naxis",f.get("recommendedUse"));
        String md=describe("markdown",SemanticRequestContext.empty(),MODEL).getContent();
        assertTrue(md.contains("event_time | day \\| event axis |"),md);
    }
    @Test void dimensionPropertiesUseOwnAnnotationsWithoutParentRoleInheritance() {
        semantic("loadedTime$createdAt","event_time","dimension_event");
        semantic("loadedTime$year",null,null);
    }
    @Test void dimensionOnlyQueryHasConsistentMarkdown() {
        String md=describe("markdown",SemanticRequestContext.empty(),"Issue155OnlyDimension").getContent();
        assertTrue(md.contains("| system_time | technical_dimension_only |"),md);
        assertEquals("system_time",field(fields("Issue155OnlyDimension"),"loadedTime$id").get("timeRole"));
    }
    @Test void renamedQueryFieldsPreserveSemanticsAndOutputKeys() {
        var f=fields("Issue155Renamed");
        assertEquals("system_time",field(f,"technicalAt").get("timeRole"));
        assertEquals("technicalOut",field(f,"technicalAt").get("outputName"));
        String md=describe("markdown",SemanticRequestContext.empty(),"Issue155Renamed").getContent();
        assertTrue(md.contains("| technicalAt | technicalOut |"),md);
        assertTrue(md.contains("system_time | technical_ingestion_only"),md);
        assertTrue(md.contains("system_time | technical_dimension_only"),md);
        var denied=SemanticRequestContext.of(null,(com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.SecurityContext)null,
                null,List.of(new DeniedPhysicalColumn(null,TABLE,"loaded_at")));
        String hidden=describe("json",denied,"Issue155Renamed").getData().toString();
        assertFalse(hidden.contains("technical_ingestion_only"),hidden);
        assertFalse(hidden.contains("technical_dimension_only"),hidden);
    }
    @Test @SuppressWarnings("unchecked") void sharedFieldNamesKeepEachModelsRoleInJsonAndMultiMarkdown() {
        var f=field(fields(MODEL,"Issue155OtherQuery"),"loadedAt");
        assertNull(f.get("timeRole"));assertNull(f.get("recommendedUse"));
        var models=(Map<String,Map<String,Object>>)f.get("models");
        assertEquals("system_time",models.get(MODEL).get("timeRole"));
        assertEquals("business_date",models.get("Issue155OtherQuery").get("timeRole"));
        String md=describe("markdown",SemanticRequestContext.empty(),MODEL,"Issue155OtherQuery").getContent();
        assertTrue(md.contains("timeRole=system_time"),md);assertTrue(md.contains("recommendedUse=other_business_axis"),md);
    }
    @Test void metadataRespectsFieldPermissionsAndDoesNotExposeHiddenPurpose() {
        var context=SemanticRequestContext.of(null,(com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.SecurityContext)null,Set.of("createdAt"));
        String json=describe("json",context,MODEL).getData().toString();
        String md=describe("markdown",context,MODEL).getContent();
        assertFalse(json.contains("technical_ingestion_only"));assertFalse(md.contains("technical_ingestion_only"));
        assertTrue(json.contains("explicit_business_axis"));assertTrue(md.contains("explicit_business_axis"));
    }
    @Test void detachedValidationRetainsAnnotationsWithoutChangingLiveModel() {
        try(var session=validation.open("issue155-detached","",directory.toString())) {
            session.validateTableModel(session.sourceBundle().findBundleResource("Issue155Table.tm",true),"");
            var model=session.resolveQueryModel(MODEL,"").model();
            var loaded=model.getJdbcModel().findJdbcPropertyByName("loadedAt");
            assertEquals("system_time",loaded.getTimeRole());assertEquals("technical_ingestion_only",loaded.getRecommendedUse());
        }
        semantic("loadedAt","system_time","technical_ingestion_only");
    }
    @Test void invalidTypesHaveFieldLocatedDiagnosticsInDetachedAndLiveLoading(@TempDir Path invalid) throws Exception {
        for(String annotation:List.of("timeRole:123","recommendedUse:[]")) {
            Files.writeString(invalid.resolve("Issue155Invalid.tm"),"export const model={name:'Issue155Invalid',tableName:'issue155_time',idColumn:'id',"
                    +"properties:[{column:'loaded_at',name:'loadedAt',type:'DATETIME',"+annotation+"}]};");
            try(var session=validation.open("issue155-invalid-draft","",invalid.toString())) {
                var error=assertThrows(RuntimeException.class,()->session.validateTableModel(session.sourceBundle().findBundleResource("Issue155Invalid.tm",true),""));
                assertTrue(messages(error).contains("Issue155Invalid.properties[0](loadedAt)."+annotation.split(":")[0]),messages(error));
            }
            assertTrue(bundles.addExternalBundle("issue155-invalid-live","",invalid.toString(),false));
            try {
                var error=assertThrows(RuntimeException.class,()->tableModelLoaderManager.load("Issue155Invalid",""));
                assertTrue(messages(error).contains("INVALID_TIME_METADATA"),messages(error));
            } finally { bundles.removeBundle("issue155-invalid-live"); }
        }
    }
    private String messages(Throwable error) {
        StringBuilder text=new StringBuilder();
        for(Throwable current=error;current!=null;current=current.getCause())text.append(current.getMessage()).append('\n');
        return text.toString();
    }
    @Test void explicitSystemAxisStillExecutesAllPhasesAndIsNotReplacedByBusinessRole() {
        Map<String,Object> payload=Map.of("columns",List.of("loadedAt","amount__rolling_7d"),"groupBy",List.of("loadedAt"),
                "orderBy",List.of("loadedAt"),"timeWindow",Map.of("field","loadedAt","grain","day","comparison","rolling_7d",
                        "rollingMode","calendar","value",List.of("2012-12-01","2012-12-03"),"targetMetrics",List.of("amount")));
        for(String mode:List.of("validate","preview","execute")) {
            var response=queries.queryModel(MODEL,mapper.toQueryRequest(payload),mode,SemanticRequestContext.empty());
            if(mode.equals("execute")) {
                assertEquals(2,response.getItems().size());
                assertTrue(response.getItems().get(0).get("loadedAt").toString().startsWith("2012-12-01"));
                assertEquals(0,new BigDecimal(response.getItems().get(1).get("amount__rolling_7d").toString()).compareTo(new BigDecimal("3")));
            }
        }
    }
}
