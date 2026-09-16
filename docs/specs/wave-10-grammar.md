# Wave 10 grammar fixes

Stdlib parse errors 85 → 2 files (2172 files, crystal 1.21.0 as oracle;
remaining `spec/helpers/string.cr` + `syntax/parser.cr` are EOF-at-length
artifacts — error offset == file length, repro minis pass; proven
pre-existing by parsing both files on the pre-wave-10 HEAD).

## INTERPOLATION operators `**` `<<` `>>` `//`

xml.cr `class_getter libxml2_version` version string:
`"#{number // 10_000}.#{number % 10_000 // 100}.#{number % 100}"`.
The INTERPOLATION lexer state had `*`/`/` but not the doubled forms, so
`//` split into two SLASH (division) and the `}` dangled. Added
`**`/`<<`/`>>`/`//` (longest-match keeps them disjoint from `*`/`<`/`>`/`/`;
MACRO_INTERPOLATION already had them — project rule: states mirror).
Golden: InterpolationOperators.

## Macro-split def signatures (`method_variant_header`)

indexable/mutable.cr `map!`: `{% if v >= 0 %} def map!(& : T -> _) : self
{% else %} def map!(&) {% end %}` + ONE shared body + `end` — each `{% %}`
branch carries a complete variant header, only one survives expansion.
`method_definition` takes `(NLS method_variant_header)*` after the main
header; the final `{% end %}` stays in method_body's macro-interleave path.
Public wrapper rule (own PSI node) so method_definition's
parameter_list/type_reference getters stay single — inlining a second pair
flips them to List getters and breaks 30+ Kotlin call sites (verified).
REQUIRED DEF (not optional — a body-opening `{% if %}` must stay body
content); NLS between control and DEF is load-bearing (NEWLINE is a real
token); no guard, no pin (`&`/`!` before the composite only inspects its
first-child set and misses the branch token; nested-`_AND_` frames are
unreliable in this runtime — both verified by experiment). Golden:
MacroSplitSignature.

## `do`/`end`/keywords/NEWLINE in MACRO_INTERPOLATION + INTERPOLATION

- `do`/`end`/`NEWLINE` tokens in MACRO_INTERPOLATION: `{{ x.map do |f| ...
  end.splat }}` (macros.cr `record` `initialize`/`copy_with`) — the block
  attaches to the call INSIDE the interpolation (`end.splat` tails prove
  it). No MACRO_BODY-style lookaheads (pushState state, no depth tracking).
  A macroNestingLevel counter was tried and REVERTED: it broke
  `\{% if Int{{n}} == X %}` (the `{{n}}` bump made `}}` return CONTENT
  without popping; `%}` lexed as PERCENT+RBRACE; whole-file drift).
  Nesting needs no counter — the pushState/popState stack pairs every `{{`
  with its own `}}`.
- `do`/`end`/`if`/`elsif`/`else`/`unless`/`while`/`until`/`begin`/`ensure`/
  `rescue`/`case`/`when`/`in`/`then`/`return`/`break`/`next`/`yield`/`self`/
  `true`/`false`/`nil`/`super`/`previous_def`/`with`/`typeof`/`sizeof`/
  `instance_sizeof`/`pointerof`/`offsetof`/`uninitialized`/`asm` keywords in
  INTERPOLATION + MACRO_INTERPOLATION: `"#{if a\n b\n end}"`, `{{ if ...
  else ... end }}`. Without them `if` lexes as IDENTIFIER and `end`
  dangles. Placed before {IDENTIFIER} (same-length tie-break: first rule
  wins); safe for `foo.if` (dot_call_access keyword_as_method) and `|when|`
  params (param_name keyword_as_record_field). Also NLS inside
  macro_interpolation (enumerable.cr `Reflect(X)` puts `{{`/`}}` on own
  lines; NLS is private — inlines, no shape change when absent).
- `{{...}}*` splat-after-interpolation parameter
  (`{{operands.splat(", ")}}*, node : ASTNode?` — interpreter/compiler.cr):
  `parameter ::= ... | macro_interpolation [STAR]` (the `*` applies to the
  generated list, never a bare STAR param which has no `{{`).
- Multi-line `{{ x.map do ... end.splat }}` inside `def f(...)` params is
  ILLEGAL in real crystal (`unexpected token "{{"`) but macro bodies are
  TEXT — tolerated via `macro_definition_open` fallback (below) instead of
  param grammar.
Goldens: MacroSplatParam, MacroInterpMultiline, MacroInterpKeywords.

## Heredoc inside `{% %}` (flat tokens)

