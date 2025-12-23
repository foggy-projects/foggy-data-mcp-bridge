# 快速开始

## 环境要求

- **JDK**: 17 及以上版本
- **Spring Boot**: 3.x

## Maven 依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

## Spring Boot 配置

在 Spring Boot 项目中，需要添加 `@EnableFoggyFramework` 注解来启用 Foggy 框架。

**方式一：在配置类上添加（推荐用于多模块项目）**

```java
@Configuration
@EnableFoggyFramework(bundleName = "your-bundle-name")
public class FoggyConfiguration {

}
```

**方式二：在启动类上添加（适用于单模块项目）**

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "your-bundle-name")
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## 脚本文件存放位置

建议将 FSScript 脚本文件统一存放在以下目录：

```
src/main/resources/foggy/templates/
```

例如：
```
src/main/resources/
└── foggy/
    └── templates/
        ├── my-script.fsscript
        └── another-script.fsscript
```

## 基本使用

### 方式一：直接加载脚本文件

```java
Fsscript script = FileFsscriptLoader.getInstance()
    .findLoadFsscript("classpath:/foggy/templates/my-script.fsscript");

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
