# Bodiless abstract defs incl. visibility-prefixed (socket.cr shape)

abstract class Base
  abstract def foo : Int32

  private abstract def bar : Int32

  def baz
    1
  end
end

class Socket
  private abstract def system_close : Nil
end
