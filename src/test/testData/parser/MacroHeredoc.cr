# Heredoc inside `{% %}` (macros.cr `record`): the opener line stays macro
# code, the following lines are raw heredoc body until the end marker.
macro record(__name name, *properties, **kwargs)
  {% raise <<-TXT unless kwargs.empty?
    macro `record` does not accept named arguments
      record #{name}, #{properties.join(", ").id}
    TXT
  %}
  foo
end
