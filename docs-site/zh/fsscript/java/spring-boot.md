# Spring Boot 集成

FSScript 提供了完整的 Spring Boot 自动配置支持。

## 自动配置

引入依赖后，以下 Bean 会自动注册：

| Bean 名称 | 类型 | 说明 |
|-----------|------|------|
| `fsscriptExpFactory` | DefaultExpFactory | 表达式工厂 |
| `fsscriptEngineFactory` | FsscriptEngineFactory | 脚本引擎工厂 |
| `fsscriptEngine` | ScriptEngine | JSR-223 脚本引擎 |
| `fileFsscriptLoader` | FileFsscriptLoader | 文件加载器 |

## 注入使用

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

## 访问 Spring Bean

在脚本中通过 `@beanName` 语法访问 Spring Bean：

```javascript
// 导入 Bean
import userService from '@userService';

// 调用 Bean 方法
export var user = userService.getUserById(1001);
```

## 预设变量

```java
@Service
public class ReportService {

    @Resource
    private ScriptEngine fsscriptEngine;

    public Object generateReport(Map<String, Object> params) {
        // 预设变量
        fsscriptEngine.put("params", params);
        fsscriptEngine.put("currentUser", getCurrentUser());

        // 执行脚本
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

## 预编译脚本

对于需要重复执行的脚本，使用预编译可以提升性能：

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
