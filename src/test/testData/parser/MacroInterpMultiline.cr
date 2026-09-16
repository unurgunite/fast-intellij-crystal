# `{{` / `}}` on their own lines (enumerable.cr `Reflect(X)`).
macro foo
  def self.type
    {% if X.union? %}
      {{
        raise("boom")
      }}
    {% else %}
      X
    {% end %}
  end
end
