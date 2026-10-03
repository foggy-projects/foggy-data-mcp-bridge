package com.foggyframework.dataset.mcp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.mcp.DatasetMcpAiAutoConfiguration;
import com.foggyframework.dataset.mcp.DatasetMcpAutoConfiguration;
import com.foggyframework.dataset.mcp.chart.ChartRenderRequest;
import com.foggyframework.dataset.mcp.chart.QueryImageRenderer;
import com.foggyframework.dataset.mcp.chart.XChartRenderer;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.controller.AnalystMcpController;
import com.foggyframework.dataset.mcp.service.McpService;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import com.foggyframework.dataset.mcp.service.NamespaceToolPolicyService;
import com.foggyframework.dataset.mcp.service.QueryImageExportService;
import com.foggyframework.dataset.mcp.service.ToolConfigLoader;
import com.foggyframework.dataset.mcp.service.ToolFilterService;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.dataset.mcp.spi.impl.LocalDatasetAccessor;
import com.foggyframework.dataset.mcp.spi.impl.SemanticServiceResolverImpl;
import com.foggyframework.dataset.mcp.storage.ChartStorageAdapter;
import com.foggyframework.dataset.mcp.tools.QueryImageExportTool;
import com.foggyframework.dataset.mcp.tools.QueryModelTool;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Real SQLite -> governed semantic query -> in-process PNG, without a render sidecar. */
@SpringBootTest(
        classes = QueryImageExportIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "server.address=127.0.0.1",
                "foggy.dataset.show-sql=false",
                "foggy.dataset.show-sql-parameters=false"
        })
class QueryImageExportIntegrationTest {

    private static final String PRIMARY_NS = "image-primary";
    private static final String SECONDARY_NS = "image-secondary";
    private static final String TABLE_MODEL = "ImageSalesModel";
    private static final String QUERY_MODEL = "ImageSalesQuery";
    // An intentionally synthetic test identity. It must never appear in image output/evidence.
    private static final String TEST_IDENTITY = "image-fixture-reader";
    private static final String EAST_ONLY_IDENTITY = "image-fixture-east-reader";
    private static final List<String> COLUMNS =
            List.of("month", "salesAmount", "targetAmount", "orders");
    private static final double[] AMOUNTS = {120.5, 151.2, 136.8, 184.6, 201.3, 228.9};

    @TempDir
    Path tempDirectory;
    @Resource
    private SystemBundlesContext bundlesContext;
    @Resource
    private SemanticServiceV3 semanticService;
    @Resource
    private SemanticQueryServiceV3 semanticQueryService;
    @Resource
    private SemanticModelCatalogService modelCatalog;
    @Resource
    private DatasetProperties datasetProperties;
    @Resource
    private DataSource dataSource;
    @Resource
    private QueryImageRenderer realRenderer;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private TestRestTemplate http;
    @Resource(name = "httpDatasetAccessor")
    private RecordingDatasetAccessor httpAccessor;
    @LocalServerPort
    private int httpPort;

    private JdbcTemplate jdbc;
    private RecordingDatasetAccessor accessor;
    private QueryImageRenderer renderer;
    private ChartStorageAdapter storage;
    private QueryImageExportTool tool;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        createRows("image_primary_sales", 0);
        createRows("image_secondary_sales", 1000);
        registerBundle(PRIMARY_NS, "image_primary_sales");
        registerBundle(SECONDARY_NS, "image_secondary_sales");

