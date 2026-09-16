# `private`/`protected` alias (macro_code_coverage.cr shape).
# Verified legal with real crystal 1.21.0.

class Coverage
  private alias NodeTuple = {Int32, Int32}
  protected alias Other = String
end
