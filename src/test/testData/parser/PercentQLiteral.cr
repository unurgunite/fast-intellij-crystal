# `%q` is backslash-LITERAL (single-quote-like): `\` never escapes the closer.
# Verified against real crystal 1.21.0: `%q(\))` == literal `\` then close,
# while every other percent kind processes backslash escapes.

# win32/file.cr shape: escaped chars inside a %q call argument
if name.starts_with?(%q(\??\)) && name[5]? == ':'
  1
end

# plain forms still work
a = %q(abc)
b = %q(a(b)c)
c = %(a#{1}b)
d = %Q(a\nb)
e = %w(a b\nc)
