# API Reference

## Core Interfaces

### Exp

Base expression interface.

```java
public interface Exp {
    /**
     * Execute expression and return raw value
     * May be wrapped in ReturnExpObject
     */
    Object evalValue(ExpEvaluator ee);

    /**
     * Execute expression and return unwrapped result (recommended)
     */
    default Object evalResult(ExpEvaluator ee);

    /**
     * Get return type
     */
    Class getReturnType(ExpEvaluator ee);
}
```

### ExpEvaluator

Runtime evaluator interface.

```java
public interface ExpEvaluator {
    Object getVar(String name);
    Object setVar(String name, Object value);
    Object getExportObject(String name);
    ExpEvaluator clone();
}
```

### Fsscript

Loaded script interface.

```java
public interface Fsscript {
    void eval(ExpEvaluator ee);
    Object evalResult(ExpEvaluator ee);
    ExpEvaluator newInstance(ApplicationContext appCtx);
    boolean hasImport();
}
```

## Implementation Classes

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

## Exception Classes

| Exception | Description |
|-----------|-------------|
| `CompileException` | Compilation error |
| `FoggyParseException` | Syntax parsing error |

## Compilation Flow

```
FSScript String
       ↓
  ExpScanner (Lexical Analysis)
       ↓
  ExpParser (Syntax Analysis - CUP Generated)
       ↓
  Exp Object Tree (AST)
       ↓
  DefaultExpEvaluator (Execution)
       ↓
  Result Value (Object)
```
