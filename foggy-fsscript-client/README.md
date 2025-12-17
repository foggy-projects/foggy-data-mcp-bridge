# Foggy FSScript Client

[中文文档](README.zh-CN.md)

An interface proxy-based FSScript invocation framework that enables Java to call FSScript functions like regular methods.

## Design Goals

FSScript Client implements **bidirectional call loop between Java and FSScript**:

```
┌─────────────────────────────────────────────────────────────┐
│                   Bidirectional Call Loop                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Java calls FSScript        FSScript calls Java            │
│   ─────────────────          ─────────────────             │
│                                                             │
│   @FsscriptClient     ←→     import '@springBean'          │
│   interface MyClient          import 'java:...'            │
│                                                             │
│   myClient.process()  ←→     service.doSomething()         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

- **Java → FSScript**: Define interfaces with `@FsscriptClient` annotation, auto-proxy to script execution
- **FSScript → Java**: Import Spring Beans via `import '@bean'`, import Java classes via `import 'java:...'`

### If you're using Spring, FSScript Client is recommended

**1. Type-safe interface invocation**

```java
// FSScript Client - type-safe, IDE friendly
@FsscriptClient
public interface DataProcessor {
    Map processData(String type, Map params);
}

@Resource
DataProcessor dataProcessor;
Map result = dataProcessor.processData("transform", inputParams);
```

**2. Seamless Spring ecosystem integration**

```java
// FSScript Client - auto-scan, auto-injection
@EnableFsscriptClient(basePackages = "com.example.script")
@SpringBootApplication
public class Application { }

// Use like regular Bean
@Service
public class MyService {
    @Resource
    private DataProcessor dataProcessor;  // Auto-injected
}
```

**3. Use case recommendations**

| Scenario | Recommended Approach |
|----------|---------------------|
| Configuration rules, business rules | FSScript Client |
| Dynamic data processing | FSScript Client |
| SQL templates (with foggy-dataset-model) | FSScript Client |
| General scripting | GraalJS |
| High-performance computation scripts | GraalVM Polyglot |

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-fsscript-client</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

### 1. Enable FSScript Client

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

### 2. Define Client Interface

```java
@FsscriptClient
public interface DataProcessorClient {

    // Method name is script name: auto-finds processData.fsscript
    // Script uses return to return result
    Map processData(String type, Map params);

    // Specify script file and export function
    @FsscriptClientMethod(
        name = "utils.fsscript",
        functionName = "formatOutput"
    )
    String formatOutput(Object data);

    // Return complex objects, auto JSON conversion
    @FsscriptClientMethod(functionName = "calculate")
    Summary calculateSummary(List<Item> items);
}
```

### 3. Write FSScript

**Option 1: Direct return (no functionName specified)**

`resources/foggy/templates/processData.fsscript`:

```javascript
// Import Spring Bean
import {getConfig} from '@configService';

let config = getConfig();

