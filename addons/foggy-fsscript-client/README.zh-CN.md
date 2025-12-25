# Foggy FSScript Client

[English](README.md)

一个基于接口代理的 FSScript 调用框架，让 Java 可以像调用普通方法一样调用 FSScript 脚本函数。

## 设计目标

FSScript Client 实现了 **Java 与 FSScript 的双向调用闭环**：

```
┌─────────────────────────────────────────────────────────────┐
│                      双向调用闭环                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Java 调用 FSScript          FSScript 调用 Java            │
│   ─────────────────          ─────────────────             │
│                                                             │
│   @FsscriptClient     ←→     import '@springBean'          │
│   interface MyClient          import 'java:...'            │
│                                                             │
│   myClient.process()  ←→     service.doSomething()         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

- **Java → FSScript**：通过 `@FsscriptClient` 注解定义接口，自动代理到脚本执行
- **FSScript → Java**：通过 `import '@bean'` 导入 Spring Bean，通过 `import 'java:...'` 导入 Java 类

### 如果你是 Spring 项目，建议通过 FSScript Client 来使用 FSScript

**1. 类型安全的接口调用**

```java
// FSScript Client - 类型安全，IDE 友好
@FsscriptClient
public interface DataProcessor {
    Map processData(String type, Map params);
}

@Resource
DataProcessor dataProcessor;
Map result = dataProcessor.processData("transform", inputParams);
```

**2. Spring 生态无缝集成**

```java
// FSScript Client - 自动扫描，自动注入
@EnableFsscriptClient(basePackages = "com.example.script")
@SpringBootApplication
public class Application { }

// 像普通 Bean 一样使用
@Service
public class MyService {
    @Resource
    private DataProcessor dataProcessor;  // 自动注入
}
```

**3. 适用场景**

| 场景 | 推荐方案 |
|------|---------|
| 配置规则、业务规则 | FSScript Client |
| 动态数据处理 | FSScript Client |
| SQL 模板（需配合 foggy-dataset-model） | FSScript Client |
| 通用脚本执行 | GraalJS |
| 高性能计算脚本 | GraalVM Polyglot |

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-fsscript-client</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

### 1. 启用 FSScript Client

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "my-application")
@EnableFsscriptClient(basePackages = "com.example.script")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 2. 定义客户端接口

```java
@FsscriptClient
public interface DataProcessorClient {

    // 方法名即脚本名：自动查找 processData.fsscript
    // 脚本直接使用 return 返回结果
    Map processData(String type, Map params);

    // 指定脚本文件和导出函数
    @FsscriptClientMethod(
        name = "utils.fsscript",
        functionName = "formatOutput"
    )
    String formatOutput(Object data);

    // 返回复杂对象，自动 JSON 转换
    @FsscriptClientMethod(functionName = "calculate")
    Summary calculateSummary(List<Item> items);
}
```

### 3. 编写 FSScript 脚本

**方式一：直接返回结果（不指定 functionName）**

`resources/foggy/templates/processData.fsscript`:

```javascript
// 导入 Spring Bean
import {getConfig} from '@configService';

let config = getConfig();

// 使用 return 返回结果
return {
    type: type,
    params: params,
    timestamp: new Date(),
    config: config
};
```

**方式二：导出多个函数（指定 functionName）**

`resources/foggy/templates/utils.fsscript`:

```javascript
// 导出多个函数
export const formatOutput = (data) => {
    return toJson(data);  // FSScript 内置函数，等效于 JSON.stringify
};

export const calculate = (items) => {
    let total = 0;
    let count = items.size();

    for (let i = 0; i < count; i++) {
        total += items.get(i).getAmount();
    }

    return {
        totalAmount: total,
        itemCount: count,
        averageAmount: count > 0 ? total / count : 0
    };
};
```

### 4. 注入使用

```java
@Service
public class MyService {

    @Resource
    private DataProcessorClient client;

    public Map process(String type, Map params) {
        return client.processData(type, params);
    }

    public Summary getSummary(List<Item> items) {
        return client.calculateSummary(items);
    }
}
```

## 核心注解

### @EnableFsscriptClient

启用 FSScript 客户端自动扫描：

```java
@EnableFsscriptClient(
    basePackages = {"com.example.script", "com.example.template"}
)
```

### @FsscriptClient

标记接口为 FSScript 客户端：

```java
@FsscriptClient
public interface MyClient {
    // ...
}
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| value | String | "" | Bean ID |
| primary | boolean | true | 是否为主 Bean |

### @FsscriptClientMethod

配置方法对应的脚本：

