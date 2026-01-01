# Introduction

FSScript is a lightweight scripting language for Java applications, featuring JavaScript-like syntax and focusing on simplifying configuration, format conversion, template development, and AST syntax tree parsing.

## Design Philosophy

FSScript is **not designed for execution performance**. If you need extreme performance, you should use GraalJS or Groovy. It's also not suitable for writing complex business logic. Its typical scenarios include:

- **Simplify Configuration** - An alternative when YAML or XML becomes too complex
- **Template Generation** - Generate templates as flexibly as JavaScript
- **Lower Barriers** - JSON body + JavaScript assistance, easy to learn
- **Syntax Parsing** - For function permission control, etc.
- **Deep Spring Integration** - Directly call Spring Beans or integrate Java interfaces
- **B2B Scenarios** - Designed for enterprise application backend tasks, reports, data processing, etc.

## Origin

It was originally derived from the MDX syntax compilation of the Mondrian project, used for dynamically assembling MDX statements. Later it was used for assembling SQL statements, templates, and JSON format data processing (native Java handling of these is quite challenging).

## Use Cases

- Need scripts to define SQL templates and dynamic queries
- Need flexible business rule configuration without introducing heavyweight engines
- Team is familiar with JavaScript and wants to get started quickly

## Core Features

- **JavaScript-like Syntax** - Supports let/const/var, arrow functions, template strings, etc.
- **Deep Spring Integration** - Import Spring Beans via `@beanName`
- **Java Interop** - Import Java classes via `java:` prefix
- **ES6 Modules** - import/export syntax, supports script modularization
- **IDE Friendly** - JavaScript syntax is recognized by mainstream IDEs
