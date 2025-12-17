# CUP 升级指南：从 0.10k 到 0.11b

## 问题分析

### 根本原因
Java CUP 从 0.10k 升级到 0.11b (com.github.vbmacher) 后，核心机制发生了变化：

1. **SymbolFactory 机制**：新版本引入了 SymbolFactory 来创建 Symbol 对象
2. **值传播机制改变**：Pass-through 语法规则不再自动传播值

### 已实施的修复

#### 1. SymbolFactory 配置 (datasetexp.cup:24)
```java
// ✅ 正确配置
public ExpParser(ExpFactory factory){
    super(null, new java_cup.runtime.DefaultSymbolFactory());
    this.factory = factory==null?DefaultExpFactory.DEFAULT : factory;
}
```

**为什么使用 DefaultSymbolFactory？**
- ElExpScanner 使用 `new Symbol(id, left, right, value)` 构造函数
- ComplexSymbolFactory 期望不同的 Symbol 格式
- DefaultSymbolFactory 与简单 Symbol 构造函数兼容

#### 2. 修复所有 Pass-through 规则

在 CUP 0.11b 中，这样的规则：
```cup
A ::= B
```

生成的代码是：
```java
RESULT = null;  // ❌ 值丢失！
```

必须改为：
```cup
A ::= B:b {:
    RESULT = b;
:}
```

**已修复的规则列表：**
1. expression_return ::= expression_or_empty
2. ifelse ::= if
3. fun_expression ::= function_def
4. expression_or_empty ::= value_expression
5. value_expression ::= term5
6. term5 ::= term4
7. term4 ::= term3
8. term3 ::= term2
9. term2 ::= term
10. term ::= term0
11. term0 ::= factor
12. factor ::= factor0
13. **factor0 ::= bit** ← 最关键的修复
14. bit ::= value_expression_primary

## 重新生成 Parser

### 步骤 1: 编译测试类
```bash
cd D:\foggy-projects\java-data-mcp-bridge\foggy-fsscript
mvn test-compile
```

### 步骤 2: 运行 CUP 生成器
```bash
java -cp "target/test-classes;target/classes;%USERPROFILE%\.m2\repository\com\github\vbmacher\java-cup\11b-20160615\java-cup-11b-20160615.jar" java_cup.JavacupHelper
```

### 步骤 3: 重新编译主代码
```bash
mvn compile
```

### 步骤 4: 运行测试
```bash
mvn test
```

## 验证测试

### 基础测试
```bash
# 运行所有 fsscript 测试
mvn test -Dtest=com.foggyframework.fsscript.**.*Test

# 单独测试 import 功能
mvn test -Dtest=ImportExpTest
```

### 预期结果
- ✅ 所有单元测试通过
- ✅ 不再出现 UnsupportedOperationException
- ✅ createVarDef 的参数不再为 null
- ✅ import * as 语法正常工作

## 如果问题依旧存在

### 方案 A：回退到旧版本 CUP (不推荐)
```xml
<!-- pom.xml -->
<java-cup-runtime.version>10k</java-cup-runtime.version>
<java-cup.version>10k</java-cup.version>
```

### 方案 B：完全重写 Scanner 适配新 CUP (推荐但工作量大)

需要修改 ElExpScanner.java 使用 SymbolFactory：

```java
// 当前方式（旧）
protected Symbol makeSymbol(int id, Object o) {
    return new Symbol(id, iPrevPrevChar, iChar, o);
}

// 新方式（需要大量修改）
protected Symbol makeSymbol(int id, Object o) {
    return symbolFactory.newSymbol("name", id,
        new Location(iPrevPrevChar),
        new Location(iChar),
        o);
}
```

### 方案 C：验证 CUP 生成的代码

检查生成的 ExpParser.java：

1. **关键位置 1**: ID token 的处理（应该在 case 141 左右）
```java
case 141: // unquoted_identifier ::= ID:i
    String i = (String)((java_cup.runtime.Symbol) CUP$ExpParser$stack.peek()).value;
    Exp RESULT = parser.factory.createId(i);  // ✅ 应该调用 createId
```

2. **关键位置 2**: factor0 ::= bit 的处理（应该在 case 116 左右）
```java
case 116: // factor0 ::= bit:b
    Exp b = (Exp)((java_cup.runtime.Symbol) CUP$ExpParser$stack.peek()).value;
    Exp RESULT = b;  // ✅ 应该传播 b 的值，而不是 null
```

3. **关键位置 3**: var_expression 的处理
```java
case 60: // var_expression ::= VAR factor0:id EQ expression_or_empty:e
    // id 和 e 都应该有值，不应该为 null
    Exp RESULT = parser.factory.createVarDef(id, e);
```

## 诊断命令

如果测试失败，收集以下信息：

```bash
# 1. 查看生成的 Parser 中 factor0 规则的代码
grep -A 10 "case 116" foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ExpParser.java

# 2. 查看 ID token 的处理
grep -A 5 "createId" foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ExpParser.java

# 3. 检查 SymbolFactory 的使用
grep "getSymbolFactory" foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ExpParser.java | head -5
```

## 技术债务

如果继续使用 CUP 0.11b，建议：

1. ✅ 使用 DefaultSymbolFactory（已完成）
2. ✅ 修复所有 pass-through 规则（已完成）
3. ⚠️ 考虑将来重写 Scanner 以完全支持新 CUP 特性
4. ⚠️ 添加 CI 测试确保 Parser 生成正确

## 总结

**兼容性矩阵：**

| 组件 | 旧版本 (0.10k) | 新版本 (0.11b) | 状态 |
|------|---------------|----------------|------|
| CUP 版本 | 未知供应商 | com.github.vbmacher | ✅ 已升级 |
| SymbolFactory | 无 | DefaultSymbolFactory | ✅ 已配置 |
| Pass-through 规则 | 自动传播 | 需显式代码 | ✅ 已修复 |
| Scanner | Symbol 构造函数 | 兼容 Default | ✅ 兼容 |

**下一步操作：**
1. 重新生成 Parser（运行 JavacupHelper）
2. 运行所有测试
3. 如果问题依旧，请提供具体的错误堆栈和生成的 ExpParser.java 的相关代码片段
