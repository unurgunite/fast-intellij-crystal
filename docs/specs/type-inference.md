# Type Inference

Two type resolution systems:

1. **`CrystalTypeInference`** (`completion/`) — type of a **variable** given its
   name and context. Used by completion and Go to Definition. Entry points
   `inferType` (joined `"A | B"`) / `inferTypeList` (per-member, for unions).
2. **`CrystalExpressionTypeResolver`** (`inspections/`) — type of **any
   expression** PSI element. Used by the type-check inspection. Delegates
   variable resolution to `CrystalTypeInference`.

Both return `null` for unknown — callers treat it as "no guessing"
(completion shows nothing extra, inspections skip the check).

## Implemented

### Scalar literals (incl. suffixes)

`x = 1` → `Int32`, `1_i64` → `Int64` (suffixes `i8`–`i128`, `u8`–`u128`,
underscores ignored), `1.0` → `Float64`, `1_f32` → `Float32`, `"s"` →
`String`, `'a'` → `Char`, `:s` → `Symbol`, `true/false` → `Bool`, `nil` →
`Nil`.

### Fixed-shape expressions

Regex → `Regex`, backtick command → `String`, heredoc → `String`,
`:"sym"` → `Symbol`, `sizeof` / `instance_sizeof` / `offsetof` → `Int32`,
`%w` → `Array(String)`, `%i` → `Array(Symbol)`, `%q`/`%Q` → `String`,
`%r` → `Regex`.

### Collections

- Arrays: element unification (`[1,2]` → `Array(Int32)`, mixed →
  `Array(Int32 | String)`), `of Type` annotation wins.
- Hashes: `of K => V` wins, else unified `Hash(K, V)` (`{a: 1}` →
  `Hash(Symbol, Int32)`).
- Tuples: positional (`{1, "hi"}` → `Tuple(Int32, String)`).

### Control-flow unions

Ternary, `if`/`unless` (implicit `else` → `| Nil`), `case`/`when`,
`begin`/`rescue` (`ensure` contributes nothing): last expression per branch,
unified. `typeof(x)` delegates to variable inference.

### Variables and parameters

- Locals and `@ivars` from same-file assignments (incl. `@x : T`
  annotations); annotated method params (`def f(x : A | B)` → both members).
- Multi-assignment indexes the RHS (`a, b = 1, "hi"`); chained assignment
  follows the rightmost value.
- Receiver chains (`x = obj.foo`) propagate through the receiver's inferred
  type into the method's return type, depth-budgeted (self/mutual reference
  terminates as unknown, never `StackOverflowError`).

### Operators

Built-in rules only (no custom `def +` overloads): arithmetic keeps the
operand type (mixed Int+Float → Float), comparisons/logicals → `Bool`
(`<=>` → `Int32`), unary keeps the type, `&&` → RHS type, `||` → union.

### Method return types

No annotation → first `return` statement's type, else the last expression
(implicit return), else unknown. Enables `ret = foo(...)` variable typing.

## Not covered (roadmap)

- Multi-path return unions (`if`/`else` returning different types in one body
  → single path only today).
- Generic parameter resolution (`Array(T)` with abstract `T`).
- Module/mixin composition, nil-check narrowing (`if x` on `Int32?`),
  cross-file ivar assignments, union dedup (`Int32 | Int32`), abstract types
  (`Number`, `Comparable(T)`).
