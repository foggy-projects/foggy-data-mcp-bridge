package com.foggyframework.dataset.db.model.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 维度路径 - 统一的路径表示和格式转换
 *
 * <p>内部使用 DOT 格式存储路径段，如 ["product", "category"]。
 * 提供方法转换为不同的输出格式：
 * <ul>
 *   <li>DOT 格式：{@code product.category} - 用于 QM ref 语法和内部存储</li>
 *   <li>UNDERSCORE 格式：{@code product_category} - 用于列别名（避免前端 JS 处理带 . 的属性名）</li>
 * </ul>
 *
 * <p>列名格式示例：
 * <ul>
 *   <li>{@code product.category$categoryId} - DOT 格式带列名</li>
 *   <li>{@code product_category$categoryId} - UNDERSCORE 格式带列名</li>
 * </ul>
 *
 * @author Foggy Framework
 * @since 2.0
 */
public class DimensionPath {

    private static final String DOT_SEPARATOR = ".";
    private static final String UNDERSCORE_SEPARATOR = "_";
    private static final String COLUMN_SEPARATOR = "$";

    /**
     * 路径段
     */
    private final List<String> segments;

    /**
     * 列名（可选）
     */
    private final String columnName;

    // ==================== 构造方法 ====================

    private DimensionPath(List<String> segments, String columnName) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.columnName = columnName;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建空路径
     */
    public static DimensionPath empty() {
        return new DimensionPath(Collections.emptyList(), null);
    }

    /**
     * 从路径段创建
     *
     * @param segments 路径段
     * @return DimensionPath
     */
    public static DimensionPath of(String... segments) {
        return new DimensionPath(Arrays.asList(segments), null);
    }

    /**
     * 从路径段列表创建
     *
     * @param segments 路径段列表
     * @return DimensionPath
     */
    public static DimensionPath of(List<String> segments) {
        return new DimensionPath(segments, null);
    }

    /**
     * 从路径段和列名创建
     *
     * @param segments   路径段
     * @param columnName 列名
     * @return DimensionPath
     */
    public static DimensionPath of(List<String> segments, String columnName) {
        return new DimensionPath(segments, columnName);
    }

    /**
     * 解析 DOT 格式路径
     *
     * <p>示例输入：
     * <ul>
     *   <li>{@code "product"} → segments=["product"]</li>
     *   <li>{@code "product.category"} → segments=["product", "category"]</li>
     *   <li>{@code "product.category$categoryId"} → segments=["product", "category"], columnName="categoryId"</li>
     * </ul>
     *
     * @param dotPath DOT 格式路径
     * @return DimensionPath
     */
    public static DimensionPath parse(String dotPath) {
        if (dotPath == null || dotPath.isEmpty()) {
            return empty();
        }

        String path = dotPath;
        String columnName = null;

        // 分离列名
        int dollarIndex = dotPath.indexOf(COLUMN_SEPARATOR);
        if (dollarIndex >= 0) {
            path = dotPath.substring(0, dollarIndex);
            columnName = dotPath.substring(dollarIndex + 1);
        }

        // 分割路径段
        List<String> segments = path.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(path.split("\\" + DOT_SEPARATOR));

        return new DimensionPath(segments, columnName);
    }

    /**
     * 解析 UNDERSCORE 格式路径
     *
     * <p>示例输入：
     * <ul>
     *   <li>{@code "product"} → segments=["product"]</li>
     *   <li>{@code "product_category"} → segments=["product", "category"]</li>
     *   <li>{@code "product_category$categoryId"} → segments=["product", "category"], columnName="categoryId"</li>
     * </ul>
     *
     * @param underscorePath UNDERSCORE 格式路径
     * @return DimensionPath
     */
    public static DimensionPath parseUnderscore(String underscorePath) {
        if (underscorePath == null || underscorePath.isEmpty()) {
            return empty();
        }

        String path = underscorePath;
        String columnName = null;

        // 分离列名
        int dollarIndex = underscorePath.indexOf(COLUMN_SEPARATOR);
        if (dollarIndex >= 0) {
            path = underscorePath.substring(0, dollarIndex);
            columnName = underscorePath.substring(dollarIndex + 1);
        }

        // 分割路径段
        List<String> segments = path.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(path.split(UNDERSCORE_SEPARATOR));

        return new DimensionPath(segments, columnName);
    }

