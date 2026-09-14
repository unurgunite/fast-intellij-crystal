# Grammar coverage for `record` definitions with a `do` body, nested `def`,
# `private def` (http/headers.cr shape — the do-body is a class_body, not a block),
# and `record` without a body.

record EntryMatch, pattern : String do
  def matches?(string) : Bool
    true
  end

  private def normalize(pattern) : String
    pattern
  end
end

record RecursiveDirectories

# Bodyless records inside a module must not eat the module's `end`
# (http/common.cr shape: `record EndOfRequest` inside `module HTTP`).
# Verified: a DO-less indented `def` after a record is a separate method,
# not a record member (`R.new(5).foo` → "undefined method 'foo' for R").
module HttpLike
  record EndOfRequest

  record HeaderLine, name : String, value : String, bytesize : Int32

  record Key, name : String do
    def hash(hasher)
      hasher
    end
  end
end

x = foo do
  def bar
    1
  end
end
