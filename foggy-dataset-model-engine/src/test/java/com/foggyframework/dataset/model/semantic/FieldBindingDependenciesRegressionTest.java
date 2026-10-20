package com.foggyframework.dataset.model.semantic;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.service.*;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@org.springframework.test.context.TestPropertySource(properties="field.binding.regression=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class FieldBindingDependenciesRegressionTest extends EcommerceTestSupport {
 @Resource SystemBundlesContext bundles;
 @Resource SemanticServiceV3 metadata;
 @Resource SemanticQueryServiceV3 queries;
 @Resource SemanticQueryPayloadMapper mapper;
 @Resource ObjectMapper json;
 @TempDir static Path models;
 @BeforeAll void setup() throws Exception {
  jdbcTemplate.execute("CREATE TABLE naming_followup (id INTEGER PRIMARY KEY, category VARCHAR(20), amount INTEGER, secret INTEGER)");
  jdbcTemplate.update("INSERT INTO naming_followup VALUES (1,'A',10,300),(2,'A',20,100),(3,'B',40,200)");
  Files.createDirectories(models.resolve("model"));Files.createDirectories(models.resolve("query"));
  Files.writeString(models.resolve("model/AuditModel.tm"),"export const model={name:'AuditModel',tableName:'naming_followup',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'},{name:'secret',column:'secret',type:'INTEGER'}],measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'},{name:'meanAmount',column:'amount',type:'NUMBER',aggregation:'avg'}]};");
  String base="{ref:m.id},{ref:m.category},{ref:m.secret},{ref:m.amount}";
  qm("Base",base);
  qm("Avg",base+",{ref:m.meanAmount}");
  qm("AvgRenamed",base+",{ref:m.meanAmount,name:'meanSales',alias:'meanSales'}");
  qm("Measure","{ref:m.id},{ref:m.category},{ref:m.secret},{ref:m.amount,name:'revenue',alias:'revOut'}");
  qm("Split","{ref:m.id},{ref:m.category,name:'groupKey',alias:'groupOut'},{ref:m.secret},{ref:m.amount,name:'revenue',alias:'revOut'}");
  qm("Both","{ref:m.category},{ref:m.amount},{ref:m.amount,name:'revenue',alias:'revOut'}");
  qm("AvgSplit","{ref:m.category,name:'groupKey',alias:'groupOut'},{ref:m.meanAmount,name:'meanSales',alias:'avgOut'}");
  qm("WindowAlias",base+",{name:'ranked',alias:'rankOut',formula:'ROW_NUMBER()',type:'INTEGER',windowOrderBy:[{field:'secret',dir:'ASC'}]}");
  qm("Formula",base+",{name:'calc',alias:'calcOut',formula:'amount * 2',type:'NUMBER'}");
  qm("Window",base+",{name:'ranked',formula:'ROW_NUMBER()',type:'INTEGER',windowOrderBy:[{field:'secret',dir:'ASC'}]}");
  qm("WindowPartition",base+",{name:'ranked',formula:'ROW_NUMBER()',type:'INTEGER',partitionBy:['secret'],windowOrderBy:[{field:'id',dir:'ASC'}]}");
  qm("Chain",base+",{name:'firstCalc',formula:'amount * 2',type:'NUMBER'},{name:'secondCalc',formula:'firstCalc + 1',type:'NUMBER'}");
  assertTrue(bundles.addExternalBundle("naming-followup-audit","",models.toString(),false));
 }
 void qm(String name,String items)throws Exception {Files.writeString(models.resolve("query/Audit"+name+".qm"),"const m=loadTableModel('AuditModel');export const queryModel={name:'Audit"+name+"',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:["+items+"]}],accesses:[]};");}
 Object query(String model,Map<String,Object> payload,SemanticRequestContext ctx){return queries.queryModel("Audit"+model,mapper.toQueryRequest(payload),"execute",ctx);}
 Object meta(String model,String format,SemanticRequestContext ctx){SemanticMetadataRequest r=new SemanticMetadataRequest();r.setQmModels(List.of("Audit"+model));return metadata.getMetadata(r,format,ctx);}
 Map<String,Object> pivot(String field,String metric,boolean top){Map<String,Object> axis=new LinkedHashMap<>();axis.put("field",field);if(top){axis.put("limit",1);axis.put("orderBy",List.of("-"+metric));}return Map.of("pivot",Map.of("rows",List.of(axis),"metrics",List.of(metric),"outputFormat","grid","options",Map.of("grandTotal",false)));}
 Map<String,Object> totalPivot(String field,String metric){var req=new LinkedHashMap<String,Object>(pivot(field,metric,false));var p=new LinkedHashMap<String,Object>((Map<String,Object>)req.get("pivot"));p.put("options",Map.of("grandTotal",true));req.put("pivot",p);return req;}
 com.fasterxml.jackson.databind.JsonNode result(String model, Map<String,Object> payload, SemanticRequestContext ctx) { return json.valueToTree(query(model,payload,ctx)); }
 com.fasterxml.jackson.databind.JsonNode fields(String model, SemanticRequestContext ctx) {return json.valueToTree(meta(model,"json",ctx)).path("data").path("fields");}
 SemanticRequestContext denied(String column) {return SemanticRequestContext.of(null,(ModelResultContext.SecurityContext)null,null,List.of(new DeniedPhysicalColumn(null,"naming_followup",column)));}
 @Test void windowOrderAndPartitionRespectPhysicalPermissions() {
  for(String model:List.of("Window","WindowPartition")) {
   assertThrows(RuntimeException.class,()->query(model,Map.of("columns",List.of("ranked")),denied("secret")),model);
  }
 }
 @Test void windowWhitelistAndMetadataUseCompleteDependencies() {
  var ctx=SemanticRequestContext.of(null,(ModelResultContext.SecurityContext)null,Set.of("ranked"));
  assertAll(()->assertThrows(RuntimeException.class,()->query("Window",Map.of("columns",List.of("ranked")),ctx)),
   ()->assertFalse(fields("Window",ctx).has("ranked")),
   ()->assertFalse(fields("Window",denied("secret")).has("ranked")));
 }
 @Test void pivotNormalizesAxisAndMeasureOutputKeys() {
  var grid=result("Split",pivot("groupKey","revenue",false),SemanticRequestContext.empty()).path("items").get(0);
  assertEquals(2,grid.path("rowHeaders").size());
  Map<String,Integer> values = new LinkedHashMap<>();
  for (int i=0; i<grid.path("rowHeaders").size(); i++) values.put(
      grid.path("rowHeaders").get(i).path("groupKey").asText(), grid.path("cells").get(i).get(0).asInt());
  assertEquals(Map.of("A",30,"B",40), values);
 }
 @Test void pivotTopNUsesBoundMeasure() {
  var grid=result("Split",pivot("groupKey","revenue",true),SemanticRequestContext.empty()).path("items").get(0);
  assertEquals("B",grid.path("rowHeaders").get(0).path("groupKey").asText());
  assertEquals(40,grid.path("cells").get(0).get(0).asInt());
 }
 @Test void renamedAverageTotalRetainsAggregation() {
  var control=result("Avg",totalPivot("category","meanAmount"),SemanticRequestContext.empty()).path("items").get(0);
  var renamed=result("AvgRenamed",totalPivot("category","meanSales"),SemanticRequestContext.empty()).path("items").get(0);
  assertEquals(control.path("cells"),renamed.path("cells"));
 }
 @Test void measureMetadataContainsOnlyQueryableNames() {
  var fields=fields("Measure",SemanticRequestContext.empty());
  assertTrue(fields.has("revenue"));assertFalse(fields.has("amount"));assertFalse(fields.has("meanAmount"));
 }
 @Test void resultSchemaUsesActualOutputKeys() {
  var response=result("Measure",Map.of("columns",List.of("category","revenue"),"groupBy",List.of("category")),SemanticRequestContext.empty());
  for(var col:response.path("schema").path("columns")) assertTrue(response.path("items").get(0).has(col.path("name").asText()),col.toString());
 }
 @Test void formulaHonorsModelAndRequestOutputAlias() {
  var response=result("Formula",Map.of("columns",List.of("calc")),SemanticRequestContext.empty());
  assertTrue(response.path("items").get(0).has("calcOut"));
  var requested=result("Formula",Map.of("columns",List.of("calc AS requestedOut")),SemanticRequestContext.empty());
  assertTrue(requested.path("items").get(0).has("requestedOut"));
  assertEquals(response.path("items").size(), requested.path("items").size());
  assertEquals(response.path("items").get(0).get("calcOut"), requested.path("items").get(0).get("requestedOut"));
 }
 @Test void predefinedDependencyClosureDoesNotExposeDependencies() {
  var response=result("Chain",Map.of("columns",List.of("secondCalc")),SemanticRequestContext.empty());
  assertEquals(3,response.path("items").size());
  assertEquals(81,response.path("items").get(0).path("secondCalc").asInt());
  assertFalse(response.path("items").get(0).has("firstCalc"));
 }
 @Test void selectedAggregateOrderAliasChecksSourcePermissions() {
  var payload=Map.<String,Object>of("columns",List.of("category","sum(amount) AS total"),"groupBy",List.of("category"),"orderBy",List.of("total"));
  var ctx=SemanticRequestContext.of(null,(ModelResultContext.SecurityContext)null,Set.of("category","amount"));
  var response=result("Base",payload,ctx);
  assertEquals(30,response.path("items").get(0).path("total").asInt());
  assertThrows(RuntimeException.class,()->query("Base",payload,denied("amount")));
 }
 @Test void existingNamesAndSourcePermissionChecksRemainCompatible() {
  assertEquals(2,result("Base",pivot("category","amount",false),SemanticRequestContext.empty()).path("items").get(0).path("cells").size());
  assertThrows(RuntimeException.class,()->query("Measure",Map.of("columns",List.of("amount")),SemanticRequestContext.empty()));
  assertThrows(RuntimeException.class,()->query("Measure",pivot("category","revenue",false),denied("amount")));
  assertThrows(RuntimeException.class,()->query("Formula",Map.of("columns",List.of("calc")),denied("amount")));
 }
 @Test void renamedNonAdditiveTotalMatchesUnrenamedControl() {
  var control=result("Avg",totalPivot("category","meanAmount"),SemanticRequestContext.empty()).path("items").get(0);
  var renamed=result("AvgSplit",totalPivot("groupKey","meanSales"),SemanticRequestContext.empty()).path("items").get(0);
  assertEquals(control.path("cells"),renamed.path("cells"));
  assertEquals(control.path("rowHeaders").size(),renamed.path("rowHeaders").size());
 }
 @Test void bothOriginalAndRenamedMeasureRemainVisible() {
  var fields=fields("Both",SemanticRequestContext.empty());
  assertTrue(fields.has("amount"));assertTrue(fields.has("revenue"));
  var response=result("Both",Map.of("columns",List.of("category","amount","revenue"),"groupBy",List.of("category")),SemanticRequestContext.empty());
  assertEquals(response.path("items").get(0).get("amount"),response.path("items").get(0).get("revOut"));
 }
 @Test void windowOutputAliasSurvivesCteProjection() {
  if (!supportsWindowFunctions()) {
   var error=assertThrows(RuntimeException.class,()->query("WindowAlias",Map.of("columns",List.of("id","ranked")),SemanticRequestContext.empty()));
   assertTrue(error.toString().contains("WINDOW_DIALECT_UNSUPPORTED"),error.toString());return;
  }
  var response=result("WindowAlias",Map.of("columns",List.of("id","ranked")),SemanticRequestContext.empty());
  assertEquals(3,response.path("items").size());
  assertTrue(response.path("items").get(0).has("rankOut"));
 }
 @Test void outputAliasDoesNotBecomeGeneralInputSynonym() {
  assertThrows(RuntimeException.class,()->query("Measure",Map.of("columns",List.of("revOut")),SemanticRequestContext.empty()));
  assertThrows(RuntimeException.class,()->query("Formula",Map.of("columns",List.of("calcOut")),SemanticRequestContext.empty()));
 }
 @Test void formulaMetadataReportsOutputName() {
  assertEquals("calcOut",fields("Formula",SemanticRequestContext.empty()).path("calc").path("outputName").asText());
 }
 @Test void pivotFormatsPreserveLogicalNamesAndValues() throws Exception {
  for(String format:List.of("tree","flat","grid")) {
   var base=Map.<String,Object>of("pivot",Map.of("rows",List.of(Map.of("field","category")),"metrics",List.of("amount"),"outputFormat",format,"options",Map.of("grandTotal",false)));
   var renamed=Map.<String,Object>of("pivot",Map.of("rows",List.of(Map.of("field","groupKey")),"metrics",List.of("revenue"),"outputFormat",format,"options",Map.of("grandTotal",false)));
   String expected=result("Base",base,SemanticRequestContext.empty()).path("items").toString().replace("category","groupKey").replace("amount","revenue");
   var actual=result("Split",renamed,SemanticRequestContext.empty()).path("items");
   assertTrue(json.readTree(expected).equals((left,right) -> left.isNumber() && right.isNumber()
       ? left.decimalValue().compareTo(right.decimalValue()) : left.equals(right) ? 0 : 1, actual),
       format + ": " + actual);
  }
 }
 @Test void aggregateAliasMatchingItsSourceDoesNotCreatePermissionCycle() {
  var ctx=SemanticRequestContext.of(null,(ModelResultContext.SecurityContext)null,Set.of("category","amount"));
  var response=result("Base",Map.of("columns",List.of("category","sum(amount) AS amount"),"groupBy",List.of("category"),"orderBy",List.of("amount")),ctx);
  assertEquals(30,response.path("items").get(0).path("amount").asInt());
 }
 @Test void differentSelectedFieldsCannotOverwriteSameOutputKey() {
  var error=assertThrows(RuntimeException.class,()->query("Formula",Map.of("columns",List.of("calc","amount AS calcOut")),SemanticRequestContext.empty()));
  assertTrue(error.toString().contains("ALIAS_COLLISION"),error.toString());
 }
 @Test void schemaSummaryKeepsQueryIdentityForAliasedGrouping() {
  var response=result("Split",Map.of("columns",List.of("groupKey","revenue"),"groupBy",List.of("groupKey")),SemanticRequestContext.empty());
  assertEquals("groupOut",response.path("schema").path("columns").get(0).path("name").asText());
  assertEquals("groupKey",response.path("schema").path("columns").get(0).path("queryName").asText());
  assertTrue(response.path("schema").path("summary").asText().contains("分组"));
 }
 @AfterAll void cleanup(){if(bundles.containBundle("naming-followup-audit"))bundles.removeBundle("naming-followup-audit");queryModelLoader.clearByNamespace("");jdbcTemplate.execute("DROP TABLE IF EXISTS naming_followup");}
}
