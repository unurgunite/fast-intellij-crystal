# Macro array tail prefers full expressions over raw tokens
# (cache_dir.cr `ENV["B"]` inside `[{% for %} ...]`).
def f
  x = [{% for t in U %} ENV["B"], {% end %}]
  foo(x)
end
