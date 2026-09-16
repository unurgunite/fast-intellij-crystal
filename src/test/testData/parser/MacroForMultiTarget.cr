# `{% for a, b in ... %}` multi-target loops (verified legal, real compiler)

{% for n, m in items %}
1
{% end %}

{% for n in ["a"] %}
foo({{ n }})
{% end %}
