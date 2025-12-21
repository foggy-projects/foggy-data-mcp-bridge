# Introduction

FSScript is a lightweight scripting language for Java applications, using JavaScript-like syntax, focused on simplifying configuration and template development.

## Design Philosophy

FSScript's design goal is **not about execution performance**, but rather:

- **Simplify Configuration** - An alternative when YAML or XML becomes too complex
- **Template Generation** - Generate templates as flexibly as JavaScript
- **Lower Barrier** - JSON-based + JavaScript assistance, easy to learn
- **Deep Spring Integration** - Directly call Spring Beans or integrate Java interfaces
- **Enterprise-focused** - Designed for backend tasks, reports, data processing in enterprise applications

## Comparison with Other Solutions

| Feature | FSScript | GraalJS | SpEL | Groovy |
|---------|----------|---------|------|--------|
| Learning Curve | Low (JS subset) | Low | Medium | Medium |
| Spring Bean Import | `import '@bean'` | Manual binding | `#bean` | Requires config |
| Java Class Import | `import 'java:...'` | `Java.type()` | Limited | Native support |
| Script Modularization | ES6 import/export | DIY | Not supported | Supported |
| Out-of-box | Yes | No (needs integration) | Yes | Yes |
| Execution | Interpreted | JIT compiled | Compiled | Compiled |
| Use Case | Config/Template/Rules | General scripting | Expression evaluation | General scripting |

## When to Choose FSScript

- Need scripts for SQL templates and dynamic queries
- Need flexible business rules without heavyweight engines
- Team is familiar with JavaScript and wants quick onboarding

## Core Features

- **JavaScript-like Syntax** - Supports let/const/var, arrow functions, template strings
- **Deep Spring Integration** - Import Spring Beans via `@beanName`
- **Java Interop** - Import Java classes via `java:` prefix
- **ES6 Modules** - import/export syntax for modular scripts
- **IDE Friendly** - JavaScript syntax recognized by mainstream IDEs
