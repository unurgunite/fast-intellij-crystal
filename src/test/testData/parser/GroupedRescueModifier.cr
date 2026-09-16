# `rescue` modifier inside grouped expressions (link.cr shape, verified legal)

status = (run || nil rescue nil)

x = (f rescue nil)
