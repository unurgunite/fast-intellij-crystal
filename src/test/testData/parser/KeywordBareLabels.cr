# Keywords as bare-call labels (verified with crystal run: foo def: a_def works)

foo def: a_def, in_macro: false

MacroInterpreter.new self, scope, node.location, def: a_def, in_macro: false
