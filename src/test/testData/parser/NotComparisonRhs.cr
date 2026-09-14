# `!=`/`==` with `!`/`!!` operands (verified: all legal, real compiler)

def check(a, b)
  if !!a != !!b
    1
  end
end

def same(a, b)
  a == !b
end

def other(a, b)
  a != !!b
end
