# Qualified type names (struct/class/module with :: path)

struct HTTP::Headers
  record Key, name : String do
    def hash(hasher)
      name.each_byte do |c|
        hasher = normalize_byte(c).hash(hasher)
      end
      hasher
    end
  end
end

class Foo::Bar::Baz
  def qux
    1
  end
end

module A::B
  def self.helper
    2
  end
end
