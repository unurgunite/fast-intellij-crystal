# Trailing comma with newline before the closing bracket (verified with crystal run)

def foo(a : Int32,
    b : Int32 = 2) : Int32
  a + b
end

puts foo(
  1,
)

h = {
  "a" => 1,
}

x = [
  1,
]
