# `union` and `type` as identifiers (verified: all legal, real compiler)

def kinds(u)
  u.union_types
end

def describe(node)
  type = node.type
  type
end

union patterns, other
