# ANTLR Migration Analysis for FSScript

> **Status**: Proposal / For Future Consideration
> **Date**: 2026-01-14
> **Related Issue**: Function default parameter `options = {}` syntax support

## Background

During the implementation of JavaScript-like function default parameter syntax (`function foo(options = {}) {}`), we encountered an LALR(1) limitation where the parser cannot disambiguate `{}` as an empty object literal vs. a code block.

Two solutions were evaluated:
- **Plan A**: Migrate to ANTLR (LL(*) parser with semantic predicates)
- **Plan B**: Scanner-level token differentiation (implemented)

Plan B was implemented successfully. This document records the Plan A analysis for future reference.

---

## Current Architecture

FSScript uses **CUP (LALR(1)) + Manual Scanner**:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  ElExpScanner   │────▶│    ExpParser    │────▶│   Exp (AST)     │
│  (Hand-written) │     │  (CUP-generated)│     │  (50+ classes)  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

Key files:
- `src/main/resources/datasetexp.cup` - Grammar definition
- `src/main/java/.../parser/ElExpScanner.java` - Tokenizer
- `src/main/java/.../parser/ExpParser.java` - Generated parser
- `src/main/java/.../exp/*.java` - AST node implementations

---

## ANTLR Migration: Pros

### 1. Stronger Grammar Expressiveness

| Feature | CUP (LALR(1)) | ANTLR (LL(*) / ALL(*)) |
|---------|---------------|------------------------|
| Lookahead | Fixed 1 token | Adaptive unlimited |
| Left recursion | Manual elimination | Direct support |
| Ambiguity handling | Shift/Reduce conflicts | Semantic predicates |
| Error recovery | Manual implementation | Built-in strategies |

**`{}` Ambiguity Example:**
```antlr
// ANTLR can elegantly solve with semantic predicates
defaultArgValue
    : {isInArgList()}? LBRACE mapItems? RBRACE  // Object literal
    ;

block
    : LBRACE statement* RBRACE  // Code block
    ;
```

### 2. Better Error Handling

```java
// ANTLR built-in error recovery strategies
parser.setErrorHandler(new BailErrorStrategy());     // Fail fast
parser.setErrorHandler(new DefaultErrorStrategy());  // Auto recover

// Friendly error messages with exact location + expected tokens
parser.addErrorListener(new BaseErrorListener() {
    @Override
    public void syntaxError(...) {
        // Precise line/column + expected token set
    }
});
```

### 3. Lexer Modes

```antlr
// Elegant template string handling
BACKTICK : '`' -> pushMode(TEMPLATE_STRING);