// Use return to return result
return {
    type: type,
    params: params,
    timestamp: new Date(),
    config: config
};
```

**Option 2: Export multiple functions (functionName specified)**

`resources/foggy/templates/utils.fsscript`:

```javascript
// Export multiple functions
export const formatOutput = (data) => {
    return toJson(data);  // FSScript built-in function, equivalent to JSON.stringify
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

### 4. Inject and Use

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

## Core Annotations

### @EnableFsscriptClient

Enable FSScript client auto-scanning:

```java
@EnableFsscriptClient(
    basePackages = {"com.example.script", "com.example.template"}
)
```

### @FsscriptClient

Mark interface as FSScript client:

```java
@FsscriptClient
public interface MyClient {
    // ...
}
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| value | String | "" | Bean ID |
| primary | boolean | true | Whether primary Bean |

### @FsscriptClientMethod

Configure method's corresponding script:

```java
@FsscriptClientMethod(
    name = "my-script.fsscript",
    functionName = "myFunction",
    cacheScript = true
)
Object myMethod(Object param);
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| name | String | "" | Script filename, defaults to method name |
| functionName | String | "" | Export function name, empty executes entire script and takes `return` value |
| fsscriptType | int | AUTO_TYPE | Script type: AUTO_TYPE(0), EL_TYPE(1), FTXT_TYPE(2) |
| cacheScript | boolean | false | Whether to cache compiled function |

## Script Return Values

FSScript Client supports two ways to return results:

### 1. Direct return (no functionName)

When `@FsscriptClientMethod` doesn't specify `functionName`, script uses `return` statement:

```java
// Java interface
Map getData(String id);
```

```javascript
// getData.fsscript
// Parameter id is auto-available
return {
    id: id,
    data: fetchData(id)
};
```

### 2. Export function (functionName specified)

When `functionName` is specified, calls the corresponding exported function:

```java
// Java interface
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

## Parameter Passing

### Basic Type Parameters

Method parameter names auto-map to script variables:

```java
// Java interface
String format(String name, Integer count);
```

```javascript
// format.fsscript - can directly use name and count variables
return `Name: ${name}, Count: ${count}`;
```

### Object Destructuring

FSScript supports object destructuring syntax:

```java
// Java interface
@FsscriptClientMethod(functionName = "build")
Map build(Map params);
```

```javascript
// Destructure params object
export const build = ({orderId, status, userId}) => {
    return {
        orderId,
        status,
        userId,
        timestamp: new Date()
    };
};
```

### Multiple Parameters

```java
// Java interface
@FsscriptClientMethod(functionName = "process")
Map process(String type, Map data, List items, Map options);
```

```javascript
// All parameters are directly accessible
export const process = (type, data, items, options) => {
    return {
        type,
        dataSize: data.size(),
        itemCount: items.size(),
        enabled: options.get("enabled")
    };
};
```

## Return Value Conversion

FSScript Client auto-converts types:

| FSScript Return | Java Return Type | Conversion |
|-----------------|------------------|------------|
| Object/Map | POJO | JSON serialize → deserialize |
| Object/Map | Map | Direct return |
| Array | List | JSON convert |
| Primitive | String/Number/Boolean | Direct return |
| null | Any | null |

```java
// Return POJO - auto-converts from Map/Object
@FsscriptClientMethod(functionName = "buildOrder")
Order buildOrder(Long orderId);

// Return List - auto-converts
@FsscriptClientMethod(functionName = "queryItems")
List<OrderItem> queryItems(Long orderId);

// Return Object - directly returns script result
Object rawResult(String param);
```

## Performance Optimization

### Script Caching

For frequently called functions, enable caching to avoid repeated parsing:

```java
@FsscriptClientMethod(
    name = "common-utils.fsscript",
    functionName = "formatAmount",
    cacheScript = true  // Cache compiled function
)
String formatAmount(BigDecimal amount);
```

**Note**: `functionName` must be specified when enabling `cacheScript`.

### Caching Mechanism

- First call: Parse script → Compile → Cache function object
- Subsequent calls: Use cached function, only create new execution environment
- Thread-safe: Uses ConcurrentHashMap + double-checked locking

## Thread Safety

FSScript Client is designed to be thread-safe:

- Each method call creates independent `ExpEvaluator` execution environment
- Parameter isolation, different calls don't interfere
- Cached function objects are stateless, safe to share

```java
// Safe to use in multi-threaded environment
@Service
public class ConcurrentService {
    @Resource
    private DataProcessorClient client;

    public void parallelProcess(List<Long> ids) {
        ids.parallelStream()
            .map(id -> client.processById(id))  // Thread-safe
            .collect(Collectors.toList());
    }
}
```

## How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│                         Call Flow                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Java calls interface method                                  │
│     ↓                                                           │
│  2. CGLib proxy intercepts (FsscriptClientProxy)                 │
│     ↓                                                           │
│  3. Read @FsscriptClientMethod annotation                        │
│     ↓                                                           │
│  4. Find script file (Bundle/BundleResource)                     │
│     ↓                                                           │
│  5. Load and parse script (FileFsscriptLoader)                   │
│     ↓                                                           │
│  6. Create execution environment (ExpEvaluator)                  │
│     ↓                                                           │
│  7. Set method parameters to environment                         │
│     ↓                                                           │
│  8. Execute script or call exported function                     │
│     ↓                                                           │
│  9. Convert return value (FsscriptReturnConverter)               │
│     ↓                                                           │
│  10. Return result to caller                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## SQL Template Support

To use FSScript for building dynamic SQL, use with `foggy-dataset` module which provides `sqlExp`, `sqlInExp` and other SQL helper functions. See [FSScript SQL Functions Documentation](../foggy-dataset/docs/FSScript-SQL-Functions.md).

## License

Apache License 2.0
