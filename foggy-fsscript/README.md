# Foggy FSScript

[中文文档](README.zh-CN.md)

A lightweight scripting language for SQL templating and configuration, inspired by Mondrian's MDX parser with JavaScript-like syntax.

## Purpose

FSScript enables elegant SQL and template composition with IDE support for quick editing. While it implements some JavaScript features, it's designed for configuration and templating - not complex business logic.

## Features

- **JavaScript-like Syntax** - Familiar programming experience with let/const/var, functions, loops, conditionals
- **Template Strings** - Support backtick template literals and `${}` interpolation
- **Modular** - ES6-style import/export for script modularity
- **Spring Integration** - Import Spring Beans via `@beanName` prefix, seamlessly integrate with Spring ecosystem
- **Java Interop** - Import Java classes via `java:` prefix, call static methods or create instances
- **IDE-Friendly** - JavaScript syntax recognized by mainstream IDEs with syntax highlighting and auto-completion
- **Minimal Runtime Overhead** - Lightweight interpreter suitable for embedded use

## Usage

### JSR-223 Standard API (Recommended)

FSScript implements the JSR-223 (javax.script) standard interface, discoverable via `ScriptEngineManager`:

```java
// Option 1: Via ScriptEngineManager (non-Spring environment)
ScriptEngineManager manager = new ScriptEngineManager();
ScriptEngine engine = manager.getEngineByName("fsscript");

engine.put("name", "World");
engine.eval("export let greeting = `Hello ${name}!`;");
System.out.println(engine.get("greeting"));  // Hello World!
```

```java
// Option 2: Spring injection (recommended, auto-supports @bean import)
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
// Option 3: Pre-compiled script (for repeated execution)
Compilable compilable = (Compilable) fsscriptEngine;
CompiledScript compiled = compilable.compile("export let sum = a + b;");

Bindings bindings = fsscriptEngine.createBindings();
bindings.put("a", 10);
bindings.put("b", 20);
compiled.eval(bindings);
System.out.println(bindings.get("sum"));  // 30
```

### Direct Scripting

```java
// Execute FSScript directly
Fsscript script = FileFsscriptLoader.getInstance()
    .findLoadFsscript("classpath:/scripts/my-script.fsscript");

ExpEvaluator evaluator = script.newInstance(applicationContext);
script.eval(evaluator);
Object result = evaluator.getExportObject("result");
```

### Java Interface Proxy (foggy-fsscript-client)

The `foggy-fsscript-client` module provides interface-based FSScript invocation:

```java
// Define interface
public interface MyTemplate {
    String buildQuery(Map<String, Object> params);
}

// Implement with FSScript
MyTemplate template = FsScriptProxy.create(MyTemplate.class, scriptSource);
String sql = template.buildQuery(params);
```

## Advanced Features

### 1. Template Strings & SQL Composition

FSScript's core purpose is elegant SQL and template assembly:

```javascript
// Dynamic SQL building
export function buildQuery(params) {
    let conditions = [];

    if (params.name) {
        conditions.push(`name LIKE '%${params.name}%'`);
    }

    if (params.status) {
        conditions.push(`status = '${params.status}'`);
    }

    let whereClause = conditions.length > 0
        ? `WHERE ${conditions.join(' AND ')}`
        : '';

    return `
        SELECT * FROM users
        ${whereClause}
        ORDER BY created_at DESC
    `;
}
```

### 2. Import Spring Beans

Import beans from Spring container using `@` prefix:

```javascript
// Import single bean
import myService from '@myServiceBean';

// Import multiple bean methods (with renaming support)
import {
    test as testMethod,
    test2,
    test3 as renamedTest3,
    test4
} from '@importBeanTest';

// Call bean methods
export var result1 = testMethod();
export var result2 = test2("parameter");

// Access bean properties
export var result3 = test4.someProperty;

// Call bean object methods
export var result4 = test4.doSomething("parameter");
```

### 3. Import Java Classes

Import Java classes using `java:` prefix, supports static method calls and instance creation:

```javascript
// Import class and use static method
import {test} from 'java:com.example.Utils';
export let result1 = test('param');

// Import entire class
import 'java:com.example.MyClass';
export let result2 = MyClass.staticMethod();

// Import class with renaming
import MyUtil from 'java:com.example.Utils';
export let result3 = MyUtil.staticMethod();

// Create Java object instances
export let instance1 = new MyUtil('param1', 'param2');
export let instance2 = new MyUtil();  // No-arg constructor
```

### 4. Modular Scripts

Support ES6-style module import/export:

```javascript
// utils.fsscript - Utility module
export function formatDate(date) {
    // Format date
}

export const API_URL = "https://api.example.com";

// main.fsscript - Main script
import {formatDate, API_URL} from 'utils.fsscript';
import * as utils from 'utils.fsscript';

let formattedDate = formatDate(new Date());
```

### 5. Functions & Scopes

Support function definitions, nested functions, and closures:

```javascript
function buildQuery() {
    let baseSql = "SELECT * FROM users";

    export function addWhere(condition) {
        return `${baseSql} WHERE ${condition}`;
    }

    export function addOrderBy(field) {
        return `${baseSql} ORDER BY ${field}`;
    }
}

buildQuery();

// Use exported functions
export let query1 = addWhere("status = 'active'");
export let query2 = addOrderBy("created_at DESC");
```

### 6. Arrays & Objects

Support JavaScript-style array and object literals:

```javascript
// Array definition
export var products = [
    { id: 1, name: "Product A", price: 100 },
    { id: 2, name: "Product B", price: 200 },
    { id: 3, name: "Product C", price: 300 }
];

// Array iteration
let totalPrice = 0;
for (let i = 0; i < products.length; i++) {
    totalPrice += products[i].price;
}

// Object access
export var firstProduct = products[0].name;
```

## History

Originally adapted from Mondrian's MDX parser, evolved into a JavaScript-like syntax for better IDE integration and developer experience.

## License

Apache License 2.0
