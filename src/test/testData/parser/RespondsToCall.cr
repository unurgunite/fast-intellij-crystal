# `responds_to?` as a plain call (verified legal)

def check(x)
  x = responds_to?(:infinite?)
  y = obj.responds_to?(:foo)
  x
end
