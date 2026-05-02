package com.foggyframework.dataset.db.model.engine.pivot.rollup;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 4: NonAdditiveRollupExecutor surviving domain slice 正确性测试
 *
 * <p>核心语义：WHERE 约束始终基于完整 axisFields tuple，grainFields 只决定 GROUP BY。</p>
 *
 * <p>覆盖风险：</p>
 * <ol>
 *   <li>多字段轴 tuple 相关性 —— 完整 axisFields tuple 约束（不因 grain 粒度裁剪）</li>
 *   <li>grandTotal 空 grain —— 也必须带完整 surviving domain 约束</li>
 *   <li>null 值处理 —— 用 'is null' 而非静默丢弃</li>
 *   <li>MAX_IN_LIST_SIZE 超限 fail-closed</li>
 * </ol>
 */
@DisplayName("NonAdditiveRollupExecutor: surviving domain slice 正确性")
class NonAdditiveRollupExecutorDomainSliceTest {

    /** 帮助方法：调用 addAxisDomainSlice (package-private) */
    private List<SemanticQueryRequest.SliceItem> buildSlice(
            List<String> axisFields,
            Set<List<Object>> domain) {
        return TestableNonAdditiveRollupExecutor.exposedAddAxisDomainSlice(axisFields, domain);
    }

    // =========================================================
    // Test 1: 单字段轴 — 纯非null → 简单 IN
    // =========================================================

    @Test
    @DisplayName("单字段轴 surviving domain → 生成简单 IN 条件（无 null）")
    void testSingleFieldAxisGeneratesInSlice() {
        List<String> axisFields = List.of("category");

        Set<List<Object>> domain = new HashSet<>();
        domain.add(List.of("Electronics"));
        domain.add(List.of("Clothing"));

        List<SemanticQueryRequest.SliceItem> slices = buildSlice(axisFields, domain);

        assertEquals(1, slices.size(), "单字段轴应生成 1 个 SliceItem");
        SemanticQueryRequest.SliceItem slice = slices.get(0);
        assertEquals("category", slice.getField());
        assertEquals("in", slice.getOp());
        @SuppressWarnings("unchecked")
        List<Object> vals = (List<Object>) slice.getValue();
        assertTrue(vals.containsAll(List.of("Electronics", "Clothing")));
    }

    // =========================================================
    // Test 2: 多字段轴 (category, product) tuple domain
    // 核心断言：生成精确 OR-of-AND，不包含 cross-tuple
    // 这是 Stage 4 最核心的场景：即使 subtotal grain=[category]，
    // WHERE 也必须约束完整的 (category, product) tuple，
    // 否则 AVG(product) subtotal 会把被 TopN 截掉的 product 算回来
    // =========================================================

    @Test
    @DisplayName("多字段轴 tuple domain → 生成精确 OR-of-AND 约束，不包含 cross-tuple")
    void testMultiFieldAxisGeneratesTupleConstraintNotCrossProduct() {
        List<String> axisFields = List.of("category", "product");

        Set<List<Object>> domain = new HashSet<>();
        domain.add(Arrays.asList("Electronics", "iPhone"));
        domain.add(Arrays.asList("Clothing", "T-Shirt"));

        List<SemanticQueryRequest.SliceItem> slices = buildSlice(axisFields, domain);

        assertEquals(1, slices.size(), "多字段轴应生成 1 个 OR-group SliceItem");

        SemanticQueryRequest.SliceItem orGroup = slices.get(0);
        assertTrue(orGroup._isOrGroup(),
                "多字段轴应生成 $or group，但实际不是 OR group：" + orGroup);

        List<SemanticQueryRequest.SliceItem> andGroups = orGroup.getOr();
        assertEquals(2, andGroups.size(), "OR 组中应有 2 个 AND 子组");

        for (SemanticQueryRequest.SliceItem andGroup : andGroups) {
            assertTrue(andGroup._isAndGroup(), "OR 组的子组应是 AND group");
            List<SemanticQueryRequest.SliceItem> conditions = andGroup.getAnd();
            assertEquals(2, conditions.size(), "每个 AND 子组应包含 2 个条件（category + product）");
        }

        // 收集所有 AND 子组的 (category, product) tuple 签名
        Set<String> tupleSignatures = andGroups.stream()
                .map(andGrp -> {
                    Map<String, Object> fv = new HashMap<>();
                    for (SemanticQueryRequest.SliceItem cond : andGrp.getAnd()) {
                        fv.put(cond.getField(), cond.getValue());
                    }
                    return fv.get("category") + ":" + fv.get("product");
                })
                .collect(Collectors.toSet());

        assertTrue(tupleSignatures.contains("Electronics:iPhone"), "应包含 Electronics:iPhone");
        assertTrue(tupleSignatures.contains("Clothing:T-Shirt"), "应包含 Clothing:T-Shirt");

        // 关键：不能包含 cross-product
        assertFalse(tupleSignatures.contains("Electronics:T-Shirt"),
                "不应包含 cross-product tuple Electronics:T-Shirt");
        assertFalse(tupleSignatures.contains("Clothing:iPhone"),
                "不应包含 cross-product tuple Clothing:iPhone");
    }

