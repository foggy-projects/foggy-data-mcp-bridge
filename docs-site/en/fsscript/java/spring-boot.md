# Spring Boot Integration

FSScript provides complete Spring Boot auto-configuration support.

## Auto Configuration

After importing the dependency, the following Beans are automatically registered:

| Bean Name | Type | Description |
|-----------|------|-------------|
| `fsscriptExpFactory` | DefaultExpFactory | Expression factory |
| `fsscriptEngineFactory` | FsscriptEngineFactory | Script engine factory |
| `fsscriptEngine` | ScriptEngine | JSR-223 script engine |
| `fileFsscriptLoader` | FileFsscriptLoader | File loader |

## Injection Usage

```java
@Service
public class MyService {

    @Resource
    private ScriptEngine fsscriptEngine;

    public Object execute(String script) {
        return fsscriptEngine.eval(script);
    }
}
```

## Access Spring Beans

Access Spring Beans in scripts via `@beanName` syntax:

```javascript
// Import Bean
import userService from '@userService';

// Call Bean method
export var user = userService.getUserById(1001);
```

## Preset Variables

```java
@Service
public class ReportService {

    @Resource
    private ScriptEngine fsscriptEngine;

    public Object generateReport(Map<String, Object> params) {
        // Preset variables
        fsscriptEngine.put("params", params);
        fsscriptEngine.put("currentUser", getCurrentUser());

        // Execute script
        return fsscriptEngine.eval("""
            export let report = {
                title: params.title,
                generatedBy: currentUser.name,
                data: @reportDataService.getData(params.dateRange)
            };
        """);
    }
}
```

## Pre-compiled Scripts

For scripts that need repeated execution, pre-compilation can improve performance:

```java
@Service
public class RuleEngine {

    @Resource
    private ScriptEngine fsscriptEngine;

    private CompiledScript compiledRule;

    @PostConstruct
    public void init() throws ScriptException {
        Compilable compilable = (Compilable) fsscriptEngine;
        compiledRule = compilable.compile("""
            export let result = amount > threshold ? 'high' : 'low';
        """);
    }

    public String evaluate(BigDecimal amount, BigDecimal threshold) {
        Bindings bindings = fsscriptEngine.createBindings();
        bindings.put("amount", amount);
        bindings.put("threshold", threshold);

        compiledRule.eval(bindings);
        return (String) bindings.get("result");
    }
}
```
