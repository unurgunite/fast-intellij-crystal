# Method bodies with rescue chains, else and ensure (verified with crystal run)

def m1
  1
rescue A
  2
rescue B
  3
end

def m2
  1
else
  2
end

def m3
  1
rescue A
  2
rescue B
  3
else
  4
ensure
  5
end

begin
  1
rescue A | B
  2
end
