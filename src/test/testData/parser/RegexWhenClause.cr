# Regex literals and related patterns in when clauses (verified with crystal run)

def m(t)
  case t
  when /a|b/
    1
  else
    2
  end
end

def n(t)
  case t
  when 1, /a|b/
    1
  end
end

def d(a, b)
  case a
  when a / b
    1
  end
end

def w(x)
  case x
  when a = f(x)
    a
  end
end
