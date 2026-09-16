# Escaped `\{%` / `\{{` inside macro bodies (big_int.cr, llvm.cr,
# ecr/macros.cr): the backslash suppresses expansion, content stays text.
macro foo(n)
  def to_i{{n}} : Int{{n}}
    bar
    \{% if x %}
    baz
    \{% end %}
    qux
  end
end

macro embed(filename, io_name)
  \{{ run("ecr/process", {{filename}}) }}
end
