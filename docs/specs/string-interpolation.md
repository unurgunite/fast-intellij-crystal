# String Interpolation

`#{expression}` inside string-like literals lexes as
`STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END` and enters
the shared `INTERPOLATION` lexer state (brace-depth tracked; pops back to the
parent state at depth 0, so nesting works). The state is parent-agnostic:
identical whether entered from a string, heredoc, percent literal, regex, or
backtick body.

## Support matrix

| Literal | Example | Interpolation? |
|---|---|---|
| Double-quoted string | `"hello #{name}"` | Yes |
| Interpolating symbol | `:"hello #{name}"` | Yes |
| Non-raw heredoc | `<<-ID ... #{name} ... ID` | Yes |
| Raw heredoc (`<<-'ID'`) | `<<-'ID' ... #{name} ... ID` | No (correct — raw) |
| `%(...)`, `%Q(...)` | `%(hello #{name})` | Yes |
| `%r(...)`, `%x(...)` | `%r(p #{name})`, `%x(echo #{name})` | Yes |
| `%q(...)`, `%w(...)`, `%i(...)` | `%q(no #{x})` | No (correct — non-interpolating by design) |
| Regex `/.../` | `/hello #{x}/` | Yes |
| Backtick command `` `...` `` | `` `echo #{x}` `` | Yes |
| Macro bodies | `{% ... %}`, `{{ ... }}` regions | No `#{}` (macros use `{{ }}` / `{% %}`) |

`%W(...)` / `%I(...)` do not exist in Crystal (syntax errors) — not supported.

## Edge cases

- **Escaped `\#{`** produces literal `#{` without interpolation in
  interpolating literals.
- **Nesting** (`"hello #{"#{x}"}"`) works via the depth counter.
- **`#{}` inside non-interpolating percent literals** (`%q(#{x})`, `%w(#{x})`)
  stays literal text; the interpolation rule fires only for `%`, `%Q`, `%r`,
  `%x`.
- **Braces vs interpolation in `%({...})`**: `#{` is consumed as one unit
  before brace-depth counting, so `%({hello #{x}})` balances.
- **Multiline** percent literals interpolate identically across lines.
- **Escapes** (`\n`, `\d`) inside `%x`/`%r` lex as `STRING_ESCAPE`; content
  inside an interpolation region is lexed by the `INTERPOLATION` state.
