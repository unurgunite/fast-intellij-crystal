# `**`, `<<`, `>>`, `//` inside string interpolation (xml.cr
# `class_getter libxml2_version` version string).
def version_string(number)
  "#{number // 10_000}.#{number % 10_000 // 100}.#{number % 100}"
end

def shifted(a, b)
  "#{a << 2} #{a >> 2} #{2 ** 3}"
end
