# `@{{ivar}}` interpolation and `other.@{{ivar}}` access (struct.cr shape,
# verified legal inside macro for-loops)

struct Foo
  def ==(other) : Bool
    {% for ivar in @type.instance_vars %}
    return false unless @{{ ivar.id }} == other.@{{ ivar.id }}
    {% end %}
    true
  end
end
