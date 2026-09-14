# Operator and special method names (verified with the real compiler):
# `def !~` (object.cr), ``def ` `` backtick name (process.cr),
# `def annotation` (annotatable.cr), wrapping `def &{{op}}` (primitives.cr).

class OperatorNames
  def !~(other)
    !(self =~ other)
  end

  def annotation(annotation_type : String) : String?
    annotation_type
  end

  def `(command : String) : String
    command
  end

  {% for op in %w(+ -) %}
    def &{{op.id}}(other : Int32) : self
      self
    end
  {% end %}
end
