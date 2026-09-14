# Proc literal with do-block body (LibGC.set_start_callback shape)

LibGC.set_start_callback -> do
  GC.lock_write
end

f = ->(x : Int32) do
  x * 2
end

g = -> do
  puts "hello"
end
