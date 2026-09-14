# Macro interpolation with braces/ASSIGN/`{}`-nesting/`?`-suffix tokens
# (verified: all legal inside macro interpolation, real compiler)

x = {{ {a: 1} }}

y = {{ (t = 1) ? t : 2 }}

def empty_check(list)
  {{ list.empty? }}
end

{% x = s.gsub(/a/, "b") %}

def stringify_of(v)
  {{ v.stringify }}
end
