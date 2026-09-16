# `out` as assignment target and receiver (slice/sort.cr shapes).
# Verified legal with real crystal 1.21.0. Bare `puts out` stays a syntax
# error there (accepted here as harmless union — unobserved in stdlib).

def merge(v, size)
  out = v.to_unsafe
  out.value = v[0]
  out.copy_from(v, size)
  out += 1
  out
end
