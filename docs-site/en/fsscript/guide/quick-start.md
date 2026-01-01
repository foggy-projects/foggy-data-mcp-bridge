# Quick Start

## Requirements

- **JDK**: 17 or above
- **Spring Boot**: 3.x

## Maven Dependency

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>8.0.1-beta</version>
</dependency>
```

## Spring Boot Configuration

In a Spring Boot project, you need to add the `@EnableFoggyFramework` annotation to enable the Foggy framework.

**Option 1: Add to configuration class (recommended for multi-module projects)**

```java
@Configuration
@EnableFoggyFramework(bundleName = "your-bundle-name")
public class FoggyConfiguration {

}
```

**Option 2: Add to main application class (for single-module projects)**

```java
@SpringBootApplication
@EnableFoggyFramework(bundleName = "your-bundle-name")
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## Script File Location

It's recommended to store FSScript files in the following directory:

```
src/main/resources/foggy/templates/
```

For example:
```
src/main/resources/
└── foggy/
    └── templates/
        ├── my-script.fsscript
        └── another-script.fsscript
```

## Basic Usage

### Option 1: Load Script Files Directly

```java
@Service
public class BasicUserFsscript implements InitializingBean {

    @Resource
    ApplicationContext applicationContext;
    @Resource
    FileFsscriptLoader fsscriptLoader;

    @Override
    public void afterPropertiesSet() throws Exception {
        Fsscript script = fsscriptLoader.findLoadFsscript("classpath:/foggy/templates/hello-world.fsscript");

        ExpEvaluator evaluator = script.newInstance(applicationContext);
        script.eval(evaluator);

        // Get exported variables
        Object result = evaluator.getExportObject("result");

        System.out.println("export: " + result);
    }

}
```

### Option 2: JSR-223 Standard Interface (Recommended)

FSScript implements the JSR-223 (javax.script) standard interface:

```java
// Non-Spring environment
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("fsscript");

engine.put("name", "World");
engine.eval("export let greeting = `Hello ${name}!`;");
System.out.println(engine.get("greeting"));  // Hello World!
```

### Option 3: Spring Environment Injection

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

### Option 4: Pre-compiled Scripts

Suitable for scenarios requiring repeated execution:

```java
Compilable compilable = (Compilable) fsscriptEngine;
CompiledScript compiled = compilable.compile("export let sum = a + b;");

Bindings bindings = fsscriptEngine.createBindings();
bindings.put("a", 10);
bindings.put("b", 20);
compiled.eval(bindings);
System.out.println(bindings.get("sum"));  // 30
```

## Next Steps

- [Why FSScript](./why-fsscript) - Understand the design philosophy
- [Variables & Types](../syntax/variables) - Learn syntax basics
- [Spring Boot Integration](../java/spring-boot) - Deep integration guide
