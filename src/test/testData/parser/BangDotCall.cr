# Trailing ! as zero-arg DOT method call (verified: x.! valid, x.? invalid)

puts ENV["T"]?.try(&.empty?.!)

x = "a"
puts x.empty?.!

puts true.!
