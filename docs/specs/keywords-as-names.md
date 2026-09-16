# Keywords as names

The real compiler (verified 1.21.0) accepts reserved keywords in name positions
that a naive lexer-keyword grammar rejects. Our lexer emits keyword tokens
(`OF`, `WHEN`, `FOR`, `ANNOTATION`, ...), so each name position below needed an
explicit keyword alternative.

## Positions

- **Call labels** (`named_argument`, parenthesized): `exec_stdio_to_fd(input, for: STDIN)`
  (process.cr). `named_argument ::= (IDENTIFIER | keyword_as_record_field) COLON expression`.
  Mirrors `bare_arg_name`, which already accepted `keyword_as_record_field`.
- **Parameter names** (`param_name ::= IDENTIFIER | keyword_as_record_field`):
  `def self.map(values, of = nil, &)` (syntax/ast.cr), `def f(for dst_io : IO)`
  (process.cr), `node.whens.each do |when|` block params (type_guess_visitor.cr).
  Declaration positions (COLON/ASSIGN/COMMA/RPAREN follow), so the keyword
  alternative fails fast elsewhere.
- **Locals / condition targets** (`variable` + `variable_reference` accept `OF`/`WHEN`):
  `if of = node.of` (syntax/to_s.cr), `puts when` / `when.body` bodies.
- **`def annotation`** (annotatable.cr): `ANNOTATION` added to `keyword_as_method`
  (it is the only definition-keyword missing there).

## Invariant: `!WHEN` guard on `statement`

`variable_reference` accepting `WHEN` lets a statement_list swallow the next
`when` clause as an expression statement: `when 0 then @x` broke with
`got 'then'`, and multi-`when` case/select collapsed into one clause.
`statement` therefore starts with a `!WHEN` negative lookahead — `when` can
never start a statement (it is always a clause keyword there). Golden:
multi-`when` case is part of `KeywordAsName.cr`.

## Operator / special method names (`OperatorMethodNames.cr`)

- `def !~` (object.cr): `BANG TILDE` added to `operator_method_name`.
- ``def ` `` (process.cr): backtick name. The lexer emits `COMMAND_BEGIN`
  without pushing the `BACKTICK` state when `afterDef` is set (mirrors the
  `def %` rule); `method_name` gains a `COMMAND_BEGIN` alternative.
- `def &{{op.id}}` (primitives.cr): `AMPERSAND macro_interpolation` added to
  `operator_method_name`; `operator_method_name` precedes `keyword_as_method`
  in `method_name` (PEG longer-first: bare `&` matched as keyword first and
  left `{{...}}` dangling).

Goldens: `KeywordAsName.cr/.txt` (`testKeywordAsName`),
`OperatorMethodNames.cr/.txt` (`testOperatorMethodNames`).
