# Record fields named with keywords (valid Crystal, verified with crystal eval)

record ZoneTransition, when : Int64, index : UInt8, standard : Bool, utc : Bool do
  getter? standard, utc
end

record TypeTemplate, type : String do
end

record Simple, name : String, value : Int32
