# Assignment inside {% for %} macro control

{% for mod in %w(LittleEndian BigEndian) %}
  module {{mod.id}}
    {% for type, i in %w(Int8 UInt8 Int16 UInt16) %}
      {% bytesize = 2 ** (i // 2) %}

      def self.encode(int : {{type.id}}, io : IO)
        buffer = int.unsafe_as(StaticArray(UInt8, {{bytesize}}))
        buffer.reverse! unless SystemEndian == self
      end
    {% end %}
  end
{% end %}
