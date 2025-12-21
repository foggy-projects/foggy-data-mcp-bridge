# Quick Start

This document explains how to use FSScript in Java, including compiling strings into `Exp` expression objects and executing them.

## Maven Dependency

```xml
<dependency>
    <groupId>com.foggyframework</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

## Minimal Example

```java
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;

public class QuickStart {
    public static void main(String[] args) {
        // 1. Create parser
        ExpParser parser = new ExpParser();

        // 2. Compile script string to Exp object
        Exp exp = parser.compileEl("let x = 10; return x * 2;");

        // 3. Create evaluator
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

        // 4. Execute and get result
        Object result = exp.evalResult(evaluator);

        System.out.println(result);  // Output: 20
    }
}
```

## Core Classes

### ExpParser - Expression Parser

```java
public class ExpParser {
    // Compile expression language script (recommended)
    public Exp compileEl(String str) throws CompileException;

    // Compile script (includes template string parsing)
    public Exp compile(String str) throws CompileException;
}
```

### Exp - Expression Interface

```java
public interface Exp {
    // Execute expression and return raw value
    Object evalValue(ExpEvaluator ee);

    // Execute expression and return unwrapped result (recommended)
    default Object evalResult(ExpEvaluator ee);
}
```

### DefaultExpEvaluator - Default Evaluator

```java
public class DefaultExpEvaluator implements ExpEvaluator {
    // Create new instance
    public static DefaultExpEvaluator newInstance();

    // Create instance with Spring context
    public static DefaultExpEvaluator newInstance(ApplicationContext appCtx);

    // Get variable value
    public Object getVar(String name);

    // Set variable value
    public Object setVar(String name, Object value);
}
```
