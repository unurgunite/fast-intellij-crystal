# Inspection export triage — 2026-11

A run of the three inspections over a large third-party Crystal project, kept as
evidence for what the plugin reported at that point. The raw IntelliJ XML exports
were not committed: they are machine output full of absolute paths into that
project, and the findings below are what survives.

Counts, as exported: 89 argument-count, 200 type-mismatch, 58 unused-variable.

## Already fixed

Checked by re-running the same call shapes against the current build. Each now
resolves correctly and is pinned by a test, so a regression is caught rather than
rediscovered:

| reported | now |
|---|---|
| `expected 'App::Params::Metadata', got 'Metadata(MyEnum)'` | correct — a specialised type fits its bare class, namespaced or not (`CrystalTypeCompatibilityTest`) |
| `expected 'Array(Router::CompiledRoute::Token)', got 'Token)'` | correct — a generic type is never truncated (`CrystalTypeCompatibilityTest`) |
| `expected 'Array(String)', got 'Array(Nil)'` | correct — `[] of String` is an `Array(String)` (`CrystalTypeCheckGenericsTest`) |
| `expected '{Int64, String, ...}', got '{'` | correct — a tuple literal is not truncated (`CrystalTypeCheckGenericsTest`) |
| `expected 'Array(String) \| Proc(...) \| Nil', got 'Enumerable(String)?'` | correct — a collection satisfies `Enumerable(T)` (`CrystalTypeCheckGenericsTest`) |
| `expected 'Array(Float32)', got 'Float64'` | correct — an `_f32` literal is a `Float32` (`CrystalTypeCompatibilityTest`) |

## Open, and why

**`expected 'Parser' or 'Type' or 'Int' or 'Float', got 'Hash(String, Payload)'` (13)**
A `Hash` passed where the parameter is a union of scalars plus `Parser`. This
is the export's most common finding and it is a genuine mismatch in the *call*, not
an inspection defect — the union does not contain the hash. Leaving it reported is
correct; what it needs is not a fix but a look at the call sites.

**`expected 'Storage::Backend', got 'Set(String)'` (5)**
`Backend` is a module that a class includes; argument-to-module inclusion is not
tracked. Reporting it is a false positive in principle, but suppressing it needs
subtype knowledge the plugin deliberately does not maintain for modules (same
reasoning as `Enumerable`/`Indexable` in `acceptsAnyType`). Out of scope until
module inclusion is modelled.

**An array literal's element types are not checked**
Not in this export — found while writing the regression tests. `H.build [1, 2]`
against `items : Array(String)` is not reported, while assigning first
(`xs = [1, 2]; H.build xs`) is. So the literal's element type is not inferred at
the call site; the variable case already works. Tracked as follow-up rather than
folded into this triage, because fixing it means inferring element types of a
literal, which touches the same resolver as the return-type work.

**Unused-variable findings (58)**
Spot-checked only; each needs its own file to judge. No pattern that suggests a
systematic false positive.

**Argument-count findings (89)**
The largest groups are `Missing required argument(s): 'json'` / `'input'` /
`'content'` and `Unknown named argument 'return_type'` / `'value'`, which look like
framework DSL methods whose parameters are macro-generated. `param`-macro
constructors are already modelled; a method generating its signature at macro
expansion time is not. These need per-case verification before being called
defects.
