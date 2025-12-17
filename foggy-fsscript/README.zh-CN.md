# Foggy FSScript

[English](README.md)

一个面向企业级 Java 应用的轻量级脚本语言，采用类 JavaScript 语法，专注于简化配置与开发。

## 设计理念

FSScript 的设计目标**不是追求执行性能**，而是：

- **简化配置** - 如用yml或xml过于复杂时
- **模板** - 可以像javascript一样使用，方便生成模板
- **降低开发门槛** - JSON主体，javascript协助，容易上手
- **深度 Spring 集成** - 可直接调用 Spring Bean，或集成java接口
- **面向 B 端场景** - 为企业级应用的后台任务、报表、数据处理等场景设计

### 与其他方案的对比

| 特性 | FSScript | GraalJS | SpEL | Groovy |
|------|----------|---------|------|--------|
| 学习成本 | 低 (JS 子集) | 低 | 中 | 中 |
| Spring Bean 导入 | `import '@bean'` | 需手动绑定 | 需 `#bean` | 需配置 |
| Java 类导入 | `import 'java:...'` | `Java.type()` | 有限支持 | 原生支持 |
| 脚本模块化 | ES6 import/export | 需自实现 | 不支持 | 支持 |
| 开箱即用 | ✅ | ❌ 需集成层 | ✅ | ✅ |
| 执行性能 | 解释执行 | JIT 编译 | 编译执行 | 编译执行 |
| 适用场景 | 配置/模板/规则 | 通用脚本 | 表达式求值 | 通用脚本 |

**选择 FSScript 的场景：**
- 需要用脚本定义 SQL 模板、动态查询
- 需要灵活配置业务规则但不想引入重量级引擎
- 团队熟悉 JavaScript，希望快速上手

## 特性

- **类 JavaScript 语法** - 熟悉的编程体验，支持 let/const/var、箭头函数、模板字符串等
- **Spring 深度集成** - 通过 `@beanName` 导入 Spring Bean，无缝对接 Spring 生态
- **Java 互操作** - 通过 `java:` 前缀导入 Java 类，调用静态方法或创建实例
- **ES6 模块化** - import/export 语法，支持脚本模块化组织
- **IDE 友好** - JavaScript 语法被主流 IDE 识别，提供语法高亮

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-fsscript</artifactId>
    <version>${foggy.version}</version>
</dependency>
```

### 基本使用

```java
Fsscript script = FileFsscriptLoader.getInstance()
    .findLoadFsscript("classpath:/scripts/my-script.fsscript");

ExpEvaluator evaluator = script.newInstance(applicationContext);
script.eval(evaluator);

// 获取导出的变量
Object result = evaluator.getExportObject("result");
```

### JSR-223 标准方式（推荐）

FSScript 实现了 JSR-223 (javax.script) 标准接口，可通过 `ScriptEngineManager` 发现和使用：

```java
// 方式一：通过 ScriptEngineManager 获取（非 Spring 环境）
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("fsscript");

engine.put("name", "World");
engine.eval("export let greeting = `Hello ${name}!`;");
System.out.println(engine.get("greeting"));  // Hello World!
```

```java
// 方式二：Spring 环境直接注入（推荐，自动支持 @bean 导入）
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

```java
// 方式三：预编译脚本（适合重复执行）
Compilable compilable = (Compilable) fsscriptEngine;
CompiledScript compiled = compilable.compile("export let sum = a + b;");

Bindings bindings = fsscriptEngine.createBindings();
bindings.put("a", 10);
bindings.put("b", 20);
compiled.eval(bindings);
System.out.println(bindings.get("sum"));  // 30
```

## 应用场景示例

### 1. 动态 SQL 模板

FSScript 的核心用途是优雅地组装复杂的动态 SQL 查询：

```javascript
import {workonSessionTokenUsingCache as token} from '@saasBasicWebUtils';

/**
 * 动态 SQL 查询
 * sqlExp、sqlInExp 等函数用于防止 SQL 注入
 */
export const sql = `
    SELECT t.id, t.name, t.amount, t.create_time
    FROM orders t
    WHERE t.tenant_id = '${token.tenantId}'
        ${sqlExp(form.param.teamId, 'AND t.team_id = ?')}
        ${sqlInExp(form.param.statusList, 'AND t.status IN ')}
        ${sqlExp(form.param.startTime, 'AND ? <= t.create_time')}
        ${sqlExp(form.param.endTime, 'AND t.create_time < ?')}
    ORDER BY t.create_time DESC
`;
```

### 2. 导入 Spring Bean

```javascript
// 导入单个 Bean
import myService from '@myServiceBean';

// 导入 Bean 的多个方法
import {
    getUserById,
    saveUser as save,
    deleteUser
} from '@userService';

// 调用
export var user = getUserById(1001);
export var result = save(user);
```

### 3. 导入 Java 类

```javascript
// 导入静态方法
import {format} from 'java:java.lang.String';
import {now} from 'java:java.time.LocalDateTime';

// 导入整个类
import DateUtils from 'java:com.example.utils.DateUtils';

// 使用
export let formatted = format("Hello %s", "World");
export let today = DateUtils.today();

// 创建实例
export let list = new ArrayList();
list.add("item1");
```

### 4. 模块化脚本

```javascript
// utils.fsscript
export function formatMoney(value) {
    return value.toFixed(2) + ' 元';
}

export const TAX_RATE = 0.13;

// main.fsscript
import {formatMoney, TAX_RATE} from './utils.fsscript';

export let price = 100;
export let tax = price * TAX_RATE;
export let display = formatMoney(price + tax);
```

### 5. 闭包与作用域

FSScript 支持 JavaScript 规范的 `let` 块级作用域：

```javascript
var closures = [];

// let 在 for 循环中每次迭代创建新的绑定
for (let i = 0; i < 3; i++) {
    closures.push(() => i);
}

// 结果: 0, 1, 2 (而非 3, 3, 3)
export var result0 = closures[0]();  // 0
export var result1 = closures[1]();  // 1
export var result2 = closures[2]();  // 2
```

## 语法参考

### 支持的语法

| 语法 | 示例 |
|------|------|
| 变量声明 | `var a = 1;` `let b = 2;` `const c = 3;` |
| 未初始化声明 | `var a;` `let b;` |
| 箭头函数 | `(x) => x * 2` `(a, b) => { return a + b; }` |
| 函数定义 | `function foo(x) { return x; }` |
| 模板字符串 | `` `Hello ${name}` `` |
| 对象字面量 | `{ name: 'test', value: 123 }` |
| 数组 | `[1, 2, 3]` |
| 展开运算符 | `{...obj}` `[...arr]` |
| 条件语句 | `if/else if/else` |
| 循环 | `for` `for...in` `for...of` `while` |
| 异常处理 | `try/catch/finally` `throw` |
| 模块 | `import/export` |

### 特有扩展

- `import '@beanName'` - 导入 Spring Bean
- `import 'java:com.example.Class'` - 导入 Java 类
- 函数内 `export` - 允许在函数体内导出变量

## 历史

最初改编自 Mondrian 的 MDX 解析器，用于拼接SQL和MDX，后演化为类 JavaScript 语法以提供更好的 IDE 集成和开发者体验。

## 许可证

Apache License 2.0