        SemanticServiceResolver resolver = new SemanticServiceResolverImpl(
                semanticService, semanticQueryService, bundlesContext, modelCatalog);
        accessor = new RecordingDatasetAccessor(resolver, datasetProperties);
        renderer = spy(realRenderer);
        storage = mock(ChartStorageAdapter.class);
        tool = new QueryImageExportTool(new QueryImageExportService(
                new QueryModelTool(accessor), renderer, storage));
    }

    @AfterEach
    void tearDown() {
        for (String namespace : List.of(PRIMARY_NS, SECONDARY_NS)) {
            String bundle = "query-image-" + namespace;
            if (bundlesContext.containBundle(bundle)) {
                bundlesContext.removeBundle(bundle);
            }
        }
    }

    @Test
    void realQueryGeneratesChineseTableBarAndLineEvidence() throws Exception {
        List<Map<String, Object>> expected = nativeRows("image_primary_sales", null);
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (String kind : List.of("table", "bar", "line")) {
            clearInvocations(renderer);
            Map<String, Object> arguments = arguments(kind);
            long started = System.nanoTime();
            Map<String, Object> result = export(arguments, context(PRIMARY_NS));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertThat(result.get("success")).as("%s export: %s", kind, safeFailure(result)).isEqualTo(true);
            assertThat(result.get("rowsRendered")).isEqualTo(6);
            assertThat(result.get("rowsReturned")).isEqualTo(6);
            assertThat(result.get("truncated")).isEqualTo(false);
            assertRowsEqual(accessor.lastResponse.getItems(), expected);
            ArgumentCaptor<ChartRenderRequest> renderedRequest = ArgumentCaptor.forClass(ChartRenderRequest.class);
            verify(renderer).render(renderedRequest.capture());
            assertRowsEqual(renderedRequest.getValue().data(), expected);
            assertThat(String.valueOf(renderedRequest.getValue().config().get("footer")))
                    .contains("显示 6 行", "本次查询返回 6 行")
                    .doesNotContain("查询共 0 行");
            assertThat(accessor.lastResponse.getSchema().getColumns())
                    .extracting(SemanticQueryResponse.SchemaInfo.ColumnDef::getTitle)
                    .contains("月份", "销售额（万元）", "目标额（万元）", "订单数（笔）");
            byte[] png = imageBytes(result);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(decoded).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(1200);
            assertThat(decoded.getHeight()).isBetween(360, 4096);
            if (!"table".equals(kind)) assertThat(decoded.getHeight()).isEqualTo(720);
            Files.write(evidenceDirectory().resolve(kind + ".png"), png);
            evidence.add(Map.of("kind", kind, "width", decoded.getWidth(),
                    "height", decoded.getHeight(), "pngBytes", png.length,
                    "elapsedMillis", elapsedMillis, "rowsRendered", result.get("rowsRendered")));
            assertSafeResult(result, png);
        }
        assertThat(accessor.queryCount).isEqualTo(3);
        verifyNoInteractions(storage);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("source", "SQLite semantic query fixture");
        manifest.put("namespace", PRIMARY_NS);
        manifest.put("model", QUERY_MODEL);
        manifest.put("payload", queryPayload(COLUMNS));
        manifest.put("rows", accessor.lastResponse.getItems());
        manifest.put("schema", accessor.lastResponse.getSchema().getColumns());
        manifest.put("headless", GraphicsEnvironment.isHeadless());
        manifest.put("renders", evidence);
        Files.writeString(evidenceDirectory().resolve("query-image-evidence.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
    }

    @Test
    void sameModelNameInTwoNamespacesUsesDifferentRealRows() throws Exception {
        Map<String, Object> primary = export(arguments("table"), context(PRIMARY_NS));
        assertThat(primary.get("success")).isEqualTo(true);
        assertRowsEqual(accessor.lastResponse.getItems(), nativeRows("image_primary_sales", null));
        Map<String, Object> secondary = export(arguments("table"), context(SECONDARY_NS));
        assertThat(secondary.get("success")).isEqualTo(true);
        assertRowsEqual(accessor.lastResponse.getItems(), nativeRows("image_secondary_sales", null));
        assertThat(imageBytes(secondary)).isNotEqualTo(imageBytes(primary));
        assertThat(accessor.lastNamespace).isEqualTo(SECONDARY_NS);
        assertThat(accessor.queryCount).isEqualTo(2);
    }

    @Test
    void rowGovernanceAndExplicitTruncationApplyBeforeRendering() throws Exception {
        Map<String, Object> arguments = arguments("table");
        arguments.put("systemSlice", List.of(Map.of("field", "region", "op", "=", "value", "华东")));
        arguments.put("maxRows", 2);
        Map<String, Object> result = export(arguments, context(PRIMARY_NS));
        assertThat(result.get("success")).isEqualTo(true);
        assertRowsEqual(accessor.lastResponse.getItems(), nativeRows("image_primary_sales", "华东"));
        assertThat(result.get("rowsReturned")).isEqualTo(3);
        assertThat(result.get("rowsRendered")).isEqualTo(2);
        assertThat(result.get("truncated")).isEqualTo(true);
        Files.write(evidenceDirectory().resolve("table-truncated.png"), imageBytes(result));
        verifyNoInteractions(storage);
    }

    @Test
    void modelPermissionRowPredicatesBoundExportsAndIntersectWithSystemSlice() throws Exception {
        ToolExecutionContext eastReader = ToolExecutionContext.builder().traceId("image-row-permission")
                .namespace(PRIMARY_NS).authorization(EAST_ONLY_IDENTITY).build();
        Map<String, Object> result = export(arguments("table"), eastReader);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("rowsReturned")).isEqualTo(3);
        assertRowsEqual(accessor.lastResponse.getItems(), nativeRows("image_primary_sales", "华东"));
        Files.write(evidenceDirectory().resolve("table-row-permission.png"), imageBytes(result));
        Files.writeString(evidenceDirectory().resolve("query-row-permission-rows.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(accessor.lastResponse.getItems()));

        clearInvocations(renderer, storage);
        Map<String, Object> conflictingSlice = arguments("table");
        conflictingSlice.put("systemSlice", List.of(Map.of("field", "region", "op", "=", "value", "华南")));
        Map<String, Object> empty = export(conflictingSlice, eastReader);
        assertDeniedWithoutImage(empty);
        assertThat(((Map<?, ?>) empty.get("error")).get("code")).isEqualTo("NO_QUERY_DATA");
        verifyNoInteractions(renderer, storage);
    }

    @Test
    void nullAndNegativeValuesArePreservedInRealQueryAndImages() throws Exception {
        jdbc.update("UPDATE image_primary_sales SET sales_amount = NULL WHERE month = ?", "2026-03");
        jdbc.update("UPDATE image_primary_sales SET sales_amount = ? WHERE month = ?", -24.5, "2026-04");
        List<Map<String, Object>> expected = nativeRows("image_primary_sales", null);
        for (String kind : List.of("table", "line")) {
            Map<String, Object> arguments = arguments(kind);
            arguments.put("title", "销售额验证：缺失值与负数");
            Map<String, Object> result = export(arguments, context(PRIMARY_NS));
            assertThat(result.get("success")).as("%s export: %s", kind, safeFailure(result)).isEqualTo(true);
            assertRowsEqual(accessor.lastResponse.getItems(), expected);
            assertThat(accessor.lastResponse.getItems().get(2).get("salesAmount")).isNull();
            assertThat(new BigDecimal(String.valueOf(accessor.lastResponse.getItems().get(3).get("salesAmount"))))
                    .isEqualByComparingTo("-24.5");
            Files.write(evidenceDirectory().resolve(kind + "-null-negative.png"), imageBytes(result));
        }
        verifyNoInteractions(storage);
        Files.writeString(evidenceDirectory().resolve("query-null-negative-rows.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(accessor.lastResponse.getItems()));

        jdbc.update("UPDATE image_primary_sales SET month = ? WHERE month = ?",
                "2026-06 · 华东区域旗舰店及社区配送中心联合销售汇总（含线上渠道）", "2026-06");
        Map<String, Object> longTable = arguments("table");
        longTable.put("title", "销售明细：长文本换行、缺失值与负数");
        longTable.put("columns", List.of(Map.of("field", "month", "caption", "月份／备注"),
                Map.of("field", "salesAmount"), Map.of("field", "targetAmount"), Map.of("field", "orders")));
        Map<String, Object> longResult = export(longTable, context(PRIMARY_NS));
        assertThat(longResult.get("success")).as("long-text table: %s", safeFailure(longResult)).isEqualTo(true);
        assertRowsEqual(accessor.lastResponse.getItems(), nativeRows("image_primary_sales", null));
        Files.write(evidenceDirectory().resolve("table-long-text.png"), imageBytes(longResult));
        Files.writeString(evidenceDirectory().resolve("query-long-text-rows.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(accessor.lastResponse.getItems()));
    }

    @Test
    void actualModelAndColumnPermissionDenialsNeverRenderOrStore() {
        Map<String, Object> unauthorized = export(arguments("table"),
                ToolExecutionContext.builder().traceId("image-denied").namespace(PRIMARY_NS).build());
        assertDeniedWithoutImage(unauthorized);
        verifyNoInteractions(renderer, storage);

        clearInvocations(renderer, storage);
        Map<String, Object> hiddenField = arguments("table");
        hiddenField.put("payload", queryPayload(List.of("month", "secretMargin")));
        hiddenField.put("columns", List.of(Map.of("field", "month"), Map.of("field", "secretMargin")));
        assertDeniedWithoutImage(export(hiddenField, context(PRIMARY_NS)));
        verifyNoInteractions(renderer, storage);

        clearInvocations(renderer, storage);
        Map<String, Object> physicalColumn = arguments("table");
        physicalColumn.put("deniedColumns", List.of(
                Map.of("table", "image_primary_sales", "columns", List.of("sales_amount"))));
        assertDeniedWithoutImage(export(physicalColumn, context(PRIMARY_NS)));
        verifyNoInteractions(renderer, storage);

        clearInvocations(renderer, storage);
        Map<String, Object> emptyQuery = arguments("table");
        emptyQuery.put("systemSlice", List.of(Map.of("field", "region", "op", "=", "value", "无数据区域")));
        Map<String, Object> empty = export(emptyQuery, context(PRIMARY_NS));
        assertDeniedWithoutImage(empty);
        assertThat(((Map<?, ?>) empty.get("error")).get("code")).isEqualTo("NO_QUERY_DATA");
        verifyNoInteractions(renderer, storage);
        assertThat(accessor.queryCount).isEqualTo(4);
    }

    @Test
    void realHttpMcpListsToolAndReturnsNativeImagesWithNamespaceIsolation() throws Exception {
        JsonNode list = rpc("tools/list", Map.of(), PRIMARY_NS, TEST_IDENTITY);
        assertThat(list.has("error")).isFalse();
        assertThat(list.path("result").path("tools").findValuesAsText("name"))
                .contains("dataset.export_image");
        JsonNode imageSchema = null;
        for (JsonNode definition : list.path("result").path("tools")) {
            if ("dataset.export_image".equals(definition.path("name").asText())) {
                imageSchema = definition.path("inputSchema");
            }
        }
        assertThat(imageSchema).isNotNull();
        assertThat(imageSchema.path("properties").has("kind")).isTrue();

        byte[] primary = null;
        for (String namespace : List.of(PRIMARY_NS, SECONDARY_NS)) {
            JsonNode response = rpc("tools/call", Map.of("name", "dataset.export_image",
                    "arguments", arguments("bar")), namespace, TEST_IDENTITY);
            JsonNode result = response.path("result");
            assertThat(response.has("error")).as(response.toString()).isFalse();
            assertThat(result.path("isError").asBoolean()).isFalse();
            assertThat(result.path("structuredContent").path("success").asBoolean()).isTrue();
            assertThat(result.path("structuredContent").path("image").has("data")).isFalse();
            assertThat(result.path("structuredContent").path("rowsReturned").asInt()).isEqualTo(6);
            JsonNode contentImage = result.path("content").get(1);
            assertThat(contentImage.path("type").asText()).isEqualTo("image");
            assertThat(contentImage.path("mimeType").asText()).isEqualTo("image/png");
            byte[] png = Base64.getDecoder().decode(contentImage.path("data").asText());
            assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
            assertThat(httpAccessor.lastNamespace).isEqualTo(namespace);
            assertRowsEqual(httpAccessor.lastResponse.getItems(), nativeRows(
                    PRIMARY_NS.equals(namespace) ? "image_primary_sales" : "image_secondary_sales", null));
            Files.write(evidenceDirectory().resolve("http-bar-" + namespace + ".png"), png);
            if (primary == null) primary = png;
            else assertThat(png).isNotEqualTo(primary);
            assertThat(response.toString()).doesNotContain(TEST_IDENTITY, "jdbc:sqlite");
        }
        JsonNode rejected = rpc("tools/call", Map.of("name", "dataset.export_image",
                "arguments", arguments("table")), PRIMARY_NS, null);
        assertThat(rejected.path("result").path("isError").asBoolean()).isTrue();
        assertThat(rejected.path("result").path("content")).hasSize(1);
        Files.writeString(evidenceDirectory().resolve("http-mcp-contract.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "tool", "dataset.export_image", "toolsList", true,
                        "nativeImage", true, "namespaceIsolation", true,
                        "unauthorizedImageAbsent", true)));
    }

    @Test
    void cliSavesThreePngFilesThroughRealHttpMcpWhenConfigured() throws Exception {
        String python = System.getProperty("foggy.image.cli-python");
        String project = System.getProperty("foggy.image.cli-project");
        Assumptions.assumeTrue(python != null && project != null,
                "set foggy.image.cli-python and foggy.image.cli-project for cross-repository CLI evidence");
        Path payload = evidenceDirectory().resolve("cli-query.json").toAbsolutePath();
        Files.writeString(payload, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(queryPayload(COLUMNS)));
        List<JsonNode> manifests = new ArrayList<>();
        for (String kind : List.of("table", "bar", "line")) {
            Path output = evidenceDirectory().resolve("cli-" + kind + ".png").toAbsolutePath();
            List<String> command = new ArrayList<>(List.of(python, "-m", "foggy_runtime_cli.main",
                    "--base-url", "http://127.0.0.1:" + httpPort, "--namespace", PRIMARY_NS,
                    "query", "export-image", QUERY_MODEL, "--payload", payload.toString(),
                    "--kind", kind, "--out", output.toString(), "--title", "真实查询 CLI 图片验证",
                    "--height", "720"));
            if (!"table".equals(kind)) command.addAll(List.of("--x", "month", "--y", "salesAmount",
                    "--x-label", "月份", "--y-label", "销售额", "--unit", "万元"));
            ProcessBuilder builder = new ProcessBuilder(command).directory(Path.of(project).toFile())
                    .redirectErrorStream(true);
            builder.environment().put("PYTHONPATH", Path.of(project).resolve("src").toString());
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            builder.environment().put("FOGGY_RUNTIME_AUTHORIZATION", TEST_IDENTITY);
            builder.environment().put("NO_PROXY", "127.0.0.1,localhost");
            Process process = builder.start();
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("CLI %s completed within 30 seconds", kind).isTrue();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("CLI %s: %s", kind, stdout).isZero();
            assertThat(stdout).doesNotContain(TEST_IDENTITY, "jdbc:sqlite");
            JsonNode result = objectMapper.readTree(stdout);
            assertThat(result.path("data").path("rowsRendered").asInt()).isEqualTo(6);
            assertThat(result.path("data").path("rowsReturned").asInt()).isEqualTo(6);
            assertThat(result.path("data").path("truncated").asBoolean()).isFalse();
            BufferedImage decoded = ImageIO.read(output.toFile());
            assertThat(decoded).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(result.path("data").path("width").asInt());
            assertThat(decoded.getHeight()).isEqualTo(result.path("data").path("height").asInt());
            assertThat(httpAccessor.lastNamespace).isEqualTo(PRIMARY_NS);
            assertRowsEqual(httpAccessor.lastResponse.getItems(), nativeRows("image_primary_sales", null));
            manifests.add(result);
        }
        Files.writeString(evidenceDirectory().resolve("cli-http-evidence.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifests));
    }

    private JsonNode rpc(String method, Map<String, Object> params, String namespace, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-NS", namespace);
        if (authorization != null) headers.set("Authorization", authorization);
        var response = http.postForEntity("http://127.0.0.1:" + httpPort + "/mcp/analyst/rpc", new HttpEntity<>(Map.of(
                "jsonrpc", "2.0", "id", "image-http-contract", "method", method, "params", params), headers), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Map<String, Object> arguments(String kind) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("model", QUERY_MODEL);
        args.put("payload", queryPayload(COLUMNS));
        args.put("kind", kind);
        args.put("title", "2026 年上半年销售" + ("table".equals(kind) ? "明细" : "趋势"));
        args.put("columns", COLUMNS.stream().map(field -> Map.of("field", field)).toList());
        args.put("xField", "month");
        args.put("yField", "salesAmount");
        args.put("xLabel", "月份");
        args.put("yLabel", "销售额");
        args.put("unit", "万元");
        args.put("width", 1200);
        args.put("height", 720);
        args.put("delivery", "inline");
        return args;
    }

    private static Map<String, Object> queryPayload(List<String> columns) {
        return Map.of("columns", columns, "orderBy", List.of(Map.of("field", "month", "dir", "asc")),
                "limit", 100);
    }

    private static ToolExecutionContext context(String namespace) {
        return ToolExecutionContext.builder().traceId("query-image-evidence")
                .namespace(namespace).authorization(TEST_IDENTITY).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> export(Map<String, Object> args, ToolExecutionContext context) {
        return (Map<String, Object>) tool.execute(args, context);
    }

    @SuppressWarnings("unchecked")
    private static byte[] imageBytes(Map<String, Object> result) {
        Map<String, Object> image = (Map<String, Object>) result.get("image");
        assertThat(image.get("mimeType")).isEqualTo("image/png");
        return Base64.getDecoder().decode((String) image.get("data"));
    }

    private Path evidenceDirectory() throws IOException {
        return Files.createDirectories(Path.of(System.getProperty(
                "foggy.image.evidence-dir", "target/query-image-evidence")));
    }

    private void assertSafeResult(Map<String, Object> result, byte[] png) throws Exception {
        String json = objectMapper.writeValueAsString(result);
        assertThat(json).doesNotContain(TEST_IDENTITY, EAST_ONLY_IDENTITY,
                "jdbc:sqlite", "authorization", "sourceIp", "headers");
        assertThat(new String(png, StandardCharsets.ISO_8859_1))
                .doesNotContain(TEST_IDENTITY, EAST_ONLY_IDENTITY, "jdbc:sqlite");
    }

    private static Object safeFailure(Map<String, Object> result) {
        return result.getOrDefault("error", "success");
    }

    private static void assertDeniedWithoutImage(Map<String, Object> result) {
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result).doesNotContainKeys("image");
        assertThat(result.get("error")).isNotNull();
        assertThat(result.toString()).doesNotContain(TEST_IDENTITY, EAST_ONLY_IDENTITY, "jdbc:sqlite");
    }

    private static void assertRowsEqual(List<Map<String, Object>> actual, List<Map<String, Object>> expected) {
        assertThat(actual).hasSize(expected.size());
        for (int row = 0; row < expected.size(); row++) {
            for (String column : COLUMNS) {
                Object expectedValue = expected.get(row).get(column);
                Object actualValue = actual.get(row).get(column);
                if (expectedValue instanceof Number) {
                    assertThat(new BigDecimal(String.valueOf(actualValue)))
                            .as("row %s, field %s", row, column)
                            .isEqualByComparingTo(new BigDecimal(String.valueOf(expectedValue)));
                } else {
                    assertThat(actualValue).as("row %s, field %s", row, column).isEqualTo(expectedValue);
                }
            }
        }
    }

    private List<Map<String, Object>> nativeRows(String table, String region) {
        String sql = "SELECT month, sales_amount AS salesAmount, target_amount AS targetAmount, orders"
                + " FROM " + table + (region == null ? "" : " WHERE region = ?") + " ORDER BY month";
        return region == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, region);
    }

    private void createRows(String table, double offset) {
        jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute("CREATE TABLE " + table + " (month TEXT PRIMARY KEY, sales_amount DECIMAL,"
                + " target_amount DECIMAL, orders INTEGER, region TEXT, secret_margin DECIMAL)");
        for (int index = 0; index < AMOUNTS.length; index++) {
            jdbc.update("INSERT INTO " + table + " VALUES (?, ?, ?, ?, ?, ?)",
                    "2026-%02d".formatted(index + 1), AMOUNTS[index] + offset,
                    140 + index * 15, 320 + index * 47,
                    index % 2 == 0 ? "华东" : "华南", 18.3);
        }
    }

    private void registerBundle(String namespace, String table) throws IOException {
        Path source = Files.createDirectories(tempDirectory.resolve(namespace));
        write(source.resolve("model/" + TABLE_MODEL + ".tm"), """
                export const model = {
                    name: '%s', caption: '销售图片验证', type: 'jdbc', tableName: '%s',
                    properties: [
                        { column: 'month', name: 'month', caption: '月份', type: 'STRING' },
                        { column: 'sales_amount', name: 'salesAmount', caption: '销售额（万元）', type: 'NUMBER' },
                        { column: 'target_amount', name: 'targetAmount', caption: '目标额（万元）', type: 'NUMBER' },
                        { column: 'orders', name: 'orders', caption: '订单数（笔）', type: 'INTEGER' },
                        { column: 'region', name: 'region', caption: '区域', type: 'STRING' },
                        { column: 'secret_margin', name: 'secretMargin', caption: '受限利润', type: 'NUMBER' }
                    ], measures: []
                };
                """.formatted(TABLE_MODEL, table));
        write(source.resolve("query/" + QUERY_MODEL + ".qm"), """
                const source = loadTableModel('%s');
                export const queryModel = {
                    name: '%s', caption: '销售图片验证查询', model: source,
                    modelPermissions: {
                        mode: 'resolver',
                        resolver: (context) => {
                            if (context.authorization == 'image-fixture-east-reader') {
                                return { allow: true, rowPredicates: [
                                    context.predicate.eq(source.region, '华东')
                                ] };
                            }
                            return { allow: context.authorization == 'image-fixture-reader' };
                        }
                    },
                    fieldPermissions: {
                        defaultVisible: true,
                        hiddenFields: [{ fields: ['secretMargin'] }]
                    },
                    columnGroups: [{ caption: '销售字段', items: [
                        { ref: source.month }, { ref: source.salesAmount },
                        { ref: source.targetAmount }, { ref: source.orders },
                        { ref: source.region }, { ref: source.secretMargin }
                    ] }], accesses: []
                };
                """.formatted(TABLE_MODEL, QUERY_MODEL));
        assertThat(bundlesContext.addExternalBundle(
                "query-image-" + namespace, namespace, source.toString(), false)).isTrue();
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static final class RecordingDatasetAccessor extends LocalDatasetAccessor {
        private int queryCount;
        private String lastNamespace;
        private SemanticQueryResponse lastResponse;

        private RecordingDatasetAccessor(SemanticServiceResolver resolver, DatasetProperties properties) {
            super(resolver, new McpProperties(), properties);
        }

        @Override
        public RX<SemanticQueryResponse> queryModel(String model, Map<String, Object> payload, String mode,
                                                    String traceId, String authorization, String namespace,
                                                    Map<String, Object> options) {
            queryCount++;
            lastNamespace = namespace;
            RX<SemanticQueryResponse> result = super.queryModel(
                    model, payload, mode, traceId, authorization, namespace, options);
            lastResponse = result.getData();
            return result;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DatasetMcpAutoConfiguration.class, DatasetMcpAiAutoConfiguration.class},
            excludeName = {
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
                    "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
                    "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
                    "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
                    "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
            })
    @EnableFoggyFramework(bundleName = "query-image-real-sqlite-test")
    @Import({QueryImageRenderer.class, XChartRenderer.class, AnalystMcpController.class, McpService.class,
            McpToolDispatcher.class, ToolFilterService.class, ToolConfigLoader.class, NamespaceToolPolicyService.class,
            QueryImageExportService.class, QueryImageExportTool.class, QueryModelTool.class, SemanticServiceResolverImpl.class})
    static class TestApplication {
        @Bean
        McpProperties mcpProperties() { return new McpProperties(); }

        @Bean
        RecordingDatasetAccessor httpDatasetAccessor(SemanticServiceResolver resolver, DatasetProperties properties) {
            return new RecordingDatasetAccessor(resolver, properties);
        }

        @Bean
        ChartStorageAdapter imageStorageAdapter() {
            return new ChartStorageAdapter() {
                @Override public String getType() { return "inline-fixture"; }
                @Override public String save(byte[] imageBytes, String format, String traceId) {
                    throw new UnsupportedOperationException("HTTP evidence uses inline image delivery");
                }
                @Override public boolean delete(String url) { return false; }
                @Override public boolean isAvailable() { return false; }
            };
        }

        @Bean
        DataSource dataSource() throws IOException {
            Path database = Files.createTempFile("query-image-fixture-", ".sqlite");
            database.toFile().deleteOnExit();
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("org.sqlite.JDBC");
            source.setUrl("jdbc:sqlite:" + database.toAbsolutePath());
            return source;
        }

        @Bean
        NamedDataSourceResolver namedDataSourceResolver(DataSource dataSource) {
            return new NamedDataSourceResolver() {
                @Override
                public DataSource resolve(String name) {
                    return null;
                }

                @Override
                public DataSource resolveDefault(String namespace) {
                    return List.of(PRIMARY_NS, SECONDARY_NS).contains(namespace) ? dataSource : null;
                }

                @Override
                public boolean isConfigured(String name) {
                    return false;
                }
            };
        }
    }
}