    // =========================================================
    // Test 3: subtotal grain 粒度比 domain 粗时（反转自之前的错误测试）
    // rows = [category, product], TopN surviving = {(Electronics, iPhone), (Clothing, T-Shirt)}
    // subtotal grain = [category]（GROUP BY 只按 category）
    //
    // 正确行为：WHERE 仍约束完整 (category, product) tuple，
    // 即使 grain 只有 [category]，product 也必须参与 WHERE 过滤。
    // 这样 AVG subtotal 只覆盖 surviving 的 product，不把 TopN 截掉的 product 算回来。
    //
    // 前版错误测试断言"只生成 category IN 过滤"——这是错误语义，已删除。
    // =========================================================

    @Test
    @DisplayName("subtotal grain 比 domain 粗时 → WHERE 仍约束完整 axisFields tuple，product 参与过滤")
    void testSubtotalGrainCoarserThanDomainStillAppliesFullTupleConstraint() {
        // axisFields = 完整行轴字段，来自 surviving domain（与 grain 无关）
        List<String> rowAxisFields = List.of("category", "product");

        Set<List<Object>> rowDomain = new HashSet<>();
        rowDomain.add(Arrays.asList("Electronics", "iPhone"));
        rowDomain.add(Arrays.asList("Clothing", "T-Shirt"));

        // addAxisDomainSlice 接受完整 axisFields，grain 字段只决定 GROUP BY（不影响 WHERE）
        List<SemanticQueryRequest.SliceItem> slices = buildSlice(rowAxisFields, rowDomain);

        // 修正后：应生成 OR-of-AND (category='Electronics' AND product='iPhone') OR (...)
        // product 必须参与 WHERE 约束
        assertEquals(1, slices.size(), "应生成 1 个 OR-group SliceItem");
        SemanticQueryRequest.SliceItem orGroup = slices.get(0);
        assertTrue(orGroup._isOrGroup(), "应是 OR-group");

        // 验证每个 AND 子组包含 product 字段约束
        boolean anyAndGroupHasProduct = orGroup.getOr().stream()
                .anyMatch(andGrp -> andGrp._isAndGroup() &&
                        andGrp.getAnd().stream().anyMatch(c -> "product".equals(c.getField())));

        assertTrue(anyAndGroupHasProduct,
                "WHERE 约束必须包含 product 字段，确保不把 TopN 截掉的 product 算回 subtotal");
    }

    // =========================================================
    // Test 4: grandTotal 场景 (空 axisFields → 不生成过滤，正确行为)
    // grandTotal 的 axisFields 是 colFields（列轴字段），rowFields 被 rollup 掉了
    // 注意：addAxisDomainSlice 分别被调用两次：一次 rowFields，一次 colFields
    // 当 axisFields 为空时（例如无列轴），不生成任何过滤
    // =========================================================

    @Test
    @DisplayName("空 axisFields → 不生成任何过滤（无列轴时正常）")
    void testEmptyAxisFieldsGeneratesNoSlice() {
        List<SemanticQueryRequest.SliceItem> slices = buildSlice(Collections.emptyList(), Collections.emptySet());
        assertTrue(slices.isEmpty(), "空 axisFields 不应生成过滤条件");
    }

    // =========================================================
    // Test 5: grandTotal 场景 —— 单字段列轴 surviving domain 仍然约束辅助查询
    // grandTotal grain 只保留 colFields（例如 month），rowFields 全部 rollup
    // 但调用 addAxisDomainSlice(sliceItems, colFields, survivingColDomain) 时
    // colFields=[month], domain={(Jan),(Feb)} → 正确生成 month IN (Jan, Feb)
    // =========================================================

    @Test
    @DisplayName("grandTotal: 列轴 surviving domain 依然约束辅助查询")
    void testGrandTotalColDomainApplied() {
        List<String> colFields = List.of("month");

        Set<List<Object>> colDomain = new HashSet<>();
        colDomain.add(List.of("Jan"));
        colDomain.add(List.of("Feb"));

        List<SemanticQueryRequest.SliceItem> slices = buildSlice(colFields, colDomain);

        assertEquals(1, slices.size(), "应生成 1 个 SliceItem");
        SemanticQueryRequest.SliceItem slice = slices.get(0);
        assertEquals("month", slice.getField());
        assertEquals("in", slice.getOp());
        @SuppressWarnings("unchecked")
        List<Object> vals = (List<Object>) slice.getValue();
        assertTrue(vals.containsAll(List.of("Jan", "Feb")));
    }

    // =========================================================
    // Test 6: 单字段轴 null 值 → 生成 IS NULL 条件
    // =========================================================

    @Test
    @DisplayName("单字段轴全 null domain → 生成 is null 条件")
    void testSingleFieldNullDomainGeneratesIsNull() {
        List<String> axisFields = List.of("category");

        Set<List<Object>> domain = new HashSet<>();
        domain.add(Collections.singletonList(null));

        List<SemanticQueryRequest.SliceItem> slices = buildSlice(axisFields, domain);

        assertEquals(1, slices.size(), "全 null domain 应生成 1 个 IS NULL SliceItem");
        SemanticQueryRequest.SliceItem slice = slices.get(0);
        assertEquals("category", slice.getField());
        assertEquals("is null", slice.getOp());
        assertNull(slice.getValue());
    }

