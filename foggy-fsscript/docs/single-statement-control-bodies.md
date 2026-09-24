# 单语句控制体与省略分号

`if/else`、`for`（包括 `in` / `of`）和 `while` 支持不带花括号的单条语句：

```javascript
if (condition) doSomething()

if (condition)
    doSomething()
else
    doSomethingElse()

for (const item of items) process(item)
while (hasNext()) processNext()
```

无花括号时，只有紧接着的一条语句受条件或循环控制。嵌套的 `else` 绑定到最近的、尚未匹配 `else` 的 `if`。需要将多条语句归为一个主体时使用 `{ ... }`。

## 分号边界

单语句主体可以在文件结尾、右花括号之前，或满足自动插分号条件的换行处省略 `;`。换行本身不一定结束表达式：后续的调用括号、下标、属性访问和二元运算符仍然可以继续上一行表达式。

```javascript
if (condition) doSomething()
alwaysRun()

// 同一行的 else 前需要显式分号。
if (condition) doSomething(); else doSomethingElse()

// 调用在下一行继续，仍是一条语句。
if (condition) doSomething
    (argument)
```

`return` 后换行会结束 return 语句；`throw` 后换行属于语法错误。`for (init; condition; update)` 中两个分号必须显式写出。省略分号不能产生一个空的控制体：`if (condition)` 后直接结束文件仍会报错；显式的 `if (condition);` 是空语句。

`++` / `--` 按独立 token 解析，后置自增减不会把下一行误当成加减表达式。前置自增减允许运算符与操作数之间换行。

函数体、`try/catch/finally` 继续使用花括号；单语句主体中的 `let` / `const` 声明也需要放在花括号里。以上规则是 FSScript 对这些控制语法的支持范围，不代表完整的 ECMAScript 实现。

## 实现与回归

- `datasetexp.cup` 使用独立的 `control_body`，并为悬挂 `else` 指定明确的优先级。
- scanner 区分控制条件的右括号与函数调用的右括号，保留换行元数据，并区分控制代码块与对象字面量。
- 解析器只在实际 token 无法继续、分号可以继续时补分号。候选判断只模拟 LR 状态归约，不执行语义动作；禁止在控制头或缺失主体的位置补出空语句。
- `SingleStatementControlBodyTest` 覆盖分支真假、嵌套、循环、提前返回、跳出循环、换行和注释、对象及箭头函数、跨行表达式、非法语法、求值次数、模板入口和解析器复用。
- 新语法的 CUP 生成冲突计数仍为 104，使用现有 `-expect 104` 参数。

JavaScript 自动插分号规则参见 [ECMA-262](https://tc39.es/ecma262/multipage/ecmascript-language-lexical-grammar.html#sec-automatic-semicolon-insertion)。
