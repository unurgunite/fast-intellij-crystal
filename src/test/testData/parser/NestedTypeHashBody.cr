# Hash literal as the entire body of a method inside a doubly-nested type.
module Mod
  class S
    def f(x : Array(String))
    {
      "a" => "1",
    }
    end
  end
end