    @Test
    @DisplayName("单字段轴混合 null 和非null → 生成 OR(IN, IS NULL)")
    void testSingleFieldMixedNullDomainGeneratesOrGroup() {
        List<String> axisFields = List.of("category");

        Set<List<Object>> domain = new HashSet<>();
        domain.add(List.of("Electronics"));
        domain.add(Collections.singletonList(null));

        List<SemanticQueryRequest.SliceItem> slices = buildSlice(axisFields, domain);

        assertEquals(1, slices.size(), "混合 null domain 应生成 1 个 OR-group SliceItem");
        SemanticQueryRequest.SliceItem orGroup = slices.get(0);
        assertTrue(orGroup._isOrGroup(), "应是 OR-group");

        boolean hasIn = orGroup.getOr().stream().anyMatch(c -> "in".equals(c.getOp()));
        boolean hasIsNull = orGroup.getOr().stream().anyMatch(c -> "is null".equals(c.getOp()));
        assertTrue(hasIn, "OR 组应包含 IN 条件");
        assertTrue(hasIsNull, "OR 组应包含 IS NULL 条件");
    }

    // =========================================================
    // Test 7: 多字段轴含 null → IS NULL 参与 AND 子组
    // =========================================================

    @Test
    @DisplayName("多字段轴含 null tuple → AND 子组中包含 is null 条件")
    void testMultiFieldWithNullGeneratesIsNullInAndGroup() {
        List<String> axisFields = List.of("category", "product");

        Set<List<Object>> domain = new HashSet<>();
        domain.add(Arrays.asList("Electronics", null)); // product 为 null

        List<SemanticQueryRequest.SliceItem> slices = buildSlice(axisFields, domain);

        assertEquals(1, slices.size());
        SemanticQueryRequest.SliceItem orGroup = slices.get(0);
        assertTrue(orGroup._isOrGroup());

        // 应有一个 AND 子组，包含 category='Electronics' AND product IS NULL
        List<SemanticQueryRequest.SliceItem> andGroup = orGroup.getOr().get(0).getAnd();
        assertEquals(2, andGroup.size());

        boolean hasCategoryEquals = andGroup.stream()
                .anyMatch(c -> "category".equals(c.getField()) && "=".equals(c.getOp()));
        boolean hasProductIsNull = andGroup.stream()
                .anyMatch(c -> "product".equals(c.getField()) && "is null".equals(c.getOp()));

        assertTrue(hasCategoryEquals, "AND 子组应包含 category='Electronics'");
        assertTrue(hasProductIsNull, "AND 子组应包含 product IS NULL，不能因为 null 而跳过该 tuple");
    }

    // =========================================================
    // Test 8: domain 超限 → fail-closed
    // =========================================================

    @Test
    @DisplayName("domain 超过 MAX_IN_LIST_SIZE → fail-closed，不静默跳过")
    void testDomainExceedsMaxInListSizeShouldFailClosed() {
        List<String> axisFields = List.of("product");

        Set<List<Object>> hugedomain = new HashSet<>();
        for (int i = 0; i < 501; i++) {
            hugedomain.add(List.of("product_" + i));
        }

        assertThrows(NonAdditiveRollupDomainTooLargeException.class, () -> {
            buildSlice(axisFields, hugedomain);
        }, "domain 超限时应 fail-closed，抛出 NonAdditiveRollupDomainTooLargeException");
    }

    @Test
    @DisplayName("多字段轴 tuple domain 超限 → fail-closed")
    void testMultiFieldTupleDomainExceedsLimitShouldFailClosed() {
        List<String> axisFields = List.of("category", "product");

        Set<List<Object>> hugeDomain = new HashSet<>();
        for (int i = 0; i < 501; i++) {
            hugeDomain.add(Arrays.asList("cat_" + (i % 10), "product_" + i));
        }

        assertThrows(NonAdditiveRollupDomainTooLargeException.class, () -> {
            buildSlice(axisFields, hugeDomain);
        }, "多字段 tuple domain 超限时应 fail-closed");
    }

    // =========================================================
    // Test 9: null 或空 domain → 不生成过滤
    // =========================================================

    @Test
    @DisplayName("null 或空 domain → 不生成过滤条件")
    void testNullOrEmptyDomainGeneratesNoSlice() {
        List<String> axisFields = List.of("category");

        List<SemanticQueryRequest.SliceItem> slicesNull = buildSlice(axisFields, null);
        assertTrue(slicesNull.isEmpty(), "null domain 不应生成过滤条件");

        List<SemanticQueryRequest.SliceItem> slicesEmpty = buildSlice(axisFields, Collections.emptySet());
        assertTrue(slicesEmpty.isEmpty(), "空 domain 不应生成过滤条件");
    }
}
