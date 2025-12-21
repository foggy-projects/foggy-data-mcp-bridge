# 快速开始

## Maven 依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

## 基本使用

### 方式一：直接加载脚本文件

```java
Fsscript script = FileFsscriptLoader.getInstance()
    .findLoadFsscript("classpath:/scripts/my-script.fsscript");

ExpEvaluator evaluator = script.newInstance(applicationContext);
script.eval(evaluator);

// 获取导出的变量
Object result = evaluator.getExportObject("result");
```

### 方式二：JSR-223 标准接口（推荐）

FSScript 实现了 JSR-223 (javax.script) 标准接口：

```java
// 非 Spring 环境
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("fsscript");

engine.put("name", "World");
engine.eval("export let greeting = `Hello ${name}!`;");
System.out.println(engine.get("greeting"));  // Hello World!
```

### 方式三：Spring 环境注入

```java
@Service
public class MyService {
    @Resource
    private ScriptEngine fsscriptEngine;

    public void execute() {
        fsscriptEngine.put("count", 10);
        fsscriptEngine.eval("export let result = count * 2;");
        System.out.println(fsscriptEngine.get("result"));  // 20
    }
}
```

### 方式四：预编译脚本

适合需要重复执行的场景：

```java
Compilable compilable = (Compilable) fsscriptEngine;
CompiledScript compiled = compilable.compile("export let sum = a + b;");

Bindings bindings = fsscriptEngine.createBindings();
bindings.put("a", 10);
bindings.put("b", 20);
compiled.eval(bindings);
System.out.println(bindings.get("sum"));  // 30
```

## 下一步

- [为什么用 FSScript](./why-fsscript) - 了解设计理念
- [变量与类型](../syntax/variables) - 学习语法基础
- [Spring Boot 集成](../java/spring-boot) - 深度集成指南
