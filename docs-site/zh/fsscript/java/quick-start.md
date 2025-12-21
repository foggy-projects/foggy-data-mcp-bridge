# 快速开始

本文档介绍如何在 Java 中使用 FSScript，包括编译字符串为 `Exp` 表达式对象并执行。

## Maven 依赖

```xml
<dependency>
    <groupId>com.foggyframework</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

## 最小示例

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

## 核心类

### ExpParser - 表达式解析器

```java
public class ExpParser {
    // 编译表达式语言脚本（推荐）
    public Exp compileEl(String str) throws CompileException;

    // 编译脚本（包含模板字符串解析）
    public Exp compile(String str) throws CompileException;
}
```

### Exp - 表达式接口

```java
public interface Exp {
    // 执行表达式并返回原始值
    Object evalValue(ExpEvaluator ee);

    // 执行表达式并返回解包后的结果（推荐）
    default Object evalResult(ExpEvaluator ee);
}
```

### DefaultExpEvaluator - 默认执行器

```java
public class DefaultExpEvaluator implements ExpEvaluator {
    // 创建新实例
    public static DefaultExpEvaluator newInstance();

    // 创建带 Spring 上下文的实例
    public static DefaultExpEvaluator newInstance(ApplicationContext appCtx);

    // 获取变量值
    public Object getVar(String name);

    // 设置变量值
    public Object setVar(String name, Object value);
}
```
