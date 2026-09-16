# Macro controls inside comma lists (cache_dir.cr `{% begin %} [ ...
# {% if %} ... {% end %} ... ] {% end %}`, enumerable.cr `zip?` tuples):
# controls are transparent to comma rhythm; `yield({...})` takes parens.
def f
  candidates = {% begin %}
    [
      ENV["A"]?,
      {% if flag?(:windows) %}
        ENV["B"]?,
      {% end %}
      ".crystal",
    ]
  {% end %}
  foo(candidates)
end

def g(elem, others)
  yield({
    elem,
    {% for t in U %}
      x{{t}},
    {% end %}
  })
end
