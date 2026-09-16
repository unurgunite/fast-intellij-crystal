# Anonymous `out : Type` lib-fun parameter (xml/libxml2.cr shape).
# Verified legal with real crystal 1.21.0 (`def f(out : T)` is rejected there
# but tolerated here as grammar union).

lib LibX
  fun xmlNewTextWriter(out : OutputBuffer*) : TextWriter
  fun plain(x : Int32) : Int32
end
