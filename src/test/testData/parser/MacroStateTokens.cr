# Full INTEGER/FLOAT and comparison/bitwise tokens inside macro states.
# Verified legal with real crystal 1.21.0 (compiler_rt, enum.cr, class.cr shapes).

{% x = s.gsub(/^a/, "b").to_i %}

{% if m.id.ends_with?('=') && !y.includes?(m.id.stringify) %}
1
{% end %}

{% api_version = v ? v.gsub(/^a/, "").to_i : d %}

class Foo
  def _lte(other : T.class) forall T
    {{ @type <= T }}
  end
end

def pow5(e)
  small_step = {{ Limb == UInt64 ? 27_u32 : 13_u32 }}
  big = {{ 1.5 + 0xFF }}
  small_step
end

# Percent literals inside macro states (hexfloat.cr shape).
{% sep = %( or ) %}

macro check_sep(ch)
  return yield "expected {{ ch.map(&.stringify).join(%( or )).id }}"
end
