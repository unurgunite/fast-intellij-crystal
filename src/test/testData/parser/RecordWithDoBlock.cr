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

x = foo do
  def bar
    1
  end
end