`{% raise <<-TXT unless kwargs.empty? ... TXT %}` (macros.cr `record`):
lexer `<<-ID` rules + `macroHeredocPending` flag + MACRO_HEREDOC_BODY state
(mirrors HEREDOC_BODY, pops to MACRO_CONTROL). Parser takes the tokens FLAT
in macro_control_content (no structured START..END rule — the opener line
carries code between start and body: `raise <<-TXT unless x.empty?`).
Golden: MacroHeredoc.

## Escaped `\{%` / `\{{` (BACKSLASH token + body-text rules)

big_int.cr, llvm.cr, ecr/macros.cr nest `\{% if %}` / `\{{ run(...) }}` with
`{{n}}` inside. The `\` emits BACKSLASH + pushback (delimiter re-lexes as
code); BNF pairs them as opaque body text (`macro_body_element` inside
macro bodies; NEW `escaped_macro_statement` at statement level — real
crystal rejects `\{%` at true statement level, but `{% for %}` bodies parse
as sibling statements so the escaped controls surface there). Same-scope
restriction learned the hard way: an earlier revision emitted `{{` inside
MACRO_CONTROL without pushing a state and let `}}` return CONTENT — the
state stuck in the wrong lexer state (`%}` → PERCENT+RBRACE) and drifted
whole files including the previously-fixed `\{{ run(...) }}` shape.
Escaped-`{{` inside MACRO_INTERPOLATION code (`\{% if Int{{n}} == X %}`) is
the one residual: `{{n}}` there lexes as BEGIN+n+CONTENT(`}}`) and the
soup rule `macro_escaped_interpolation` covers only the common cases.
Goldens: EscapedMacroDelimiters.

## Comma-transparent macro controls in lists

`{% begin %} [ ... {% if %} ... {% end %} ... ] {% end %}` (cache_dir.cr),
`yield({elem, {% for %} ...})` (enumerable.cr `zip?`): expression_list takes
`macro_control` items that are TRANSPARENT to comma rhythm — after a control
an element may follow without a comma (the comma belongs to generated code)
and `{% end %}` may sit bare before `]`/`)`. The control branch owns its
leading comma AND the plain branch carries `!MACRO_CONTROL_BEGIN` after its
comma (`expression` itself matches bare `{%...%}` via primary, so without
the token guard the plain branch still eats `, {% if %}` first — two rounds
of the same bug, both verified). Flat, macro_control LAST in items (zero
shape change when macro-free). `yield(...)` paren form rides
argument_list_macro. Golden: ExprListMacroControls.

## `macro_array_tail`: expression before raw tokens

`[{% for %} ENV["B"], {% end %}]`: the flat tail tried macro_control_token
(CONSTANT/IDENTIFIER) before `expression`, so `ENV` matched as a token and
`["B"]` stranded the loop's `!RBRACKET` guard (`got ','`). `expression`
now precedes `macro_control_token` (maximal munch first; failure backtracks
to the choice point). Golden: MacroArrayExprFirst.

## Multi-assign postfix modifier

`start, finish = finish, start if finish < start` (syntax/location.cr
`self.lines`): multi_assignment never took `[postfix_modifier]` (single
assignment always had it). One-token fix. Golden: MultiAssignPostfix.

## Unclosed-macro fallback (`macro_definition_open`)

Macro TEXT with generated-code fragments (`def f({{...}})` params —
macros.cr `record`, illegal in real crystal but legal body text) never
flips back to YYINITIAL, so no `end` token closes the macro. Fallback rule
(header + body, no END) AFTER the closed alternative; same stub contract
(factory maps MACRO_DEFINITION_OPEN to the macro stub holder; visitor
delegates to visitMacroDefinition) so the index still sees the macro.

## Reverted experiments (do not retry)

- macroNestingLevel counter in MACRO_INTERPOLATION (see above).
- `&`/`!` lookahead guards on method_variant_header (only inspect the
  composite's first-child set; nested `_AND_` frames unreliable here).
- Arming afterDef/macroHeaderSeen for MACRO_BODY `def`-text (flips out of
  MACRO_BODY on the signature NEWLINE, orphans def text, silent whole-file
  drift — params in macro bodies are TEXT, not parsed params).
- `{{`-same-line guard on the MACRO_BODY `def` depth counter (red herring:
  6→19 files; position of `{{` does not decide the pairing).
- Structured macro_heredoc START..END rule (no contiguity — code sits
  between opener and body).
- Extra `LPAREN argument_list RPAREN` arm on yield_statement (duplicate
  flat+wrapper getArgumentList that breaks javac — the flat
  argument_list_macro arm already covers paren yields).

Goldens (all 0 PsiErrorElement): MultiAssignPostfix, MacroSplitSignature,
InterpolationOperators, MacroHeredoc, EscapedMacroDelimiters,
ExprListMacroControls, MacroArrayExprFirst, MacroInterpMultiline,
MacroInterpKeywords, MacroSplatParam.
