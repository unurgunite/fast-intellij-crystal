# Macro-suffixed method segments (`LibLLVM.build_{{name}}2(...)` shape,
# verified legal inside macro for-loops)

class Builder
  {% for m in ["a"] %}
  def run
    Value.new LibLLVM.build_{{ m }}2(self, 1)
  end
  {% end %}
end
