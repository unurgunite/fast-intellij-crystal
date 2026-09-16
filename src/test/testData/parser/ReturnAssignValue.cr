# `return`/`break`/`next` with assignment values (verified legal)

def fetch(x)
  return @last = x
end

def take(x)
  break @found = x
end

def skip(x)
  next @item = x
end
