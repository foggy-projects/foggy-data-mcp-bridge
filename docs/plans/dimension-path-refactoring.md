# DimensionPath 重构计划

## 设计决策

基于用户确认的设计方向：

1. **标准格式**: 使用 DOT 格式 (`product.category$id`) 作为内部标准
   - 理由：QM 中可以使用 alias 重新指定列名，TM 中使用标准 dot 格式即可
2. **向后兼容**: 直接删除旧方法，不保留 @Deprecated
3. **路径索引**: 在 TableModel 中添加 `Map<String, DbDimension>` 索引

## 重构步骤

### 第一步：创建 DimensionPath 类

创建文件: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/path/DimensionPath.java`

```java
package com.foggyframework.dataset.db.model.path;

/**
 * 维度路径 - 统一的路径表示和格式转换
 *
 * 内部使用 DOT 格式存储路径，如 "product.category"
 * 提供转换方法生成 UNDERSCORE 格式用于列别名
 */
public class DimensionPath {

    private final List<String> segments;  // 路径段
    private final String columnName;       // 列名（可选）

    // 构造方法
    public static DimensionPath of(String... segments);
    public static DimensionPath parse(String dotPath);  // 解析 "product.category"
    public static DimensionPath parseUnderscore(String underscorePath);  // 解析 "product_category"

    // 格式输出
    public String toDotFormat();        // "product.category"
    public String toUnderscoreFormat(); // "product_category"

    // 带列名输出
    public String toColumnRef();        // "product.category$columnName"
    public String toColumnAlias();      // "product_category$columnName"

    // 路径操作
    public DimensionPath append(String segment);
    public DimensionPath parent();
    public boolean isEmpty();
    public int depth();
}
```

### 第二步：重构 DbDimension 接口

修改文件: `DbDimension.java`

```java
// 添加新方法
DimensionPath getDimensionPath();

// 删除旧方法（直接删除，不保留）
// - getFullPath()
// - getFullPathForAlias()
```

### 第三步：重构 DbDimensionSupport

修改文件: `DbDimensionSupport.java`

```java
// 添加字段
private DimensionPath dimensionPath;

// 实现 getDimensionPath()
@Override
public DimensionPath getDimensionPath() {
    if (dimensionPath == null) {
        dimensionPath = buildDimensionPath();
    }
    return dimensionPath;
}

private DimensionPath buildDimensionPath() {
    if (!isNestedDimension()) {
        return DimensionPath.of(name);
    }
    return getParentDimension().getDimensionPath().append(name);
}
```

### 第四步：重构 ColumnRef 和 DimensionProxy

修改文件: `ColumnRef.java`, `DimensionProxy.java`

```java
// 替换 List<String> dimensionPath 为 DimensionPath
private DimensionPath dimensionPath;

// 简化方法
public String getFullRef() {
    return dimensionPath.toColumnRef();
}

public String getAliasRef() {
    return dimensionPath.toColumnAlias();
}
```

### 第五步：重构 TableModelSupport

修改文件: `TableModelSupport.java`

```java
// 添加索引 Map
Map<String, DbDimension> pathToDimension = new HashMap<>();

// 在 init() 中构建索引
private void buildDimensionIndex() {
    for (DbDimension dimension : dimensions) {
        String path = dimension.getDimensionPath().toDotFormat();
        pathToDimension.put(path, dimension);
    }
}

// 简化查找方法
@Override
public DbDimension findJdbcDimensionByName(String name) {
    // 优先精确匹配路径
    DbDimension dim = pathToDimension.get(name);
    if (dim != null) return dim;

    // 如果是下划线格式，转换后查找
    if (name.contains("_") && !name.contains(".")) {
        String dotPath = DimensionPath.parseUnderscore(name).toDotFormat();
        dim = pathToDimension.get(dotPath);
        if (dim != null) return dim;
    }

    // 简单名称匹配（兼容旧代码）
    for (DbDimension d : dimensions) {
        if (d.getName().equals(name)) return d;
    }
    return null;
}
```

### 第六步：简化 QueryModelSupport 列注册

修改文件: `QueryModelSupport.java`

```java
// 简化 registerNestedDimensionAliases 方法
// 只注册一次，使用 DOT 格式作为主键
private void registerColumn(String dotPath, JdbcQueryColumn column) {
    nameToJdbcQueryColumn.put(dotPath, column);
}

// 查找时支持格式转换
public JdbcQueryColumn findColumn(String ref) {
    // 优先 DOT 格式查找
    JdbcQueryColumn col = nameToJdbcQueryColumn.get(ref);
    if (col != null) return col;

    // 下划线格式转换
    if (ref.contains("_")) {
        String dotRef = DimensionPath.parseUnderscore(ref).toColumnRef();
        return nameToJdbcQueryColumn.get(dotRef);
    }
    return null;
}
```

### 第七步：更新 SelectColumnDef

修改文件: `SelectColumnDef.java`

```java
// 简化方法，统一使用 DimensionPath
public String getRefAsString() {
    if (ref instanceof ColumnRef columnRef) {
        return columnRef.getDimensionPath().toColumnAlias();
    }
    // ...
}

public String getRefForLookup() {
    if (ref instanceof ColumnRef columnRef) {
        return columnRef.getDimensionPath().toColumnRef();
    }
    // ...
}
```

## 文件修改清单

| 文件 | 操作 |
|------|------|
| `path/DimensionPath.java` | 新建 |
| `spi/DbDimension.java` | 修改：添加 getDimensionPath()，删除旧方法 |
| `impl/dimension/DbDimensionSupport.java` | 修改：实现 getDimensionPath() |
| `proxy/ColumnRef.java` | 修改：使用 DimensionPath |
| `proxy/DimensionProxy.java` | 修改：使用 DimensionPath |
| `impl/model/TableModelSupport.java` | 修改：添加索引 Map |
| `engine/query_model/QueryModelSupport.java` | 修改：简化列注册 |
| `def/query/SelectColumnDef.java` | 修改：简化格式转换 |

## 测试验证

- 运行 `NestedDimensionTest` 验证嵌套维度功能
- 运行 `FactSalesNestedDimQueryModel` 相关测试
- 验证 DOT 格式和 UNDERSCORE 格式的相互转换

## 预期效果

1. **统一路径表示**: DimensionPath 类封装所有路径逻辑
2. **单一注册**: 每个列只注册一次（DOT 格式）
3. **O(1) 查找**: 通过索引 Map 直接定位维度
4. **代码简化**: 删除冗余的格式转换方法