```java
@FsscriptClientMethod(
    name = "my-script.fsscript",
    functionName = "myFunction",
    cacheScript = true
)
Object myMethod(Object param);
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | "" | 脚本文件名，默认使用方法名 |
| functionName | String | "" | 导出函数名，为空则执行整个脚本并取 `return` 的值 |
| fsscriptType | int | AUTO_TYPE | 脚本类型：AUTO_TYPE(0)、EL_TYPE(1)、FTXT_TYPE(2) |
| cacheScript | boolean | false | 是否缓存编译后的函数 |

## 脚本返回值

FSScript Client 支持两种返回结果的方式：

### 1. 直接 return（不指定 functionName）

当 `@FsscriptClientMethod` 未指定 `functionName` 时，脚本使用 `return` 语句返回结果：

```java
// Java 接口
Map getData(String id);
```

```javascript
// getData.fsscript
// 参数 id 自动可用
return {
    id: id,
    data: fetchData(id)
};
```

### 2. 导出函数（指定 functionName）

当指定 `functionName` 时，调用脚本中对应的导出函数：

```java
// Java 接口
@FsscriptClientMethod(functionName = "process")
Map processData(String type, Map data);
```

```javascript
// processData.fsscript
export const process = (type, data) => {
    return {
        type: type,
        result: transform(data)
    };
};
```

## 参数传递

### 基本类型参数

方法参数名自动映射为脚本变量：

```java
// Java 接口
String format(String name, Integer count);
```

```javascript
// format.fsscript - 可直接使用 name 和 count 变量
return `Name: ${name}, Count: ${count}`;
```

### 对象解构

FSScript 支持对象解构语法：

```java
// Java 接口
@FsscriptClientMethod(functionName = "build")
Map build(Map params);
```

```javascript
// 解构 params 对象
export const build = ({orderId, status, userId}) => {
    return {
        orderId,
        status,
        userId,
        timestamp: new Date()
    };
};
```

### 多参数

```java
// Java 接口
@FsscriptClientMethod(functionName = "process")
Map process(String type, Map data, List items, Map options);
```

```javascript
// 所有参数都可直接访问
export const process = (type, data, items, options) => {
    return {
        type,
        dataSize: data.size(),
        itemCount: items.size(),
        enabled: options.get("enabled")
    };
};
```

## 返回值转换

FSScript Client 自动进行类型转换：

| FSScript 返回 | Java 返回类型 | 转换方式 |
|--------------|---------------|---------|
| 对象/Map | POJO | JSON 序列化 → 反序列化 |
| 对象/Map | Map | 直接返回 |
| 数组 | List | JSON 转换 |
| 基本类型 | String/Number/Boolean | 直接返回 |
| null | 任意 | null |

```java
// 返回 POJO - 自动从 Map/Object 转换
@FsscriptClientMethod(functionName = "buildOrder")
Order buildOrder(Long orderId);

// 返回 List - 自动转换
@FsscriptClientMethod(functionName = "queryItems")
List<OrderItem> queryItems(Long orderId);

// 返回 Object - 直接返回脚本结果
Object rawResult(String param);
```

## 性能优化

### 脚本缓存

对于频繁调用的函数，启用缓存可避免重复解析：

```java
@FsscriptClientMethod(
    name = "common-utils.fsscript",
    functionName = "formatAmount",
    cacheScript = true  // 缓存编译后的函数
)
String formatAmount(BigDecimal amount);
```

**注意**：启用 `cacheScript` 时必须指定 `functionName`。

### 缓存机制

- 首次调用：解析脚本 → 编译 → 缓存函数对象
- 后续调用：直接使用缓存的函数，只创建新的执行环境
- 线程安全：使用 ConcurrentHashMap + 双重检查锁定

## 线程安全

FSScript Client 设计为线程安全：

- 每次方法调用创建独立的 `ExpEvaluator` 执行环境
- 参数隔离，不同调用互不干扰
- 缓存的函数对象是无状态的，可安全共享

```java
// 可安全地在多线程环境使用
@Service
public class ConcurrentService {
    @Resource
    private DataProcessorClient client;

    public void parallelProcess(List<Long> ids) {
        ids.parallelStream()
            .map(id -> client.processById(id))  // 线程安全
            .collect(Collectors.toList());
    }
}
```

## 工作原理

```
┌─────────────────────────────────────────────────────────────────┐
│                        调用流程                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Java 调用接口方法                                            │
│     ↓                                                           │
│  2. CGLib 代理拦截 (FsscriptClientProxy)                         │
│     ↓                                                           │
│  3. 读取 @FsscriptClientMethod 注解                              │
│     ↓                                                           │
│  4. 查找脚本文件 (Bundle/BundleResource)                         │
│     ↓                                                           │
│  5. 加载并解析脚本 (FileFsscriptLoader)                          │
│     ↓                                                           │
│  6. 创建执行环境 (ExpEvaluator)                                  │
│     ↓                                                           │
│  7. 设置方法参数到环境                                           │
│     ↓                                                           │
│  8. 执行脚本或调用导出函数                                        │
│     ↓                                                           │
│  9. 转换返回值 (FsscriptReturnConverter)                         │
│     ↓                                                           │
│  10. 返回结果给调用者                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## SQL 模板支持

如果需要使用 FSScript 构建动态 SQL，请配合 `foggy-dataset` 模块使用，它提供了 `sqlExp`、`sqlInExp` 等 SQL 辅助函数。详见 [FSScript SQL 函数文档](../foggy-dataset/docs/FSScript-SQL-Functions.zh-CN.md)。

## 许可证

Apache License 2.0
