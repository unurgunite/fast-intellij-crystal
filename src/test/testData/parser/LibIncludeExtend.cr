# `include` / `extend` inside `lib` blocks (xml/libxml2.cr shape, verified legal)

lib LibX
  struct Doc
    include NodeCommon
    extend NodeCommon
  end
end
