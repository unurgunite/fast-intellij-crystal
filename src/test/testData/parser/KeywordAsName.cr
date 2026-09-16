# Keywords as names (verified with the real compiler, crystal 1.21.0):
# keyword labels in calls, `of`/`for` as parameter names, `of` as a local
# (condition assignment), `|when|` block params referenced in the body.

foo(x, for: STDIN)

foo(x, in: 1, of: 2, type: 3, select: 4)

class KeywordNames
  def self.map(values, of = nil, &)
    of
  end

  def self.exec(input, for dst_io : Int32)
    dst_io
  end

  def describe(node)
    if of = node.of
      of
    end
  end

  def guess(node)
    node.whens.each do |when|
      puts when
      guess(when.body)
    end
  end
end

# Multi-when case keeps separate clauses even with `|when|`-shaped bodies
# (regression: `when` as variable_reference must not swallow the next clause).
case tag
when 0 then @x
when 1 then @y
else @z
end
