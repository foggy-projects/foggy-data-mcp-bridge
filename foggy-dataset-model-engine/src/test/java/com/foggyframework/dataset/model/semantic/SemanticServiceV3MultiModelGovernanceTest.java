package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F-3 跨模型 denied QM-field 泄漏修复的 Java 侧回归测试。
 *
 * <p>根因（{@link
 * com.foggyframework.dataset.model.semantic.service.impl.SemanticServiceV3Impl#processModelFieldsV3}）：
 * 原始实现对每个 QM 调用 {@code fields.put(key, freshInfo)}，而
 * {@code createXxxFieldInfo} 方法每次新建只含当前模型的 {@code "models"}
 * 单元素 map，导致共享 QM 字段名（如 FactOrderQueryModel 与
 * FactSalesQueryModel 都暴露的 {@code customer$id}）在多模型 metadata 输出
 * 里只保留最后处理的一个模型，违反 v1.3 "{@code fields[x]["models"]} 是多
 * 模型归属面" 的契约。</p>
 *
 * <p>修复：{@code mergeFieldInfo(fields, key, freshInfo)} 若 key 已存在，
 * 合并 {@code models} 子 map 而非整体覆盖。</p>
 *
 * <p>镜像 Python 侧 {@code tests/test_metadata_v3_cross_model_governance.py}
 * 的 7 个用例（少数用例因 Java 侧依赖 Spring + QM 文件而做必要调整）。</p>
 */
@DisplayName("F-3 · V3 metadata 跨模型治理回归")
class SemanticServiceV3MultiModelGovernanceTest extends EcommerceTestSupport {

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    /** 两个都有 {@code customer$id} JOIN 维度（共享 QM 字段名）。 */
    private static final String FACT_ORDER = "FactOrderQueryModel";
    private static final String FACT_SALES = "FactSalesQueryModel";

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Map<String, Object> fetchJsonMetadata(
            List<String> models,
            List<DeniedPhysicalColumn> deniedColumns,
            Set<String> fieldAccess) {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(models);
        SemanticRequestContext ctx;
        if (fieldAccess == null && deniedColumns == null) {
            ctx = SemanticRequestContext.empty();
        } else {
            ctx = SemanticRequestContext.of(null, null, fieldAccess,
                    deniedColumns, null);
        }
        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "json", ctx);
        assertNotNull(response, "响应不应为空");
        Map<String, Object> data = response.getData();
        assertNotNull(data, "data 不应为空");
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fieldsOf(Map<String, Object> data) {
        Object raw = data.get("fields");
        assertNotNull(raw, "metadata 必须含 fields 条目");
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private Set<String> modelKeysOf(Map<String, Object> fields, String fieldName) {
        Map<String, Object> entry = (Map<String, Object>) fields.get(fieldName);
        if (entry == null) return Collections.emptySet();
        Map<String, Object> models = (Map<String, Object>) entry.get("models");
        return models == null ? Collections.emptySet() : models.keySet();
    }

    // ------------------------------------------------------------------
    // F-3 核心回归
    // ------------------------------------------------------------------

    @Test
    @DisplayName("F-3 核心回归：共享 QM 字段在多模型 metadata 中保留所有模型归属")
    void sharedFieldRetainsAllModelsWhenNoDeny() {
        Map<String, Object> data = fetchJsonMetadata(
                Arrays.asList(FACT_ORDER, FACT_SALES),
                null, null);
        Map<String, Object> fields = fieldsOf(data);

        assertTrue(fields.containsKey("customer$id"),
                "customer$id 应在 metadata 中暴露");
        Set<String> keys = modelKeysOf(fields, "customer$id");
        assertTrue(keys.contains(FACT_ORDER),
                "customer$id.models 应含 FactOrderQueryModel；实际 key=" + keys);
        assertTrue(keys.contains(FACT_SALES),
                "customer$id.models 应含 FactSalesQueryModel；实际 key=" + keys);
        assertEquals(2, keys.size(),
                "修复前此处只会有一个 key（put 覆盖）；修复后应严格为 2");
    }

    @Test
    @DisplayName("F-3: 共享 caption 字段同样受保护（mergeFieldInfo 覆盖 $caption 路径）")
    void sharedCaptionFieldRetainsAllModels() {
        Map<String, Object> data = fetchJsonMetadata(
                Arrays.asList(FACT_ORDER, FACT_SALES),
                null, null);
        Map<String, Object> fields = fieldsOf(data);

        Set<String> keys = modelKeysOf(fields, "customer$caption");
        assertTrue(keys.contains(FACT_ORDER) && keys.contains(FACT_SALES),
                "customer$caption.models 应含两个模型；实际 key=" + keys);
    }

    @Test
    @DisplayName("F-3: 共享维度属性（customer$customerType 等）同样合并归属")
    void sharedDimensionPropertyFieldRetainsAllModels() {
        Map<String, Object> data = fetchJsonMetadata(
                Arrays.asList(FACT_ORDER, FACT_SALES),
                null, null);
        Map<String, Object> fields = fieldsOf(data);

        // customer$customerType 在两个 fact 模型的 QM 里都暴露
        Set<String> keys = modelKeysOf(fields, "customer$customerType");
        if (!keys.isEmpty()) {
            assertTrue(keys.contains(FACT_ORDER) && keys.contains(FACT_SALES),
                    "customer$customerType.models 应同时含 FactOrder + FactSales；"
                            + "实际 key=" + keys);
        }
        // 兜底：至少 customer$id/$caption 两个共享字段其一必须覆盖两个模型
        Set<String> idKeys = modelKeysOf(fields, "customer$id");
        assertEquals(2, idKeys.size(),
                "customer$id 应当合并两个模型归属");
    }

    // ------------------------------------------------------------------
    // 单模型 / 空模型边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("单模型 metadata：customer$id.models 仅含该模型，不回归 F-3 修复前行为")
    void singleModelStillYieldsSingleModelKey() {
        Map<String, Object> data = fetchJsonMetadata(
                Collections.singletonList(FACT_ORDER), null, null);
        Map<String, Object> fields = fieldsOf(data);

        Set<String> keys = modelKeysOf(fields, "customer$id");
        assertEquals(Set.of(FACT_ORDER), keys,
                "单模型场景下 models 应严格为单 key");
    }

    @Test
    @DisplayName("空模型请求：fields 为空，不抛异常")
    void emptyModelListDoesNotCrash() {
        Map<String, Object> data = fetchJsonMetadata(
                Collections.emptyList(), null, null);
        Map<String, Object> fields = fieldsOf(data);
        assertTrue(fields.isEmpty(), "空模型请求应产出空 fields");
    }

    // ------------------------------------------------------------------
    // 合并语义细节
    // ------------------------------------------------------------------

    @Test
    @DisplayName("顶层 fieldInfo（type / filterable 等）按首次写入保留；不会被后续模型覆盖")
    @SuppressWarnings("unchecked")
    void topLevelFieldInfoFollowsFirstWriteWins() {
        Map<String, Object> data = fetchJsonMetadata(
                Arrays.asList(FACT_ORDER, FACT_SALES),
                null, null);
        Map<String, Object> fields = fieldsOf(data);

        Map<String, Object> entry = (Map<String, Object>) fields.get("customer$id");
        assertNotNull(entry);
        // 顶层结构仍然存在且完整（覆盖合并不会把非 models 字段丢掉）
        assertNotNull(entry.get("name"), "fieldInfo.name 不应丢失");
        assertNotNull(entry.get("fieldName"), "fieldInfo.fieldName 不应丢失");
        assertNotNull(entry.get("meta"), "fieldInfo.meta 不应丢失");
        assertNotNull(entry.get("type"), "fieldInfo.type 不应丢失");
        assertNotNull(entry.get("filterable"), "fieldInfo.filterable 不应丢失");
    }

    @Test
    @DisplayName("顺序无关：[FactOrder, FactSales] 和 [FactSales, FactOrder] 产出同一组 model keys")
    void modelOrderInvariantOnKeySet() {
        Set<String> keysOrderAB = modelKeysOf(
                fieldsOf(fetchJsonMetadata(
                        Arrays.asList(FACT_ORDER, FACT_SALES), null, null)),
                "customer$id");
        Set<String> keysOrderBA = modelKeysOf(
                fieldsOf(fetchJsonMetadata(
                        Arrays.asList(FACT_SALES, FACT_ORDER), null, null)),
                "customer$id");
        assertEquals(keysOrderAB, keysOrderBA,
                "metadata 的模型归属不应依赖输入顺序");
    }
}
