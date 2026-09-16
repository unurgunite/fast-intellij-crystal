# Specs

Living behavioral specs: what the plugin **does** (verified against crystal
1.21.0 where noted). A spec exists per feature area — create one when adding a
feature, update it when behavior changes.

| Spec | Area |
|---|---|
| [completion.md](completion.md) | Completion triggers, scope priorities, DOT/namespace/annotation/class-body/override |
| [hover-popovers.md](hover-popovers.md) | Hover popup formats, doc links, resolution priority |
| [type-inference.md](type-inference.md) | Implemented inference vs roadmap |
| [unused-variable.md](unused-variable.md) | "Value assigned never used" inspection algorithm |
| [exception-handling.md](exception-handling.md) | `begin/rescue/ensure` grammar |
| [bare-call-arguments.md](bare-call-arguments.md) | Paren-free call arguments, leading-`::` invariant |
| [keywords-as-names.md](keywords-as-names.md) | Keywords in name positions, `!WHEN` guard |
| [string-interpolation.md](string-interpolation.md) | `#{}` support matrix per literal |
| [block-highlighting.md](block-highlighting.md) | Keyword block highlighting |
| [find-usages.md](find-usages.md) | Find Usages / Rename from definition names |

Elsewhere: testing conventions → [../TESTING.md](../TESTING.md);
point-in-time grammar reports → [../reports/](../reports/);
unimplemented designs → [../proposals/](../proposals/);
one-off decisions → [../decision-log/](../decision-log/).
