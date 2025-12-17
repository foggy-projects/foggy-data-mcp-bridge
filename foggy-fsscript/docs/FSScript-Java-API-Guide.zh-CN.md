# FSScript Java API 使用指南

本文档介绍如何在 Java 中使用 FSScript，包括将字符串编译成 `Exp` 表达式对象并执行。

## 目录

1. [快速开始](#1-快速开始)
2. [核心类介绍](#2-核心类介绍)
3. [编译表达式](#3-编译表达式)
4. [执行表达式](#4-执行表达式)
5. [高级用法](#5-高级用法)
6. [完整示例](#6-完整示例)

---

## 1. 快速开始

### 1.1 Maven 依赖

```xml
<dependency>
    <groupId>com.foggyframework</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

### 1.2 最简示例

```java
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;

public class QuickStart {
    public static void main(String[] args) {
        // 1. 创建解析器
        ExpParser parser = new ExpParser();

        // 2. 编译脚本字符串为 Exp 对象
        Exp exp = parser.compileEl("let x = 10; return x * 2;");

        // 3. 创建执行器
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

        // 4. 执行并获取结果
        Object result = exp.evalResult(evaluator);

        System.out.println(result);  // 输出: 20
    }
}
```

---

## 2. 核心类介绍

### 2.1 ExpParser - 表达式解析器

`ExpParser` 是将 FSScript 字符串编译为 `Exp` 对象的核心类。

```java
package com.foggyframework.fsscript.parser;

public class ExpParser extends java_cup.runtime.lr_parser {

    // 默认构造器
    public ExpParser();

    // 使用自定义 ExpFactory 构造
    public ExpParser(ExpFactory factory);

    // 编译表达式语言脚本（推荐使用）
    public Exp compileEl(String str) throws CompileException;

    // 编译脚本（包含模板字符串解析）
    public Exp compile(String str) throws CompileException;
}
```

### 2.2 Exp - 表达式接口

`Exp` 是所有表达式的基础接口。

```java
package com.foggyframework.fsscript.parser.spi;

public interface Exp {

    // 执行表达式并返回原始值（可能包含 ReturnExpObject 包装）
    Object evalValue(ExpEvaluator ee);

    // 执行表达式并返回解包后的结果（推荐使用）
    default Object evalResult(ExpEvaluator ee);

    // 获取返回类型
    Class getReturnType(ExpEvaluator ee);
}
```

### 2.3 DefaultExpEvaluator - 默认执行器

`DefaultExpEvaluator` 是表达式的运行时执行器，管理变量作用域和上下文。

```java
package com.foggyframework.fsscript;

public class DefaultExpEvaluator implements ExpEvaluator {

    // 创建新实例
    public static DefaultExpEvaluator newInstance();

    // 创建带 Spring 上下文的实例
    public static DefaultExpEvaluator newInstance(ApplicationContext appCtx);

    // 获取变量值
    public Object getVar(String name);

    // 设置变量值
    public Object setVar(String name, Object value);

    // 获取当前闭包
    public FsscriptClosure getCurrentFsscriptClosure();

    // 获取 Spring 上下文
    public ApplicationContext getApplicationContext();
}
```

---

## 3. 编译表达式

### 3.1 使用 compileEl() 方法

`compileEl()` 是编译 FSScript 脚本的主要方法：

```java
ExpParser parser = new ExpParser();

// 编译简单表达式
Exp exp1 = parser.compileEl("1 + 2");

// 编译包含变量的表达式
Exp exp2 = parser.compileEl("let x = 10; let y = 20; return x + y;");

// 编译包含函数的表达式
Exp exp3 = parser.compileEl("function add(a, b) { return a + b; } add(1, 2);");

// 编译包含对象的表达式
Exp exp4 = parser.compileEl("let obj = {a: 1, b: 2}; return obj.a + obj.b;");
```

### 3.2 使用 compile() 方法

`compile()` 方法用于编译包含模板字符串的脚本：

```java
ExpParser parser = new ExpParser();

// 编译包含 ${} 插值的字符串
String script = "/static/jsClasses/WeiXin.js${version}";
Exp exp = parser.compile(script);
```

### 3.3 编译异常处理

```java
try {
    ExpParser parser = new ExpParser();
    Exp exp = parser.compileEl("invalid syntax {{");
} catch (CompileException e) {
    System.err.println("编译错误: " + e.getMessage());
} catch (FoggyParseException e) {
    // 语法错误，包含位置信息
    System.err.println("语法错误: " + e.getMessage());
}
```

---

## 4. 执行表达式

### 4.1 evalResult() vs evalValue()

两个方法的区别：

```java
ExpParser parser = new ExpParser();
DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

Exp exp = parser.compileEl("return 10 * 2");

// evalValue() - 返回原始值，可能被 ReturnExpObject 包装
Object rawValue = exp.evalValue(evaluator);
// rawValue 类型是 Exp.ReturnExpObject

// evalResult() - 返回解包后的实际值（推荐）
Object result = exp.evalResult(evaluator);
// result = 20
```

**推荐使用 `evalResult()`**，它会自动处理 `return` 语句返回的包装对象。

### 4.2 处理不同类型的返回值

```java
ExpParser parser = new ExpParser();
DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();

// 数值
Integer num = (Integer) parser.compileEl("1 + 2").evalResult(ee);

// 字符串
String str = (String) parser.compileEl("'hello' + ' world'").evalResult(ee);

// 布尔值
Boolean bool = (Boolean) parser.compileEl("1 < 2").evalResult(ee);

// 数组/列表
List<Integer> list = (List<Integer>) parser.compileEl("[1, 2, 3]").evalResult(ee);

// 对象/Map
Map<String, Object> map = (Map<String, Object>) parser.compileEl("{a: 1, b: 2}").evalResult(ee);
```

### 4.3 预设变量

```java
DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

// 设置变量
evaluator.setVar("userName", "John");
evaluator.setVar("config", configMap);

// 在脚本中使用
ExpParser parser = new ExpParser();
Exp exp = parser.compileEl("return 'Hello, ' + userName;");
String result = (String) exp.evalResult(evaluator);  // "Hello, John"
```

---

## 5. 高级用法

### 5.1 使用 Spring 上下文

```java
@Autowired
private ApplicationContext applicationContext;

public void executeScript(String script) {
    ExpParser parser = new ExpParser();
    Exp exp = parser.compileEl(script);

    // 创建带 Spring 上下文的执行器
    DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance(applicationContext);

    Object result = exp.evalResult(evaluator);
    // 现在脚本可以通过 @beanName 访问 Spring Bean
}
```

### 5.2 获取导出的变量

FSScript 支持 `export` 语句导出变量：

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

// 获取导出的变量
FsscriptClosure closure = evaluator.getCurrentFsscriptClosure();
Map<String, Object> exportMap = (Map<String, Object>) closure.getVar(FsscriptClosure.EXPORT_MAP_KEY);

Object x = exportMap.get("x");  // 10
Object y = exportMap.get("y");  // 20
```

### 5.3 自定义 ExpFactory

```java
// 使用自定义工厂
ExpFactory customFactory = new MyCustomExpFactory();
ExpParser parser = new ExpParser(customFactory);

Exp exp = parser.compileEl("custom expression");
```

### 5.4 克隆执行器

在并发场景下，可能需要克隆执行器以保证线程安全：

```java
DefaultExpEvaluator original = DefaultExpEvaluator.newInstance();
original.setVar("shared", "value");

// 克隆执行器
ExpEvaluator cloned = original.clone();

// 在不同线程中使用克隆的执行器
```

### 5.5 模块导入

```java
ExpParser parser = new ExpParser();
DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

// 主脚本导入其他模块
String script = """
    import { helper } from 'utils.fsscript';
    return helper(10);
    """;

Exp exp = parser.compileEl(script);
Object result = exp.evalResult(evaluator);
```

---

## 6. 完整示例

### 6.1 基础计算器

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
            throw new RuntimeException("计算失败: " + expression, e);
        }
    }

    public static void main(String[] args) {
        Calculator calc = new Calculator();

        // 基础运算
        System.out.println(calc.evaluate("1 + 2"));        // 3
        System.out.println(calc.evaluate("(1 + 2) * 3"));  // 9

        // 使用变量
        System.out.println(calc.evaluate("let x = 10; let y = 20; return x + y;"));  // 30

        // 使用函数
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

### 6.2 数据处理器

```java
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import java.util.*;

public class DataProcessor {

    public static void main(String[] args) {
        ExpParser parser = new ExpParser();
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();

        // 设置输入数据
        List<Map<String, Object>> users = Arrays.asList(
            Map.of("name", "Alice", "age", 25),
            Map.of("name", "Bob", "age", 30),
            Map.of("name", "Charlie", "age", 35)
        );
        evaluator.setVar("users", users);

        // 使用脚本处理数据
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

        System.out.println("用户数: " + result.get("count"));       // 3
        System.out.println("姓名列表: " + result.get("names"));     // Alice, Bob, Charlie
        System.out.println("平均年龄: " + result.get("avgAge"));    // 30.0
    }
}
```

### 6.3 条件规则引擎

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

        // 设置上下文变量
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            evaluator.setVar(entry.getKey(), entry.getValue());
        }

        // 执行规则
        for (Rule rule : rules) {
            Boolean matches = (Boolean) rule.condition.evalResult(evaluator);
            if (Boolean.TRUE.equals(matches)) {
                System.out.println("规则匹配: " + rule.name);
                return rule.action.evalResult(evaluator);
            }
        }

        return null;
    }

    public static void main(String[] args) {
        RuleEngine engine = new RuleEngine();

        List<Rule> rules = Arrays.asList(
            new Rule("VIP折扣", "userLevel == 'VIP' && amount > 100", "amount * 0.8"),
            new Rule("普通折扣", "amount > 200", "amount * 0.9"),
            new Rule("无折扣", "true", "amount")
        );

        Map<String, Object> context = Map.of(
            "userLevel", "VIP",
            "amount", 150
        );

        Object result = engine.executeRules(rules, context);
        System.out.println("最终金额: " + result);  // 120.0
    }
}
```

---

## 附录

### A. 常见表达式类型

| 表达式类 | 描述 | 示例 |
|----------|------|------|
| `IdExp` | 标识符/变量引用 | `x`, `userName` |
| `StringExp` | 字符串字面量 | `'hello'`, `"world"` |
| `NumberExp` | 数值字面量 | `123`, `45.67` |
| `BooleanExp` | 布尔字面量 | `true`, `false` |
| `NullExp` | 空值 | `null` |
| `ArrayExp` | 数组字面量 | `[1, 2, 3]` |
| `MapExp` | 对象/Map字面量 | `{a: 1, b: 2}` |
| `VarExp` | 变量声明 | `var x = 10` |
| `ForExp` | 循环 | `for (...) { }` |
| `ReturnExp` | 返回语句 | `return value` |
| `FunctionDefExp` | 函数定义 | `function f() { }` |

### B. 编译流程

```
FSScript 字符串
       ↓
  ExpScanner (词法分析)
       ↓
  ExpParser (语法分析 - CUP 生成)
       ↓
  Exp 对象树 (AST)
       ↓
  DefaultExpEvaluator (执行)
       ↓
  结果值 (Object)
```

### C. 异常处理最佳实践

```java
public Object safeEvaluate(String script) {
    try {
        ExpParser parser = new ExpParser();
        Exp exp = parser.compileEl(script);
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        return exp.evalResult(evaluator);
    } catch (CompileException e) {
        // 编译错误
        log.error("脚本编译失败: {}", e.getMessage());
        throw new ScriptException("编译错误", e);
    } catch (FoggyParseException e) {
        // 语法错误
        log.error("脚本语法错误: {}", e.getMessage());
        throw new ScriptException("语法错误", e);
    } catch (RuntimeException e) {
        // 运行时错误
        log.error("脚本执行失败: {}", e.getMessage());
        throw new ScriptException("执行错误", e);
    }
}
```
