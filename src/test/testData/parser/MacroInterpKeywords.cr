# Keywords inside `{{ }}` / `#{}` (macros.cr `record` `copy_with`
# `{{ ... if ... else ... end.splat }}`, खान `if` in string interpolation).
macro foo(properties)
  def copy_with({{
                  properties.map do |property|
                    if property.is_a?(Assign)
                      x
                    else
                      y
                    end
                  end.splat
                }})
  end
end

def version_string(number)
  "#{if number > 0
    "pos"
  else
    "neg"
  end}"
end
