# Macro-generated enum members and `fun` names (verified legal in macro context)

enum OpCode : UInt16
  {% for n in ["a"] %}
  {{ n.id.upcase }} = {{ 1 }}
  {% end %}
end

lib LibX
  fun {{ "foo".id }}(x : Int32) : Int32
end