    // ==================== 格式输出方法 ====================

    /**
     * 转换为 DOT 格式路径（不含列名）
     *
     * @return DOT 格式，如 "product.category"
     */
    public String toDotFormat() {
        return String.join(DOT_SEPARATOR, segments);
    }

    /**
     * 转换为 UNDERSCORE 格式路径（不含列名）
     *
     * @return UNDERSCORE 格式，如 "product_category"
     */
    public String toUnderscoreFormat() {
        return String.join(UNDERSCORE_SEPARATOR, segments);
    }

    /**
     * 转换为 DOT 格式的列引用（含列名）
     *
     * @return 如 "product.category$categoryId"，如果无列名则同 toDotFormat()
     */
    public String toColumnRef() {
        String path = toDotFormat();
        if (columnName != null && !columnName.isEmpty()) {
            return path + COLUMN_SEPARATOR + columnName;
        }
        return path;
    }

    /**
     * 转换为 UNDERSCORE 格式的列别名（含列名）
     *
     * @return 如 "product_category$categoryId"，如果无列名则同 toUnderscoreFormat()
     */
    public String toColumnAlias() {
        String path = toUnderscoreFormat();
        if (columnName != null && !columnName.isEmpty()) {
            return path + COLUMN_SEPARATOR + columnName;
        }
        return path;
    }

    // ==================== 路径操作方法 ====================

    /**
     * 追加路径段
     *
     * @param segment 新路径段
     * @return 新的 DimensionPath（不含列名）
     */
    public DimensionPath append(String segment) {
        List<String> newSegments = new ArrayList<>(segments);
        newSegments.add(segment);
        return new DimensionPath(newSegments, null);
    }

    /**
     * 追加路径段和列名
     *
     * @param segment    新路径段
     * @param columnName 列名
     * @return 新的 DimensionPath
     */
    public DimensionPath append(String segment, String columnName) {
        List<String> newSegments = new ArrayList<>(segments);
        newSegments.add(segment);
        return new DimensionPath(newSegments, columnName);
    }

    /**
     * 设置列名
     *
     * @param columnName 列名
     * @return 新的 DimensionPath
     */
    public DimensionPath withColumnName(String columnName) {
        return new DimensionPath(segments, columnName);
    }

    /**
     * 获取父路径（去掉最后一个段）
     *
     * @return 父路径，如果当前路径为空或只有一个段则返回空路径
     */
    public DimensionPath parent() {
        if (segments.size() <= 1) {
            return empty();
        }
        return new DimensionPath(segments.subList(0, segments.size() - 1), null);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取路径段列表
     *
     * @return 不可变的路径段列表
     */
    public List<String> getSegments() {
        return segments;
    }

    /**
     * 获取列名
     *
     * @return 列名，可能为 null
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * 判断是否为空路径
     *
     * @return 如果没有路径段则返回 true
     */
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /**
     * 获取路径深度
     *
     * @return 路径段数量
     */
    public int depth() {
        return segments.size();
    }

    /**
     * 获取第一个路径段
     *
     * @return 第一个段，如果为空路径则返回 null
     */
    public String first() {
        return segments.isEmpty() ? null : segments.get(0);
    }

    /**
     * 获取最后一个路径段
     *
     * @return 最后一个段，如果为空路径则返回 null
     */
    public String last() {
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }

    /**
     * 判断是否有列名
     *
     * @return 如果有列名返回 true
     */
    public boolean hasColumnName() {
        return columnName != null && !columnName.isEmpty();
    }

    /**
     * 判断是否为嵌套路径（深度 > 1）
     *
     * @return 如果深度 > 1 返回 true
     */
    public boolean isNested() {
        return segments.size() > 1;
    }

    // ==================== Object 方法 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DimensionPath that = (DimensionPath) o;
        return Objects.equals(segments, that.segments) &&
                Objects.equals(columnName, that.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segments, columnName);
    }

    @Override
    public String toString() {
        return toColumnRef();
    }
}
