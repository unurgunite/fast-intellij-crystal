# Macro-generated `->()` proc parameters (proc.cr #partial shape).
# Verified legal with real crystal 1.21.0. `{% %}` in `def` params is a real
# syntax error, tolerated here as grammar union (same precedent as `fun {{}}`).

def partial(*args : *U) forall U
  x = ->(
    {% for i in 0...2 %}
    arg{{i}} : Int32,
    {% end %}
  ) {
    1
  }
  x
end

add = ->(x : Int32, y : Int32) { x + y }
