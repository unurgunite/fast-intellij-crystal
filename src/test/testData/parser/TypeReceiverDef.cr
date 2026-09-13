# Method definitions with an explicit type receiver (Type.name).
# Regression test: the bare CONSTANT alternative of method_name used to shadow
# the longer type_path DOT alternative (PEG first-match), so `def Float64.new!`
# parsed as a method named `Float64` and the rest (`(value) : Float64`) broke.
struct Float64
  def Float64.new(value)
    value.to_f64
  end

  def Float64.new!(value) : Float64
    value.to_f64!
  end

  def Float64.parse(str : String) : self
    str.to_f64
  end
end

class Foo::Bar
  def Foo::Bar.baz(x : Int32) : String
    x.to_s
  end
end

# Bare uppercase method name (no receiver) must keep working.
def Foo
  42
end

# Self receiver unaffected.
class Baz
  def self.qux : Nil
  end
end
