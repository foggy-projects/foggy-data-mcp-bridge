# API 参考

## 核心接口

### Exp

表达式基础接口。

```java
public interface Exp {
    /**
     * 执行表达式并返回原始值
     * 可能被包装在 ReturnExpObject 中
     */
    Object evalValue(ExpEvaluator ee);

    /**
     * 执行表达式并返回解包后的结果（推荐）
     */
    default Object evalResult(ExpEvaluator ee);

    /**
     * 获取返回类型
     */
    Class getReturnType(ExpEvaluator ee);
}
```

### ExpEvaluator

运行时执行器接口。

```java
public interface ExpEvaluator {
    Object getVar(String name);
    Object setVar(String name, Object value);
    Object getExportObject(String name);
    ExpEvaluator clone();
}
```

### Fsscript

加载的脚本接口。

```java
public interface Fsscript {
    void eval(ExpEvaluator ee);
    Object evalResult(ExpEvaluator ee);
    ExpEvaluator newInstance(ApplicationContext appCtx);
    boolean hasImport();
}
```

## 实现类

### ExpParser

```java
public class ExpParser {
    public ExpParser();
    public ExpParser(ExpFactory factory);
    public Exp compileEl(String str) throws CompileException;
    public Exp compile(String str) throws CompileException;
}
```

### DefaultExpEvaluator

```java
public class DefaultExpEvaluator implements ExpEvaluator {
    public static DefaultExpEvaluator newInstance();
    public static DefaultExpEvaluator newInstance(ApplicationContext appCtx);
    public Object getVar(String name);
    public Object setVar(String name, Object value);
    public FsscriptClosure getCurrentFsscriptClosure();
    public ApplicationContext getApplicationContext();
}
```

### FileFsscriptLoader

```java
public class FileFsscriptLoader {
    public static FileFsscriptLoader getInstance();
    public Fsscript findLoadFsscript(String path);
}
```

## 异常类

| 异常 | 说明 |
|------|------|
| `CompileException` | 编译错误 |
| `FoggyParseException` | 语法解析错误 |

## 编译流程

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
