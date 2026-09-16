# Macro-splat parameters (`{{...}}` whole param, `{{...}}*` splat tail —
# macros.cr `record` `initialize`/`copy_with`, interpreter/compiler.cr).
macro foo(properties)
  def initialize({{ properties.map do |field| x end.splat }})
  end
end

macro bar
  def {{name.id}}(
    {{operands.splat(", ")}}*, node : ASTNode?
  ) : Nil
    foo(node)
  end
end
