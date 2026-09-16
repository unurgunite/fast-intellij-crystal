# Shorthand `&.` with keyword methods (verified: all legal, real compiler)

def prev_of(a_def)
  a_def.previous.try(&.def)
end

def names(list)
  list.map(&.def)
end

def range_end(transitions, tx_index)
  transitions[tx_index + 1]?.try(&.when) || 0
end

def anno(cur, prog)
  cur.try(&.annotation(prog))
end
