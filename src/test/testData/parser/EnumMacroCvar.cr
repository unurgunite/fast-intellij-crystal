# Enum body with macro control and class variables (llvm/enums.cr shape)

enum Attribute : UInt8
  Foo
  Bar

  # NOTE: enum body does not allow class_getter, hence the nil cast
  @@kind_ids = nil.as(Hash(Attribute, UInt32)?)

  protected def self.kind_ids
    @@kind_ids ||= load_kinds
  end
end

class Socket
  enum Protocol
    IP = 1
    {% if flag?(:win32) %}
      TCP = 2
    {% else %}
      TCP = 3
    {% end %}
  end
end
