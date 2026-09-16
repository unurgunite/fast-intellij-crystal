# Bare-call arguments

Behavioral spec for parenthesis-free (bare) call arguments: `foo a, b`,
`getsockopt optname, 0, level: ::Socket::Protocol::TCP`.

## Named-arg values (`named_bare_value`)

`named_bare_argument ::= bare_arg_name COLON named_bare_value [ASSIGN bare_expression]`.

- `named_bare_value ::= namespace_access bare_postfix_op* | bare_expression`.
- The `namespace_access` alternative exists because the bare-expression tower
  has no leading-`::` primary, so `level: ::Socket::Protocol::TCP` otherwise
  errors. Chains (`::A::B::C`) ride `bare_postfix_op*` (which includes
  `namespace_access`), producing flat `NAMESPACE_ACCESS` siblings — same shape
  as `postfix_expression` builds in expression context.
- The `COLON` anchor precedes the value, so this alternative cannot steal
  `CONSTANT::...` tails from expression statements.

## Invariant: no positional leading-`::` bare alternative

A trailing `bare_argument` alternative for `::`-led values
(`bare_namespace_value ::= namespace_access [dot_call_access]`) was tried and
reverted: after the CONSTANT callee, the `::...` remainder suddenly parsed as
a bare argument, so `bare_command_expression` stole
`NamespaceAccess::Inner.inner_method`, `@buffer = IO::Memory.new`, and
`"#{Foo::Bar.method}"` (all regressed from expression to BARE_COMMAND shape).
Positional leading-`::` values stay unsupported by design; only the named
(`name: ::X`) position accepts them.

Golden test: `BareNamespaceValue.cr/.txt` (`testBareNamespaceValue`).