mode TEMPLATE_STRING;
TEMPLATE_TEXT : ~[$`]+ ;
TEMPLATE_EXPR : '${' -> pushMode(DEFAULT_MODE);
TEMPLATE_END : '`' -> popMode;
```

### 4. Grammar Islands

```antlr
// Embed different syntaxes
SQL_BLOCK : 'SQL' '{' .*? '}' ;  // Embedded SQL
JSON_BLOCK : 'JSON' '{' .*? '}' ; // Embedded JSON
```

### 5. Toolchain and Ecosystem

| Tool | Purpose |
|------|---------|
| ANTLRWorks / ANTLR Lab | Visual grammar debugging |
| grun (TestRig) | CLI grammar testing |
| IDE plugins | IntelliJ/VS Code syntax highlighting |
| Parse Tree Visualizer | AST visualization |

### 6. Maintainability

```antlr
// ANTLR grammar closer to EBNF, more readable
functionDef
    : 'function' ID '(' paramList? ')' block
    ;

paramList
    : param (',' param)*
    ;

param
    : ID ('=' expression)?  // Default params naturally expressed
    ;
```

### 7. Performance Optimization Options

```java
// ANTLR 4 supports two-stage parsing
parser.getInterpreter().setPredictionMode(PredictionMode.SLL); // Fast mode
// Falls back to
parser.getInterpreter().setPredictionMode(PredictionMode.LL);  // Full mode
```

---

## ANTLR Migration: Cons

### 1. Higher Memory Usage

| Aspect | CUP | ANTLR |
|--------|-----|-------|
| Parse Tree | Not generated (direct AST) | Full tree by default |
| Memory model | Streaming | Full load |
| Large file handling | Clear advantage | Needs optimization |

```java
// ANTLR generates full Parse Tree by default
ParseTree tree = parser.program();  // Entire tree in memory

// Can optimize with Unbuffered mode, but adds complexity
CharStream input = new UnbufferedCharStream(reader);
```

### 2. Runtime Dependency

```xml
<!-- CUP only needs tiny runtime -->
<dependency>
    <groupId>java_cup</groupId>
    <artifactId>runtime</artifactId>
</dependency>

<!-- ANTLR runtime ~500KB -->
<dependency>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-runtime</artifactId>
    <version>4.13.1</version>
</dependency>
```

### 3. Extra Parse Tree → AST Conversion Layer

```
CUP flow:    Token Stream → [Parser] → AST (Exp)
ANTLR flow:  Token Stream → [Parser] → Parse Tree → [Visitor] → AST (Exp)
```

```java
// Need to implement Visitor to build Exp
public class FSScriptASTBuilder extends FSScriptBaseVisitor<Exp> {
    @Override
    public Exp visitFunctionDef(FunctionDefContext ctx) {
        String name = ctx.ID().getText();
        List<Exp> params = visit(ctx.paramList());
        Exp body = visit(ctx.block());
        return factory.createFunctionDef(name, body, params);
    }
    // ... visit method needed for each grammar rule
}
```

### 4. Debugging Complexity

| Scenario | CUP | ANTLR |
|----------|-----|-------|
| Grammar conflicts | Clear Shift/Reduce reports | May hide in predicates |
| Performance issues | Deterministic analysis | ALL(*) may backtrack unexpectedly |
| Semantic predicates | None | Can cause hard-to-debug behavior |

### 5. Indirect Left Recursion Not Supported

```antlr
// Direct left recursion ✅
expr : expr '+' term | term ;

// Indirect left recursion ❌ needs rewrite
A : B 'x' ;
B : A 'y' | 'z' ;
```

### 6. Version Compatibility Risk

ANTLR 3 → 4 was a breaking change. Future ANTLR 5 may have similar risks.

### 7. Performance in Simple Cases

For simple, unambiguous grammars, LALR(1) deterministic analysis is faster than ALL(*) adaptive analysis:

```
Simple expression parsing:
  CUP:    O(n) deterministic
  ANTLR:  O(n) ~ O(n²) depending on ambiguity
```

---

## Comparison Summary

| Dimension | CUP (Current) | ANTLR |
|-----------|---------------|-------|
| **Grammar capability** | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Ambiguity handling** | ⭐⭐ (Scanner hack) | ⭐⭐⭐⭐⭐ (Predicates) |
| **Error recovery** | ⭐⭐ | ⭐⭐⭐⭐ |
| **Tool support** | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Memory efficiency** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Runtime size** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **Learning curve** | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Ecosystem** | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Determinism** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

---

## AST Impact Assessment

**Impact on existing Exp hierarchy: MINIMAL**

The 50+ `Exp` implementations and `ExpFactory` would be preserved. ANTLR migration primarily affects:

1. **Grammar file**: Full rewrite from CUP to ANTLR
2. **Scanner**: Replace hand-written scanner with ANTLR lexer
3. **Parse action code**: Move from CUP actions to ANTLR visitor
4. **Custom logic**: ASI, NF handling, array fix need adaptation

---

## Recommendation

### Scenarios Favoring Migration:
- Frequent grammar extensions needed
- Better error messages required
- Need to embed other DSLs
- Memory not a concern

### Scenarios Against Migration:
- Current grammar is stable
- Package size/memory sensitive
- No complex ambiguity handling needed
- Plan B solution meets requirements

---

## Migration Approach (If Pursued)

1. Write ANTLR grammar alongside existing CUP
2. Create ANTLR visitor that uses existing `ExpFactory`
3. Run parallel testing with both parsers
4. Gradually switch over after validation

---

## Related Files

- Plan B implementation: `ElExpScanner.java` (LBRACE_OBJ context tracking)
- Grammar: `datasetexp.cup`
- Test: `FunctionExpTest#emptyObjectDefaultTest()`

---

## References

- [ANTLR 4 Documentation](https://github.com/antlr/antlr4/blob/master/doc/index.md)
- [CUP User's Manual](http://www2.cs.tum.edu/projects/cup/docs.php)
- [The Definitive ANTLR 4 Reference](https://pragprog.com/titles/tpantlr2/the-definitive-antlr-4-reference/)
