# FSScript Java API Guide

[中文文档](FSScript-Java-API-Guide.zh-CN.md)

This document explains how to use FSScript in Java, including compiling strings into `Exp` expression objects and executing them.

## Table of Contents

1. [Quick Start](#1-quick-start)
2. [Core Classes](#2-core-classes)
3. [Compiling Expressions](#3-compiling-expressions)
4. [Executing Expressions](#4-executing-expressions)
5. [Advanced Usage](#5-advanced-usage)
6. [Complete Examples](#6-complete-examples)

---

## 1. Quick Start

### 1.1 Maven Dependency

```xml
<dependency>
    <groupId>com.foggyframework</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

### 1.2 Minimal Example

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

---

## 2. Core Classes

### 2.1 ExpParser - Expression Parser

`ExpParser` is the core class for compiling FSScript strings into `Exp` objects.

```java
package com.foggyframework.fsscript.parser;

public class ExpParser extends java_cup.runtime.lr_parser {

    // Default constructor
    public ExpParser();

    // Constructor with custom ExpFactory
    public ExpParser(ExpFactory factory);

    // Compile expression language script (recommended)
    public Exp compileEl(String str) throws CompileException;

    // Compile script (includes template string parsing)
    public Exp compile(String str) throws CompileException;
}
```

### 2.2 Exp - Expression Interface

`Exp` is the base interface for all expressions.

```java
package com.foggyframework.fsscript.parser.spi;

public interface Exp {

    // Execute expression and return raw value (may be wrapped in ReturnExpObject)
    Object evalValue(ExpEvaluator ee);

    // Execute expression and return unwrapped result (recommended)
    default Object evalResult(ExpEvaluator ee);

    // Get return type
    Class getReturnType(ExpEvaluator ee);
}
```

### 2.3 DefaultExpEvaluator - Default Evaluator

`DefaultExpEvaluator` is the runtime executor that manages variable scopes and contexts.

```java
package com.foggyframework.fsscript;

public class DefaultExpEvaluator implements ExpEvaluator {

    // Create new instance
    public static DefaultExpEvaluator newInstance();

    // Create instance with Spring context
    public static DefaultExpEvaluator newInstance(ApplicationContext appCtx);

    // Get variable value
    public Object getVar(String name);

    // Set variable value
    public Object setVar(String name, Object value);

    // Get current closure
    public FsscriptClosure getCurrentFsscriptClosure();

    // Get Spring context
    public ApplicationContext getApplicationContext();
}
```

---

## 3. Compiling Expressions

### 3.1 Using compileEl() Method

`compileEl()` is the primary method for compiling FSScript scripts:

```java
ExpParser parser = new ExpParser();

// Compile simple expression
Exp exp1 = parser.compileEl("1 + 2");

// Compile expression with variables
Exp exp2 = parser.compileEl("let x = 10; let y = 20; return x + y;");

// Compile expression with functions
Exp exp3 = parser.compileEl("function add(a, b) { return a + b; } add(1, 2);");

// Compile expression with objects
Exp exp4 = parser.compileEl("let obj = {a: 1, b: 2}; return obj.a + obj.b;");
```

### 3.2 Using compile() Method

`compile()` method is used for compiling scripts with template strings:

```java
ExpParser parser = new ExpParser();

// Compile string with ${} interpolation
String script = "/static/jsClasses/WeiXin.js${version}";
Exp exp = parser.compile(script);
```

### 3.3 Compilation Error Handling

```java
try {
    ExpParser parser = new ExpParser();
    Exp exp = parser.compileEl("invalid syntax {{");
} catch (CompileException e) {
    System.err.println("Compilation error: " + e.getMessage());
} catch (FoggyParseException e) {
    // Syntax error with position info
    System.err.println("Syntax error: " + e.getMessage());
}
```

---

## 4. Executing Expressions

### 4.1 evalResult() vs evalValue()

Differences between the two methods:

```java
ExpParser parser = new ExpParser();
DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

Exp exp = parser.compileEl("return 10 * 2");

// evalValue() - Returns raw value, may be wrapped in ReturnExpObject
Object rawValue = exp.evalValue(evaluator);
// rawValue type is Exp.ReturnExpObject

// evalResult() - Returns unwrapped actual value (recommended)
Object result = exp.evalResult(evaluator);
// result = 20
```

**Recommended to use `evalResult()`**, as it automatically handles wrapped objects returned by `return` statements.

### 4.2 Handling Different Return Types

```java
ExpParser parser = new ExpParser();
DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();

// Numeric
Integer num = (Integer) parser.compileEl("1 + 2").evalResult(ee);

// String
String str = (String) parser.compileEl("'hello' + ' world'").evalResult(ee);

// Boolean
Boolean bool = (Boolean) parser.compileEl("1 < 2").evalResult(ee);

// Array/List
List<Integer> list = (List<Integer>) parser.compileEl("[1, 2, 3]").evalResult(ee);

// Object/Map
Map<String, Object> map = (Map<String, Object>) parser.compileEl("{a: 1, b: 2}").evalResult(ee);
```

### 4.3 Preset Variables

```java
DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

// Set variables
evaluator.setVar("userName", "John");
evaluator.setVar("config", configMap);

// Use in script
ExpParser parser = new ExpParser();
Exp exp = parser.compileEl("return 'Hello, ' + userName;");
String result = (String) exp.evalResult(evaluator);  // "Hello, John"
```

---

## 5. Advanced Usage

### 5.1 Using Spring Context

```java
@Autowired
private ApplicationContext applicationContext;

public void executeScript(String script) {
    ExpParser parser = new ExpParser();
    Exp exp = parser.compileEl(script);

    // Create evaluator with Spring context
    DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance(applicationContext);

    Object result = exp.evalResult(evaluator);
    // Script can now access Spring Beans via @beanName
}
```

### 5.2 Retrieving Exported Variables

FSScript supports `export` statements to export variables:

```java
ExpParser parser = new ExpParser();
DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

String script = """
    export var x = 10;
    export var y = 20;
    export function add(a, b) { return a + b; }
    """;

Exp exp = parser.compileEl(script);
exp.evalResult(evaluator);

// Retrieve exported variables
FsscriptClosure closure = evaluator.getCurrentFsscriptClosure();
Map<String, Object> exportMap = (Map<String, Object>) closure.getVar(FsscriptClosure.EXPORT_MAP_KEY);

Object x = exportMap.get("x");  // 10
Object y = exportMap.get("y");  // 20
```

### 5.3 Custom ExpFactory

```java
// Use custom factory
ExpFactory customFactory = new MyCustomExpFactory();
ExpParser parser = new ExpParser(customFactory);

Exp exp = parser.compileEl("custom expression");
```

### 5.4 Cloning Evaluators

In concurrent scenarios, you may need to clone evaluators for thread safety:

```java
DefaultExpEvaluator original = DefaultExpEvaluator.newInstance();
original.setVar("shared", "value");

// Clone evaluator
ExpEvaluator cloned = original.clone();

// Use cloned evaluator in different threads
```

### 5.5 Module Imports

```java
ExpParser parser = new ExpParser();
DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

// Main script imports other modules
String script = """
    import { helper } from 'utils.fsscript';
    return helper(10);
    """;

Exp exp = parser.compileEl(script);
Object result = exp.evalResult(evaluator);
```

---

## 6. Complete Examples

### 6.1 Basic Calculator

```java
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;

public class Calculator {

    private final ExpParser parser = new ExpParser();

    public Object evaluate(String expression) {
        try {
            Exp exp = parser.compileEl(expression);
            DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
            return exp.evalResult(evaluator);
        } catch (Exception e) {
            throw new RuntimeException("Evaluation failed: " + expression, e);
        }
    }

    public static void main(String[] args) {
        Calculator calc = new Calculator();

        // Basic operations
        System.out.println(calc.evaluate("1 + 2"));        // 3
        System.out.println(calc.evaluate("(1 + 2) * 3"));  // 9

        // Using variables
        System.out.println(calc.evaluate("let x = 10; let y = 20; return x + y;"));  // 30

        // Using functions
        System.out.println(calc.evaluate(
            "function factorial(n) { " +
            "  if (n <= 1) { return 1; } " +
            "  return n * factorial(n - 1); " +
            "} " +
            "factorial(5);"
        ));  // 120
    }
}
```

### 6.2 Data Processor

```java
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import java.util.*;

public class DataProcessor {

    public static void main(String[] args) {
        ExpParser parser = new ExpParser();
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

        // Set input data
        List<Map<String, Object>> users = Arrays.asList(
            Map.of("name", "Alice", "age", 25),
            Map.of("name", "Bob", "age", 30),
            Map.of("name", "Charlie", "age", 35)
        );
        evaluator.setVar("users", users);

        // Process data with script
        String script = """
            let result = {
                count: users.length,
                names: users.map(u => u.name).join(', '),
                avgAge: 0
            };

            let totalAge = 0;
            for (const user of users) {
                totalAge = totalAge + user.age;
            }
            result.avgAge = totalAge / users.length;

            return result;
            """;

        Exp exp = parser.compileEl(script);
        Map<String, Object> result = (Map<String, Object>) exp.evalResult(evaluator);

        System.out.println("User count: " + result.get("count"));       // 3
        System.out.println("Name list: " + result.get("names"));        // Alice, Bob, Charlie
        System.out.println("Average age: " + result.get("avgAge"));     // 30.0
    }
}
```

### 6.3 Conditional Rule Engine

```java
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;

public class RuleEngine {

    private final ExpParser parser = new ExpParser();

    public static class Rule {
        public final String name;
        public final Exp condition;
        public final Exp action;

        public Rule(String name, String conditionScript, String actionScript) {
            ExpParser parser = new ExpParser();
            this.name = name;
            this.condition = parser.compileEl(conditionScript);
            this.action = parser.compileEl(actionScript);
        }
    }

    public Object executeRules(List<Rule> rules, Map<String, Object> context) {
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

        // Set context variables
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            evaluator.setVar(entry.getKey(), entry.getValue());
        }

        // Execute rules
        for (Rule rule : rules) {
            Boolean matches = (Boolean) rule.condition.evalResult(evaluator);
            if (Boolean.TRUE.equals(matches)) {
                System.out.println("Rule matched: " + rule.name);
                return rule.action.evalResult(evaluator);
            }
        }

        return null;
    }

    public static void main(String[] args) {
        RuleEngine engine = new RuleEngine();

        List<Rule> rules = Arrays.asList(
            new Rule("VIP discount", "userLevel == 'VIP' && amount > 100", "amount * 0.8"),
            new Rule("Regular discount", "amount > 200", "amount * 0.9"),
            new Rule("No discount", "true", "amount")
        );

        Map<String, Object> context = Map.of(
            "userLevel", "VIP",
            "amount", 150
        );

        Object result = engine.executeRules(rules, context);
        System.out.println("Final amount: " + result);  // 120.0
    }
}
```

---

## Appendix

### A. Common Expression Types

| Expression Class | Description | Example |
|-----------------|-------------|---------|
| `IdExp` | Identifier/variable reference | `x`, `userName` |
| `StringExp` | String literal | `'hello'`, `"world"` |
| `NumberExp` | Numeric literal | `123`, `45.67` |
| `BooleanExp` | Boolean literal | `true`, `false` |
| `NullExp` | Null value | `null` |
| `ArrayExp` | Array literal | `[1, 2, 3]` |
| `MapExp` | Object/Map literal | `{a: 1, b: 2}` |
| `VarExp` | Variable declaration | `var x = 10` |
| `ForExp` | Loop | `for (...) { }` |
| `ReturnExp` | Return statement | `return value` |
| `FunctionDefExp` | Function definition | `function f() { }` |

### B. Compilation Process

```
FSScript string
       ↓
  ExpScanner (lexical analysis)
       ↓
  ExpParser (syntax analysis - CUP generated)
       ↓
  Exp object tree (AST)
       ↓
  DefaultExpEvaluator (execution)
       ↓
  Result value (Object)
```

### C. Exception Handling Best Practices

```java
public Object safeEvaluate(String script) {
    try {
        ExpParser parser = new ExpParser();
        Exp exp = parser.compileEl(script);
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        return exp.evalResult(evaluator);
    } catch (CompileException e) {
        // Compilation error
        log.error("Script compilation failed: {}", e.getMessage());
        throw new ScriptException("Compilation error", e);
    } catch (FoggyParseException e) {
        // Syntax error
        log.error("Script syntax error: {}", e.getMessage());
        throw new ScriptException("Syntax error", e);
    } catch (RuntimeException e) {
        // Runtime error
        log.error("Script execution failed: {}", e.getMessage());
        throw new ScriptException("Execution error", e);
    }
}
```
