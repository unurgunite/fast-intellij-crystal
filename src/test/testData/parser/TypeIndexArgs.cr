# Static/named type indexes (verified: all legal, real compiler)

def f16 : UInt8[16]
  x
end

def fmax : UInt8[LibC::MAX_PATH]
  x
end

def fnamed : NamedTuple(time: Time, location: Location)
  x
end
