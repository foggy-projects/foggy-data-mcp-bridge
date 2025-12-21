# Quick Start

## Maven Dependency

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

## Basic Usage

### Option 1: Load Script Files Directly

```java
Fsscript script = FileFsscriptLoader.getInstance()
    .findLoadFsscript("classpath:/scripts/my-script.fsscript");

ExpEvaluator evaluator = script.newInstance(applicationContext);
script.eval(evaluator);

// Get exported variables
Object result = evaluator.getExportObject("result");
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

### Option 3: Spring Injection

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

For scripts that need repeated execution:

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
