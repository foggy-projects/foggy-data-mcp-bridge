# Operators

## Arithmetic Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `+` | Addition / Concatenation | `1 + 2` => `3` |
| `-` | Subtraction | `5 - 3` => `2` |
| `*` | Multiplication | `2 * 3` => `6` |
| `/` | Division | `6 / 3` => `2` |
| `%` | Modulo | `7 % 3` => `1` |

## Increment/Decrement

```javascript
x++     // Post-increment
++x     // Pre-increment
x--     // Post-decrement
--x     // Pre-decrement
```

## Comparison Operators

| Operator | Description |
|----------|-------------|
| `==` | Equal |
| `<>` | Not equal |
| `<` | Less than |
| `>` | Greater than |
| `<=` | Less than or equal |
| `>=` | Greater than or equal |

## Logical Operators

| Operator | Description |
|----------|-------------|
| `&&` | Logical AND |
| `\|\|` | Logical OR |
| `!` | Logical NOT |

## Ternary Operator

```javascript
condition ? valueIfTrue : valueIfFalse
```

## Optional Chaining

```javascript
obj?.property
obj?.nested?.value
```

## Spread Operator

```javascript
[...arr1, ...arr2, newItem]
{ ...obj1, ...obj2, newProp: value }
```

## Operator Precedence

From highest to lowest:

1. Parentheses `()`
2. Member access `.` `[]` `?.`
3. Unary operators `-` `!` `~` `++` `--` `delete`
4. Multiplication/Division/Modulo `*` `/` `%`
5. Addition/Subtraction `+` `-`
6. Comparison `<` `>` `<=` `>=`
7. Equality `==` `<>`
8. Logical AND `&&`
9. Logical OR `||`
10. Ternary `?:`
11. Assignment `=`
