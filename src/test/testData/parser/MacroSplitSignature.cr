# Macro-split def signature (`indexable/mutable.cr` `map!`): each `{% %}`
# branch carries a complete variant header, one shared body + `end`.
module Indexable
  {% begin %}
  {% if compare_versions(Crystal::VERSION, "1.1.1") >= 0 %}
  def map!(& : T -> _) : self
  {% else %}
  def map!(&)
  {% end %}
    each_index do |i|
      unsafe_put(i, yield unsafe_fetch(i))
    end
    self
  end
  {% end %}
end
