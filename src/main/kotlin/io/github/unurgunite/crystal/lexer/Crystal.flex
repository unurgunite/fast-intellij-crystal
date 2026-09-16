package io.github.unurgunite.crystal.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import io.github.unurgunite.crystal.psi.CrystalTypes;
import com.intellij.psi.TokenType;

%%

%class CrystalLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
  // Lexer instance state below MUST be reset in `resetState()` — IntelliJ's
  // FlexAdapter reuses ONE lexer instance across files AND across incremental
  // re-lexes, and JFlex's zzResetReader does not touch user fields. Stale
  // fields were a real bug source: `lastSignificantToken` leaked across files
  // (regex-vs-division misfire at file start) and `stateStack`/`depthStack`
  // leaked across re-lexes. CrystalLexerAdapter.start() calls
  // flex.resetState() explicitly on fresh lexes — see the adapter.
  public void resetState() {
    interpolationDepth = 0;
    percentDepth = 0;
    percentOpenChar = 0;
    percentCloseChar = 0;
    percentTokenType = null;
    percentInterpolation = false;
    percentNoEscape = false;
    afterDef = false;
    lastSignificantToken = null;
    stateStack.clear();
    depthStack.clear();
    heredocId = "";
    heredocIndented = false;
    heredocRaw = false;
    macroHeaderSeen = false;
    macroParenDepth = 0;
    macroBodyDepth = 0;
    macroBodyAtLineStart = false;
    macroBodyBuffer.setLength(0);
    macroHeredocPending = false;
  }

  private int interpolationDepth = 0;
  private int percentDepth = 0;
  private char percentOpenChar = 0;
  private char percentCloseChar = 0;
  private IElementType percentTokenType = null;
  private boolean percentInterpolation = false;
  // `%q` is single-quote-like: backslash does NOT escape the delimiter
  // (`%q(\))` = literal `\` then close — verified with real crystal 1.21.0;
  // every other percent kind (`%()`, `%Q`, `%w`, `%i`, `%r`, `%x`) processes
  // backslash escapes). Set in the `%q` rule, cleared in all other `%` rules.
  private boolean percentNoEscape = false;
  // After `def`/`macro` the next `%` is the modulo operator (e.g. `def %(other)`),
  // not a percent literal (`%(...)`). Set on DEF/MACRO, cleared on signature terminators
  // (LPAREN/COLON/ASSIGN/SEMICOLON/NEWLINE/END) or when the `%` is lexed.
  private boolean afterDef = false;
  // Last significant token type, for regex-vs-division disambiguation (`/` after
  // WHEN/COMMA/LPAREN/operator/keyword starts a regex; after a value it divides).
  // Updated on every returned token except whitespace/comments.
  private IElementType lastSignificantToken = null;

  // Records the token for isRegexAllowed(); whitespace/comments pass through.
  // NEWLINE/SEMICOLON end a statement, so the next `/` starts operand position.
  private IElementType track(IElementType type) {
    if (type == TokenType.WHITE_SPACE || type == CrystalTypes.LINE_COMMENT) {
      return type;
    }
    if (type == CrystalTypes.NEWLINE || type == CrystalTypes.SEMICOLON) {
      lastSignificantToken = null;
      return type;
    }
    lastSignificantToken = type;
    return type;
  }
  private String heredocId = "";
  private boolean heredocIndented = false;
  private boolean heredocRaw = false;

  // State stack for nested string interpolation
  private final java.util.ArrayDeque<Integer> stateStack = new java.util.ArrayDeque<>();
  private final java.util.ArrayDeque<Integer> depthStack = new java.util.ArrayDeque<>();

  // Regex disambiguation: regex literals can only start in operator position.
  // Token-based (not char-based): after WHEN/COMMA/LPAREN/operators/keywords a
  // `/` starts a regex (`when /re/`, `foo(/re/)`); after a value (identifier,
  // literal, `)`, `]`, string end) it divides (`x / 2`). Character scanning
  // misfires on `when` (ends in letter `n`) and on `//` comments.
  private boolean isRegexAllowed() {
    if (lastSignificantToken == null) return true; // start of file/statement
    IElementType t = lastSignificantToken;
    // After DEF the `/` is the operator method name (`def /(...)`), never a regex.
    // After a value (identifier, literal, `)`, `]`, string end) it divides (`x / 2`).
    if (t == CrystalTypes.DEF
        || t == CrystalTypes.IDENTIFIER || t == CrystalTypes.CONSTANT
        || t == CrystalTypes.INTEGER_LITERAL || t == CrystalTypes.FLOAT_LITERAL
        || t == CrystalTypes.CHAR_LITERAL || t == CrystalTypes.STRING_LITERAL
        || t == CrystalTypes.SYMBOL_LITERAL
        || t == CrystalTypes.RPAREN
        || t == CrystalTypes.RBRACKET || t == CrystalTypes.RBRACE
        || t == CrystalTypes.INSTANCE_VAR || t == CrystalTypes.CLASS_VAR
        || t == CrystalTypes.GLOBAL_VAR || t == CrystalTypes.SELF
        || t == CrystalTypes.SUPER || t == CrystalTypes.TRUE
        || t == CrystalTypes.FALSE || t == CrystalTypes.NIL
        || t == CrystalTypes.END) return false;
    return true;
  }

  // Bare postfix-callee regex (`doc.gsub /re/ "x"` — generator.cr): the
  // callee lexes as IDENTIFIER/CONSTANT (a value token, so isRegexAllowed()
  // says division), but real Crystal (verified 1.21.0) reads `/` as a regex
  // when it is space-separated from the callee AND glued to its content:
  // `doc.gsub /re/` → regex; `a.b / c`, `6/ 2`, `1/2` → division; `a /b/` →
  // regex (`a(/b/)` call — legal). Rule: previous significant token is
  // IDENTIFIER/CONSTANT (a bare-call callee shape), char before `/` is blank,
  // char after is not blank. Only consulted when isRegexAllowed() is false.
  private boolean isBareRegexSlash() {
    IElementType t = lastSignificantToken;
    if (t != CrystalTypes.IDENTIFIER && t != CrystalTypes.CONSTANT) return false;
    if (zzStartRead <= 0 || zzMarkedPos >= zzEndRead) return false;
    char before = zzBuffer.charAt(zzStartRead - 1);
    if (before != ' ' && before != '\t') return false;
    char after = zzBuffer.charAt(zzMarkedPos);
    return after != ' ' && after != '\t' && after != '\r' && after != '\n';
  }

  // Macro body state tracking
  // NOTE: every field in this %{...%} block must ALSO be reset in
  // resetState() above — FlexAdapter reuses one instance across files.
  // Whether a MACRO_BODY `do` opens a counted block (see the `do` rule):
  // false when it binds a call's arg list (`foo(x do ... end)` — real
  // Crystal attaches the block to the call and the `end` closes the macro
  // body, NOT a do-block). Value tokens mean "call argument position";
  // everything else (operators, openers, keywords, start) means "statement
  // position, opens a block". MACRO_BODY_CONTENT is not significant (raw
  // text must not flip the verdict); NEWLINE resets to statement position.
  private boolean isCallArgBlockDo() {
    IElementType t = lastSignificantToken;
    if (t == null || t == CrystalTypes.NEWLINE || t == CrystalTypes.SEMICOLON) return false;
    if (t == TokenType.WHITE_SPACE || t == CrystalTypes.MACRO_BODY_CONTENT
        || t == CrystalTypes.MACRO_INTERPOLATION_BEGIN || t == CrystalTypes.MACRO_INTERPOLATION_END
        || t == CrystalTypes.MACRO_CONTROL_BEGIN || t == CrystalTypes.MACRO_CONTROL_END
        || t == CrystalTypes.MACRO_FRESH_VAR) return false;
    return t == CrystalTypes.IDENTIFIER || t == CrystalTypes.CONSTANT
        || t == CrystalTypes.RPAREN || t == CrystalTypes.RBRACKET || t == CrystalTypes.RBRACE
        || t == CrystalTypes.INTEGER_LITERAL || t == CrystalTypes.FLOAT_LITERAL
        || t == CrystalTypes.CHAR_LITERAL || t == CrystalTypes.STRING_LITERAL
        || t == CrystalTypes.SYMBOL_LITERAL || t == CrystalTypes.SYMBOL_COLON
        || t == CrystalTypes.COMMAND_BEGIN || t == CrystalTypes.COMMAND_LITERAL || t == CrystalTypes.COMMAND_END
        || t == CrystalTypes.REGEX_BEGIN || t == CrystalTypes.REGEX_LITERAL || t == CrystalTypes.REGEX_END
        || t == CrystalTypes.PERCENT_LITERAL_BEGIN || t == CrystalTypes.PERCENT_LITERAL_END
        || t == CrystalTypes.PERCENT_SYMBOL_BEGIN || t == CrystalTypes.PERCENT_SYMBOL_END
        || t == CrystalTypes.INSTANCE_VAR || t == CrystalTypes.CLASS_VAR
        || t == CrystalTypes.GLOBAL_VAR || t == CrystalTypes.SELF
        || t == CrystalTypes.TRUE || t == CrystalTypes.FALSE || t == CrystalTypes.NIL
        || t == CrystalTypes.STRING_INTERPOLATION_END || t == CrystalTypes.HEREDOC_END
        || t == CrystalTypes.END;
  }
  private boolean macroHeaderSeen = false;
  // Signature paren depth while macroHeaderSeen (`macro call(` sets 1 on the
  // header `(`; the flip to MACRO_BODY happens only on NEWLINE at depth 0).
  private int macroParenDepth = 0;
  private int macroBodyDepth = 0;
  private boolean macroBodyAtLineStart = false;
  private StringBuilder macroBodyBuffer = new StringBuilder();

  // Heredoc opened inside `{% %}` (`{% raise <<-TXT unless ... %}` —
  // macros.cr `record`): the opener stays in MACRO_CONTROL (rest of the line
  // is macro code), but the FOLLOWING lines are raw heredoc body until the
  // `TXT` end marker. Set by the `<<-ID` rules below, consumed by the
  // MACRO_CONTROL NEWLINE rule which diverts into MACRO_HEREDOC_BODY.
  // Single-slot like heredocId (the YYINITIAL heredoc has the same
  // limitation — stacked heredocs are out of scope).
  private boolean macroHeredocPending = false;

  private void pushState(int newState) {
    stateStack.push(zzLexicalState);
    yybegin(newState);
  }

  private void popState() {
    if (!stateStack.isEmpty()) {
      yybegin(stateStack.pop());
    } else {
      yybegin(YYINITIAL);
    }
  }

  public int getInterpolationDepth() { return interpolationDepth; }
  public void setInterpolationDepth(int depth) { this.interpolationDepth = depth; }

  private static char closingChar(char open) {
    switch (open) {
      case '(': return ')';
      case '[': return ']';
      case '{': return '}';
      case '<': return '>';
      case '|': return '|';
      default: return open;
    }
  }
%}

// Macros
DIGIT = [0-9]
HEX_DIGIT = [0-9a-fA-F]
OCT_DIGIT = [0-7]
BIN_DIGIT = [01]
ID_CHAR = [a-zA-Z0-9_]

WHITE_SPACE = [ \t\f]+
NEWLINE = \r\n | \r | \n
LINE_COMMENT = "#" [^\r\n]*

// Numbers
DEC_INT = {DIGIT} ({DIGIT} | "_")*
HEX_INT = "0x" ({HEX_DIGIT} | "_")+
OCT_INT = "0o" ({OCT_DIGIT} | "_")+
BIN_INT = "0b" ({BIN_DIGIT} | "_")+
INT_SUFFIX = ("_"? ("i" | "u") ("8" | "16" | "32" | "64" | "128"))?
INTEGER = ({DEC_INT} | {HEX_INT} | {OCT_INT} | {BIN_INT}) {INT_SUFFIX}

FLOAT_SUFFIX = ("_"? "f" ("32" | "64"))?

// Identifiers
IDENTIFIER = [a-z_] {ID_CHAR}* [?!]?
CONSTANT = [A-Z] {ID_CHAR}*
INSTANCE_VAR = "@" {IDENTIFIER}
CLASS_VAR = "@@" {IDENTIFIER}
GLOBAL_VAR = "$" ({IDENTIFIER} | {DIGIT}+ | "~" | "?" | [0-9]+ "?")

// Character literal escape sequences
CHAR_ESCAPE = "\\" ( [abefnrtv\\'0] | "x" {HEX_DIGIT}{2} | "u" "{" {HEX_DIGIT}+ "}" | "u" {HEX_DIGIT}{4} | {OCT_DIGIT}{1,3} )
CHAR_LITERAL = "'" ( [^'\\] | {CHAR_ESCAPE} ) "'"

// Symbol (simple forms only — :"string" handled separately for interpolation support).
// Crystal symbol names may carry a method-suffix operator: `:foo?`, `:foo!`, `:foo=`,
// `:[]`, `:[]=`, `:()`. The trailing `?`/`!`/`=` and bracket forms are common in stdlib
// (e.g. `delegate :pos=, :closed?`, `getter :foo?`).
SYMBOL = ":" ( {IDENTIFIER} | {CONSTANT} ) ( [?!=] | "[]" | "()" )?

%state STRING INTERPOLATION REGEX BACKTICK PERCENT_LITERAL HEREDOC_BODY HEREDOC_START_LINE MACRO_BODY MACRO_INTERPOLATION MACRO_CONTROL MACRO_HEREDOC_BODY

%%

<YYINITIAL> {
  // Whitespace and comments
  {WHITE_SPACE}        { return track(TokenType.WHITE_SPACE); }
  "\\" (\r\n | \r | \n) { return track(TokenType.WHITE_SPACE); }
  {NEWLINE}            { afterDef = false; if (macroHeaderSeen && macroParenDepth == 0) { macroHeaderSeen = false; macroBodyDepth = 0; macroBodyAtLineStart = true; yybegin(MACRO_BODY); } return track(CrystalTypes.NEWLINE); }
  {LINE_COMMENT}       { return track(CrystalTypes.LINE_COMMENT); }

  // Keywords (longest match first for keywords with ? suffix)
  "abstract"           { return track(CrystalTypes.ABSTRACT); }
  "alias"              { return track(CrystalTypes.ALIAS); }
  "annotation"         { return track(CrystalTypes.ANNOTATION); }
  "as?"                { return track(CrystalTypes.AS_QUESTION); }
  "as"                 { return track(CrystalTypes.AS); }
  "asm"                { return track(CrystalTypes.ASM); }
  "begin"              { return track(CrystalTypes.BEGIN); }
  "break"              { return track(CrystalTypes.BREAK); }
  "case"               { return track(CrystalTypes.CASE); }
  "class"              { return track(CrystalTypes.CLASS); }
  "def"                { afterDef = true; return track(CrystalTypes.DEF); }
  "do"                 { return track(CrystalTypes.DO); }
  "else"               { return track(CrystalTypes.ELSE); }
  "elsif"              { return track(CrystalTypes.ELSIF); }
  "end"                { afterDef = false; return track(CrystalTypes.END); }
  "ensure"             { return track(CrystalTypes.ENSURE); }
  "enum"               { return track(CrystalTypes.ENUM); }
  "extend"             { return track(CrystalTypes.EXTEND); }
  "false"              { return track(CrystalTypes.FALSE); }
  "for"                { return track(CrystalTypes.FOR); }
  "fun"                { return track(CrystalTypes.FUN); }
  "if"                 { return track(CrystalTypes.IF); }
  "in"                 { return track(CrystalTypes.IN); }
  "include"            { return track(CrystalTypes.INCLUDE); }
  "instance_sizeof"    { return track(CrystalTypes.INSTANCE_SIZEOF); }
  "is_a?"              { return track(CrystalTypes.IS_A); }
  "lib"                { return track(CrystalTypes.LIB); }
  // `macro` heads a definition only when a name follows on the same line
  // (`macro foo`, `macro []`, `macro +`). Any other `macro` (record field
  // `macro : M`, call `foo.macro`, assignment, `def macro(`) must NOT arm the
  // macro-body switch, or the next NEWLINE flips to MACRO_BODY and swallows
  // the rest of the file.
  // The name class is deliberately wide (`[^ \t\r\n:#;]` — any visible char
  // except `:`/`#`/`;`): macro names share method-name shapes incl. operators
  // (`macro []` — enum.cr — was missed by an IDENTIFIER-only lookahead and
  // the body lexed in YYINITIAL, breaking `|` literal text into PIPE).
  // `:` exclusion keeps `macro : M` (record field) unarmed; `#` keeps
  // `macro # comment` unarmed. `foo.macro args` would false-arm, but `macro`
  // is a keyword and never a valid callee — no valid code hits that.
  // The arm is ARMED on LPAREN (`macro call(` — the header `(` does NOT
  // consume it; the signature's own NEWLINEs must still flip to MACRO_BODY).
  // Instead the arm is CONSUMED at the signature's CLOSING `)`: tracked via
  // macroParenDepth (the header `(` sets it to 1, nested parens deepen, `)`
  // at depth 1 clears both and arms the NEWLINE flip). Without this the
  // NEWLINE inside `(a,\n b)` flips to MACRO_BODY mid-signature —
  // interpreter.cr `private macro call(`.
  // JFlex picks the first rule on equal length, so the lookahead rule is first.
  "macro" / [ \t]+ [^ \t\r\n:#;]  { macroHeaderSeen = true; macroParenDepth = 0; afterDef = true; return track(CrystalTypes.MACRO); }
  "macro"              { afterDef = true; return track(CrystalTypes.MACRO); }
  "module"             { return track(CrystalTypes.MODULE); }
  "next"               { return track(CrystalTypes.NEXT); }
  "nil?"               { return track(CrystalTypes.NIL_QUESTION); }
  "nil"                { return track(CrystalTypes.NIL); }
  "of"                 { return track(CrystalTypes.OF); }
  "offsetof"           { return track(CrystalTypes.OFFSETOF); }
  "out"                { return track(CrystalTypes.OUT); }
  "pointerof"          { return track(CrystalTypes.POINTEROF); }
  "previous_def"       { return track(CrystalTypes.PREVIOUS_DEF); }
  "private"            { return track(CrystalTypes.PRIVATE); }
  "protected"          { return track(CrystalTypes.PROTECTED); }
  "forall"             { return track(CrystalTypes.FORALL); }
  "require"            { return track(CrystalTypes.REQUIRE); }
  "rescue"             { return track(CrystalTypes.RESCUE); }
  "responds_to?"       { return track(CrystalTypes.RESPONDS_TO); }
  "return"             { return track(CrystalTypes.RETURN); }
  "select"             { return track(CrystalTypes.SELECT); }
  "self"               { return track(CrystalTypes.SELF); }
  "sizeof"             { return track(CrystalTypes.SIZEOF); }
  "struct"             { return track(CrystalTypes.STRUCT); }
  "super"              { return track(CrystalTypes.SUPER); }
  "record"             { return track(CrystalTypes.RECORD); }
  "then"               { return track(CrystalTypes.THEN); }
  "true"               { return track(CrystalTypes.TRUE); }
  "typeof"             { return track(CrystalTypes.TYPEOF); }
  "uninitialized"      { return track(CrystalTypes.UNINITIALIZED); }
  "union"              { return track(CrystalTypes.UNION); }
  "unless"             { return track(CrystalTypes.UNLESS); }
  "until"              { return track(CrystalTypes.UNTIL); }
  "verbatim"           { return track(CrystalTypes.VERBATIM); }
  "when"               { return track(CrystalTypes.WHEN); }
  "while"              { return track(CrystalTypes.WHILE); }
  "with"               { return track(CrystalTypes.WITH); }
  "yield"              { return track(CrystalTypes.YIELD); }

  // Invalid single-quote string (more than one character between quotes)
  // This rule must come before CHAR_LITERAL because JFlex longest-match wins.
  // It matches 'xx...x' with 2+ characters inside, producing a single BAD_CHARACTER token.
  "'" [^'\\] [^'\r\n] [^'\r\n]* "'" { return track(TokenType.BAD_CHARACTER); }

  // Literals
  {CHAR_LITERAL}       { return track(CrystalTypes.CHAR_LITERAL); }
  ":\"" / [^]          { pushState(STRING); return track(CrystalTypes.SYMBOL_COLON); }
  {SYMBOL}             { return track(CrystalTypes.SYMBOL_LITERAL); }

  // Numbers (float before int since float is more specific with dot).
  // The float rule needs a digit after the dot, so `d[1..]` lexes INTEGER `1`
  // + DOTDOT (open range), never FLOAT `1.` + DOT.
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} "_f" ("32" | "64")                                                    { return track(CrystalTypes.FLOAT_LITERAL); }
  // Suffix float without dot/exponent/underscore (`1f32` — compiler_rt/pow.cr).
  // JFlex longest-match prefers 4-char `1f32` over 1-char INTEGER; placed with
  // the other float rules. Plain `1f` / `1f33` stay INTEGER + IDENTIFIER (illegal).
  {DEC_INT} "f" ("32" | "64")                                                     { return track(CrystalTypes.FLOAT_LITERAL); }
  {INTEGER}            { return track(CrystalTypes.INTEGER_LITERAL); }

  // Heredoc start: <<-IDENTIFIER or <<-'IDENTIFIER'
  "<<-'" [A-Za-z_][A-Za-z0-9_]* "'"  {
                         String text = yytext().toString();
                         heredocId = text.substring(4, text.length() - 1);
                         heredocIndented = true;
                         heredocRaw = true;
                         yybegin(HEREDOC_START_LINE);
                         return track(CrystalTypes.HEREDOC_START);
                       }
  "<<-" [A-Za-z_][A-Za-z0-9_]*       {
                         String text = yytext().toString();
                         heredocId = text.substring(3);
                         heredocIndented = true;
                         heredocRaw = false;
                         yybegin(HEREDOC_START_LINE);
                         return track(CrystalTypes.HEREDOC_START);
                       }

  // `\{%` / `\{{` anywhere: escaped delimiters that push the matching macro
  // state exactly like the unescaped forms (the `\` only suppresses immediate
  // expansion; the macro still parses — e.g. big_int.cr nests `\{% if %}`
  // inside `{% for %}`). A lone `\` elsewhere stays BAD_CHARACTER.
  // The `\` emits an explicit BACKSLASH token (not consumed): the parser
  // needs it to tell escaped `\{%` body-text apart from real controls.
  "\\" "{{"            { yypushback(2); return track(CrystalTypes.BACKSLASH); }
  "\\" "{%"            { yypushback(2); return track(CrystalTypes.BACKSLASH); }
  // Macro control at top level: {% ... %}
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  // Macro interpolation at top level: {{ ... }}
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }

  // Macro fresh variable (`%var`, `%var{i}` — json/from_json.cr,
  // iterator.cr): real crystal lexes `%`+ident-start as MACRO_VAR when NOT a
  // percent literal (verified in the crystal 1.21.0 lexer source:
  // `delimiter_state_for_percent_literal` requires the delimiter right after
  // the letter, so `%var` is never a literal). JFlex longest-match already
  // prefers `%w(`/`%i(`/etc. over this (3 chars > 2), and `%(`/`%|` over this
  // only when genuinely literal. Placed right after the literal rules.
  // `%`+digit (`%1`, `%1s`) stays PERCENT (format strings — NOT fresh vars:
  // real crystal only takes ident-start after `%`).
  // Implemented as PERCENT + pushback (not a single FRESH_VAR token): the
  // parser combines them (`fresh_var_assignment` / fresh-var primaries),
  // keeping token shapes stable (highlighting reads PERCENT/IDENTIFIER).
  "%" {IDENTIFIER}     { yypushback(yylength() - 1); return track(CrystalTypes.PERCENT); }

  // Percent literals: %w(...), %i(...), %(...), %[...], %{...}, %<...>, %|...|
  // NOTE: `%<letter><delim>` requires the delimiter IMMEDIATELY after the
  // letter (real crystal: `%w [` is modulo + array — verified 1.21.0).
  // Same for `%<delim>`: `% (` is modulo + group (JFlex `"%" [\(\[\{<|]`
  // has no gap, so no change needed there — this note guards regressions).
  "%w" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = false;
                         percentNoEscape = false;
                         yybegin(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%i" [\(\[\{<|]     {
                           char c = yycharat(yylength() - 1);
                           percentOpenChar = c;
                           percentCloseChar = closingChar(c);
                           percentDepth = 1;
                           percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                           percentInterpolation = false;
                         percentNoEscape = false;
                           yybegin(PERCENT_LITERAL);
                           return track(CrystalTypes.PERCENT_SYMBOL_BEGIN);
                         }
  "%q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = false;
                         percentNoEscape = true;
                         yybegin(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%Q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         yybegin(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%r" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.REGEX_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         yybegin(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%x" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.COMMAND_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         yybegin(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%" [\(\[\{<|]      {
                          if (afterDef) { afterDef = false; yypushback(1); return track(CrystalTypes.PERCENT); }
                          char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         yybegin(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }

  // String start
  \"                   { pushState(STRING); return track(CrystalTypes.STRING_LITERAL); }

  // Command literal
  // After `def`/`macro` a backtick is the method name (`def \`(command)` —
  // process.cr), not a literal: same token but no BACKTICK state push, so the
  // parameter list lexes normally. Mirrors the afterDef `%` rule below.
  "`"                    { if (afterDef) { afterDef = false; return track(CrystalTypes.COMMAND_BEGIN); } pushState(BACKTICK); return track(CrystalTypes.COMMAND_BEGIN); }

  // Regex literal (only in operator position — not after identifiers, constants, literals, ) or ])
  // PLUS bare postfix-callee regex (`doc.gsub /re/` — see isBareRegexSlash()).
  "/"                    { if (isRegexAllowed() || isBareRegexSlash()) { pushState(REGEX); return track(CrystalTypes.REGEX_BEGIN); }
                           return track(CrystalTypes.SLASH); }

  // Multi-character operators (longest first)
  "<=>"                { return track(CrystalTypes.SPACESHIP); }
  "==="                { return track(CrystalTypes.CASE_EQ); }
  "**="                { return track(CrystalTypes.DOUBLE_STAR_ASSIGN); }
  "//="                { return track(CrystalTypes.DOUBLE_SLASH_ASSIGN); }
  "<<="                { return track(CrystalTypes.LSHIFT_ASSIGN); }
  ">>="                { return track(CrystalTypes.RSHIFT_ASSIGN); }
  "||="                { return track(CrystalTypes.OR_OR_ASSIGN); }
  "&&="                { return track(CrystalTypes.AND_AND_ASSIGN); }
  "..."                { return track(CrystalTypes.DOTDOTDOT); }
  // `&->` is block-pass of a proc literal (`f(&->@a.b)`, `Fiber.new(name: "x",
  // &->@stack_pool.collect_loop)` — scheduler.cr, fiber.cr), NOT wrapping-minus:
  // JFlex longest-match would otherwise take `&-` (WRAP_MINUS, 2 chars) over `&`
  // (AMPERSAND, 1 char), leaving a stray `>`. Emit `&` and push `->` back so it
  // re-lexes as ARROW. Placed before the WRAP block; strictly longer so it wins.
  "&->"                { yypushback(2); return track(CrystalTypes.AMPERSAND); }
  "&**="               { return track(CrystalTypes.WRAP_DOUBLE_STAR_ASSIGN); }
  "&**"                { return track(CrystalTypes.WRAP_DOUBLE_STAR); }
  "&*="                { return track(CrystalTypes.WRAP_STAR_ASSIGN); }
  "&*"                 { return track(CrystalTypes.WRAP_STAR); }
  "&+="                { return track(CrystalTypes.WRAP_PLUS_ASSIGN); }
  "&+"                 { return track(CrystalTypes.WRAP_PLUS); }
  "&-="                { return track(CrystalTypes.WRAP_MINUS_ASSIGN); }
  // Unary wrapping-negate (`value < 0 ? &-v : v` — big_int.cr): `&-` in
  // OPERAND position (no value before it — same classification as
  // isRegexAllowed()) is prefix negation, not binary wrapping-minus.
  // The `-v` tail re-lexes as MINUS + operand via pushback; the parser treats
  // WRAP_NEG as a prefix operator in unary/bare_unary. In value position
  // (`a &- b`) the plain WRAP_MINUS below is returned (longest-match ties
  // resolve by rule order, so this guard rule sits first).
  "&-" [^= \t\n]        { if (isRegexAllowed()) { yypushback(1); return track(CrystalTypes.WRAP_NEG); }
                           yypushback(1); return track(CrystalTypes.WRAP_MINUS); }
  "&-"                 { return track(CrystalTypes.WRAP_MINUS); }
  // Bare splat (`start_attribute *args, **nargs` — xml/builder.cr): `*` glued
  // to its operand with whitespace BEFORE it (after a callee) is splat, not
  // multiplication (`x * 2`, `a*b` keep STAR: space after `*` or no space
  // before it). prev-token check via lastSignificantToken (callee IDENTIFIER
  // or CONSTANT); char-before check via the JFlex buffer (same-class field).
  // Excludes `*=`/`**` tails (next char `=`/`*` — separate rules own them).
  "*" [^= \t\n*]        { char before = zzStartRead > 0 ? zzBuffer.charAt(zzStartRead - 1) : '\n';
                           if ((before == ' ' || before == '\t' || before == '\n' || before == '\r' || before == ',' || before == '(' || before == '[')
                               && (lastSignificantToken == CrystalTypes.IDENTIFIER || lastSignificantToken == CrystalTypes.CONSTANT)) {
                             yypushback(1); return track(CrystalTypes.SPLAT);
                           }
                           yypushback(1); return track(CrystalTypes.STAR); }
  // Unary plus/minus glued to the operand after a callee (`shift -span.to_i,
  // -span.nanoseconds` — time.cr): whitespace before, none after, callee
  // before that. Same SPLAT-style disambiguation: `a - b` / `a-b` keep MINUS
  // (space after / no space before). Emits dedicated tokens so the
  // binary_op_lookahead guard (which blocks bare commands on MINUS/PLUS +
  // operand) does not kill the bare call; the parser accepts them in
  // unary/bare_unary next to MINUS/PLUS. `return -x` / `(-x)` keep MINUS
  // (prev token is not a callee name — untouched path).
  "-" [^= \t\n>]        { char before = zzStartRead > 0 ? zzBuffer.charAt(zzStartRead - 1) : '\n';
                           if ((before == ' ' || before == '\t')
                               && (lastSignificantToken == CrystalTypes.IDENTIFIER || lastSignificantToken == CrystalTypes.CONSTANT)) {
                             yypushback(1); return track(CrystalTypes.UNARY_MINUS);
                           }
                           yypushback(1); return track(CrystalTypes.MINUS); }
  "+" [^= \t\n]         { char before = zzStartRead > 0 ? zzBuffer.charAt(zzStartRead - 1) : '\n';
                           if ((before == ' ' || before == '\t')
                               && (lastSignificantToken == CrystalTypes.IDENTIFIER || lastSignificantToken == CrystalTypes.CONSTANT)) {
                             yypushback(1); return track(CrystalTypes.UNARY_PLUS);
                           }
                           yypushback(1); return track(CrystalTypes.PLUS); }
  "**"                 { return track(CrystalTypes.DOUBLE_STAR); }
  "//"                 { return track(CrystalTypes.DOUBLE_SLASH); }
  "<<"                 { return track(CrystalTypes.LSHIFT); }
  ">>"                 { return track(CrystalTypes.RSHIFT); }
  "=="                 { return track(CrystalTypes.EQ); }
  "!="                 { return track(CrystalTypes.NEQ); }
  "<="                 { return track(CrystalTypes.LTE); }
  ">="                 { return track(CrystalTypes.GTE); }
  "&&"                 { return track(CrystalTypes.AND_AND); }
  "||"                 { return track(CrystalTypes.OR_OR); }
  "+="                 { return track(CrystalTypes.PLUS_ASSIGN); }
  "-="                 { return track(CrystalTypes.MINUS_ASSIGN); }
  "*="                 { return track(CrystalTypes.STAR_ASSIGN); }
  "/="                 { return track(CrystalTypes.SLASH_ASSIGN); }
  "%="                 { return track(CrystalTypes.PERCENT_ASSIGN); }
  "&="                 { return track(CrystalTypes.AMPERSAND_ASSIGN); }
  "|="                 { return track(CrystalTypes.PIPE_ASSIGN); }
  "^="                 { return track(CrystalTypes.CARET_ASSIGN); }
  ".."                 { return track(CrystalTypes.DOTDOT); }
  "->"                 { return track(CrystalTypes.ARROW); }
  "=>"                 { return track(CrystalTypes.DOUBLE_ARROW); }
  "::"                 { return track(CrystalTypes.DOUBLE_COLON); }

  // Single character operators
  "+"                  { return track(CrystalTypes.PLUS); }
  "-"                  { return track(CrystalTypes.MINUS); }
  "*"                  { return track(CrystalTypes.STAR); }
  "/"                  { return track(CrystalTypes.SLASH); }
  "%"                  { return track(CrystalTypes.PERCENT); }
  "&"                  { return track(CrystalTypes.AMPERSAND); }
  "|"                  { return track(CrystalTypes.PIPE); }
  "^"                  { return track(CrystalTypes.CARET); }
  "~"                  { return track(CrystalTypes.TILDE); }
  "<"                  { return track(CrystalTypes.LT); }
  ">"                  { return track(CrystalTypes.GT); }
  "!"                  { return track(CrystalTypes.BANG); }
  "=~"                 { return track(CrystalTypes.MATCH_OP); }
  "="                  { afterDef = false; return track(CrystalTypes.ASSIGN); }
  "."                  { return track(CrystalTypes.DOT); }
  "?"                  { return track(CrystalTypes.QUESTION); }
  ":"                  { afterDef = false; return track(CrystalTypes.COLON); }
  ";"                  { afterDef = false; return track(CrystalTypes.SEMICOLON); }
  ","                  { return track(CrystalTypes.COMMA); }
  "@"                  { return track(CrystalTypes.AT); }

  // Delimiters
  // While a macro header is armed, parens track the signature depth: the
  // header `(` opens depth 1, the matching `)` closes the signature (but the
  // arm STAYS — the body flip happens on the following NEWLINE). NEWLINEs
  // inside the signature (depth > 0) must NOT flip to MACRO_BODY.
  // `{{`/`{%` inside the signature are still macro code (a macro-splat param
  // `def f({{ x }})` is ILLEGAL in real crystal — verified 1.21.0 — but a
  // macro's OWN params can carry defaults containing them); they push the
  // matching state exactly like top-level `{{`/`{%`, and the arm survives
  // (the NEWLINE flip still fires when the signature closes).
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  "("                  { afterDef = false; if (macroHeaderSeen) macroParenDepth++; return track(CrystalTypes.LPAREN); }
  ")"                  { if (macroHeaderSeen && macroParenDepth > 0) macroParenDepth--; return track(CrystalTypes.RPAREN); }
  "["                  { return track(CrystalTypes.LBRACKET); }
  "]"                  { return track(CrystalTypes.RBRACKET); }
  "{"                  { return track(CrystalTypes.LBRACE); }
  "}"                  { return track(CrystalTypes.RBRACE); }

  // Identifiers (after keywords to ensure keywords take priority)
  {CLASS_VAR}          { return track(CrystalTypes.CLASS_VAR); }
  {INSTANCE_VAR}       { return track(CrystalTypes.INSTANCE_VAR); }
  {GLOBAL_VAR}         { return track(CrystalTypes.GLOBAL_VAR); }
  {CONSTANT}           { return track(CrystalTypes.CONSTANT); }
  {IDENTIFIER}         { return track(CrystalTypes.IDENTIFIER); }
}

<STRING> {
  \"                   { popState(); return track(CrystalTypes.STRING_LITERAL); }
  "#{"                 { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return track(CrystalTypes.STRING_INTERPOLATION_BEGIN); }
  "\\" [abefnrtv\\\"\\'0]  { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "u" {HEX_DIGIT}{4}        { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "x" {HEX_DIGIT}{1,2}      { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" {OCT_DIGIT}{1,3}          { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" .               { return track(CrystalTypes.STRING_ESCAPE); }
  [^\"\#\\]+           { return track(CrystalTypes.STRING_LITERAL); }
  "#"                  { return track(CrystalTypes.STRING_LITERAL); }
}

<REGEX> {
  "#{"                 { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return track(CrystalTypes.STRING_INTERPOLATION_BEGIN); }
  "#"                  { return track(CrystalTypes.REGEX_LITERAL); }
  // `|` and `[`/`]` are literal inside regex (character classes, alternation)
  // and must not be lexed as PIPE/LBRACKET tokens: `when /a|b/` would otherwise
  // split the pattern and break `when`-clause parsing.
  "|"                  { return track(CrystalTypes.REGEX_LITERAL); }
  "["                  { return track(CrystalTypes.REGEX_LITERAL); }
  "]"                  { return track(CrystalTypes.REGEX_LITERAL); }
  "\\" [abefnrtv\\\"\\'0\/]  { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "u" {HEX_DIGIT}{4}        { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "x" {HEX_DIGIT}{1,2}      { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" {OCT_DIGIT}{1,3}          { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" .               { return track(CrystalTypes.STRING_ESCAPE); }
  "/" [imx]*            { popState(); return track(CrystalTypes.REGEX_END); }
  [^\/\#\\]+            { return track(CrystalTypes.REGEX_LITERAL); }
}

<BACKTICK> {
  "#{"                 { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return track(CrystalTypes.STRING_INTERPOLATION_BEGIN); }
  "#"                  { return track(CrystalTypes.COMMAND_LITERAL); }
  "\\" [abefnrtv\\\"\\'0]  { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "u" {HEX_DIGIT}{4}        { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" "x" {HEX_DIGIT}{1,2}      { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" {OCT_DIGIT}{1,3}          { return track(CrystalTypes.STRING_ESCAPE); }
  "\\\n"               { return track(CrystalTypes.STRING_ESCAPE); }
  "\\\r\n"             { return track(CrystalTypes.STRING_ESCAPE); }
  "\\" .               { return track(CrystalTypes.STRING_ESCAPE); }
  "`"                  { popState(); return track(CrystalTypes.COMMAND_END); }
  {NEWLINE}            { return track(CrystalTypes.COMMAND_LITERAL); }
  [^\`#\\]+            { return track(CrystalTypes.COMMAND_LITERAL); }
}

<INTERPOLATION> {
  "{"                  { interpolationDepth++; return track(CrystalTypes.LBRACE); }
  "}"                  { interpolationDepth--;
                         if (interpolationDepth == 0) {
                           interpolationDepth = depthStack.isEmpty() ? 0 : depthStack.pop();
                           popState();
                           return track(CrystalTypes.STRING_INTERPOLATION_END);
                         }
                         return track(CrystalTypes.RBRACE);
                       }
  // All normal tokens are valid inside interpolation.
  // `..` / `...` ranges: JFlex longest-match does NOT apply across separate
  // rules the way one expects for `.` vs `..` here — the single-DOT rule would
  // split open ranges (`d[1..]` → `1` `.` `.`), so multi-dot rules come first.
  "..."                { return track(CrystalTypes.DOTDOTDOT); }
  ".."                 { return track(CrystalTypes.DOTDOT); }
  {WHITE_SPACE}        { return track(TokenType.WHITE_SPACE); }
  {NEWLINE}            { return track(CrystalTypes.NEWLINE); }
  {LINE_COMMENT}       { return track(CrystalTypes.LINE_COMMENT); }
  // Control-flow / jump / value keywords (`"#{if a\n b\n end}"`,
  // `{{ if ... else ... end }}` — macros.cr `record` `copy_with`):
  // without these `if` lexes as IDENTIFIER and the `end` dangles.
  // Before {IDENTIFIER} (tie-break: same length, first rule wins).
  // Safe for `foo.if` (dot_call_access takes keyword_as_method) and
  // `|when|` params (param_name takes keyword_as_record_field).
  "do"                 { return track(CrystalTypes.DO); }
  "end"                { return track(CrystalTypes.END); }
  "if"                 { return track(CrystalTypes.IF); }
  "elsif"              { return track(CrystalTypes.ELSIF); }
  "else"               { return track(CrystalTypes.ELSE); }
  "unless"             { return track(CrystalTypes.UNLESS); }
  "while"              { return track(CrystalTypes.WHILE); }
  "until"              { return track(CrystalTypes.UNTIL); }
  "begin"              { return track(CrystalTypes.BEGIN); }
  "ensure"             { return track(CrystalTypes.ENSURE); }
  "rescue"             { return track(CrystalTypes.RESCUE); }
  "case"               { return track(CrystalTypes.CASE); }
  "when"               { return track(CrystalTypes.WHEN); }
  "in"                 { return track(CrystalTypes.IN); }
  "then"               { return track(CrystalTypes.THEN); }
  "return"             { return track(CrystalTypes.RETURN); }
  "break"              { return track(CrystalTypes.BREAK); }
  "next"               { return track(CrystalTypes.NEXT); }
  "yield"              { return track(CrystalTypes.YIELD); }
  "self"               { return track(CrystalTypes.SELF); }
  "true"               { return track(CrystalTypes.TRUE); }
  "false"              { return track(CrystalTypes.FALSE); }
  "nil"                { return track(CrystalTypes.NIL); }
  "super"              { return track(CrystalTypes.SUPER); }
  "previous_def"       { return track(CrystalTypes.PREVIOUS_DEF); }
  "with"               { return track(CrystalTypes.WITH); }
  "typeof"             { return track(CrystalTypes.TYPEOF); }
  "sizeof"             { return track(CrystalTypes.SIZEOF); }
  "instance_sizeof"    { return track(CrystalTypes.INSTANCE_SIZEOF); }
  "pointerof"          { return track(CrystalTypes.POINTEROF); }
  "offsetof"           { return track(CrystalTypes.OFFSETOF); }
  "uninitialized"      { return track(CrystalTypes.UNINITIALIZED); }
  "asm"                { return track(CrystalTypes.ASM); }
  ":\"" / [^]          { pushState(STRING); return track(CrystalTypes.SYMBOL_COLON); }
  {SYMBOL}             { return track(CrystalTypes.SYMBOL_LITERAL); }
  {IDENTIFIER}         { return track(CrystalTypes.IDENTIFIER); }
  {CONSTANT}           { return track(CrystalTypes.CONSTANT); }
  {INSTANCE_VAR}       { return track(CrystalTypes.INSTANCE_VAR); }
  {CLASS_VAR}          { return track(CrystalTypes.CLASS_VAR); }
  {GLOBAL_VAR}         { return track(CrystalTypes.GLOBAL_VAR); }
  {DEC_INT}            { return track(CrystalTypes.INTEGER_LITERAL); }
  \"                   { pushState(STRING); return track(CrystalTypes.STRING_LITERAL); }
  {CHAR_LITERAL}       { return track(CrystalTypes.CHAR_LITERAL); }
  "."                  { return track(CrystalTypes.DOT); }
  "("                  { return track(CrystalTypes.LPAREN); }
  ")"                  { return track(CrystalTypes.RPAREN); }
  "["                  { return track(CrystalTypes.LBRACKET); }
  "]"                  { return track(CrystalTypes.RBRACKET); }
  "::"                 { return track(CrystalTypes.DOUBLE_COLON); }
  ":"                  { return track(CrystalTypes.COLON); }
  "=="                 { return track(CrystalTypes.EQ); }
  "!="                 { return track(CrystalTypes.NEQ); }
  "<="                 { return track(CrystalTypes.LTE); }
  ">="                 { return track(CrystalTypes.GTE); }
  "&&"                 { return track(CrystalTypes.AND_AND); }
  "||"                 { return track(CrystalTypes.OR_OR); }
  "=>"                 { return track(CrystalTypes.DOUBLE_ARROW); }
  "+"                  { return track(CrystalTypes.PLUS); }
  "-"                  { return track(CrystalTypes.MINUS); }
  // `**`, `<<`, `>>`, `//` (`"#{2 ** 3}"`, `"#{a << 2}"`, `"#{n // m}"` —
  // xml.cr `class_getter` version string): JFlex longest-match keeps them
  // disjoint from `*`, `<`, `>`, `/`; MACRO_INTERPOLATION already has them
  // (mirror rule: both states share the same operator set).
  "**"                 { return track(CrystalTypes.DOUBLE_STAR); }
  "<<"                 { return track(CrystalTypes.LSHIFT); }
  ">>"                 { return track(CrystalTypes.RSHIFT); }
  "//"                 { return track(CrystalTypes.DOUBLE_SLASH); }
  "*"                  { return track(CrystalTypes.STAR); }
  "/"                  { if (isRegexAllowed() || isBareRegexSlash()) { pushState(REGEX); return track(CrystalTypes.REGEX_BEGIN); }
                         return track(CrystalTypes.SLASH); }
  "%"                  { return track(CrystalTypes.PERCENT); }
  "<"                  { return track(CrystalTypes.LT); }
  ">"                  { return track(CrystalTypes.GT); }
  "&"                  { return track(CrystalTypes.AMPERSAND); }
  "|"                  { return track(CrystalTypes.PIPE); }
  "^"                  { return track(CrystalTypes.CARET); }
  "~"                  { return track(CrystalTypes.TILDE); }
  "!"                  { return track(CrystalTypes.BANG); }
  "?"                  { return track(CrystalTypes.QUESTION); }
  "="                  { return track(CrystalTypes.ASSIGN); }
  ","                  { return track(CrystalTypes.COMMA); }
  [^]                  { return track(TokenType.BAD_CHARACTER); }
}

<PERCENT_LITERAL> {
  "#{"                 { if (percentInterpolation) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return track(CrystalTypes.STRING_INTERPOLATION_BEGIN); } return track(percentTokenType); }
  "#"                  { return track(percentTokenType); }
  // Handle nested opening delimiters (except | which doesn't nest)
  .                    {
                           char c = yycharat(0);
                           if (c == percentCloseChar) {
                             percentDepth--;
                             if (percentDepth == 0) {
                               // popState (not YYINITIAL): percent literals can
                               // now start inside MACRO_INTERPOLATION /
                               // MACRO_CONTROL via pushState; pop returns there.
                               // From YYINITIAL the stack is empty and popState
                               // falls back to YYINITIAL — same as before.
                               popState();
                               if (percentTokenType == CrystalTypes.SYMBOL_LITERAL) {
                                 return track(CrystalTypes.PERCENT_SYMBOL_END);
                               }
                               return track(CrystalTypes.PERCENT_LITERAL_END);
                             }
                             return track(percentTokenType);
                           } else if (c == percentOpenChar && percentOpenChar != '|') {
                             percentDepth++;
                             return track(percentTokenType);
                           }
                           return track(percentTokenType);
                         }
  "\\" .               { // `%q` is backslash-literal (single-quote-like): `\` never escapes,
                          // so `\)` is content `\` + close. Push the closer back and emit
                          // the lone backslash as content; the next round closes via `.`.
                          if (percentNoEscape && yycharat(1) == percentCloseChar) { yypushback(1); return track(percentTokenType); }
                          if (percentTokenType == CrystalTypes.STRING_LITERAL || percentTokenType == CrystalTypes.COMMAND_LITERAL) { return track(CrystalTypes.STRING_ESCAPE); } return track(percentTokenType); }
  {NEWLINE}            { return track(percentTokenType); }
}

<HEREDOC_START_LINE> {
  // Consume the rest of the line after <<-ID (could have more code on same line)
  {NEWLINE}            { yybegin(HEREDOC_BODY); return track(CrystalTypes.HEREDOC_CONTENT); }
  .+                   { return track(CrystalTypes.HEREDOC_START); }
}

<HEREDOC_BODY> {
  // Check for end marker (with optional leading whitespace if indented)
  ^[ \t]* {CONSTANT}  {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           yybegin(YYINITIAL);
                           return track(CrystalTypes.HEREDOC_END);
                         }
                         return track(CrystalTypes.HEREDOC_CONTENT);
                       }
  ^[ \t]* {IDENTIFIER} {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           yybegin(YYINITIAL);
                           return track(CrystalTypes.HEREDOC_END);
                         }
                         return track(CrystalTypes.HEREDOC_CONTENT);
                       }
  [^\r\n\#\\]+         { return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" [abefnrtv\\\"\\'0]  { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" "u" {HEX_DIGIT}{4}        { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" "x" {HEX_DIGIT}{1,2}      { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" {OCT_DIGIT}{1,3}          { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" .               { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "#{"                 { if (!heredocRaw) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return track(CrystalTypes.STRING_INTERPOLATION_BEGIN); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "#"                  { return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\"                 { return track(CrystalTypes.HEREDOC_CONTENT); }
  {NEWLINE}            { return track(CrystalTypes.HEREDOC_CONTENT); }
}

// Heredoc body inside `{% %}` (see macroHeredocPending above). Mirrors
// HEREDOC_BODY token-for-token, except the end marker popState()s back to
// MACRO_CONTROL instead of yybegin(YYINITIAL). `#{}` interpolation shares
// the INTERPOLATION state via the pushState stack (pops back here).
<MACRO_HEREDOC_BODY> {
  // Check for end marker (with optional leading whitespace if indented)
  ^[ \t]* {CONSTANT}  {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           popState();
                           return track(CrystalTypes.HEREDOC_END);
                         }
                         return track(CrystalTypes.HEREDOC_CONTENT);
                       }
  ^[ \t]* {IDENTIFIER} {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           popState();
                           return track(CrystalTypes.HEREDOC_END);
                         }
                         return track(CrystalTypes.HEREDOC_CONTENT);
                       }
  [^\r\n\#\\]+         { return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" [abefnrtv\\\"\\'0]  { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" "u" {HEX_DIGIT}{4}        { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" "x" {HEX_DIGIT}{1,2}      { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" {OCT_DIGIT}{1,3}          { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\" .               { if (!heredocRaw) { return track(CrystalTypes.STRING_ESCAPE); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "#{"                 { if (!heredocRaw) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return track(CrystalTypes.STRING_INTERPOLATION_BEGIN); } return track(CrystalTypes.HEREDOC_CONTENT); }
  "#"                  { return track(CrystalTypes.HEREDOC_CONTENT); }
  "\\"                 { return track(CrystalTypes.HEREDOC_CONTENT); }
  {NEWLINE}            { return track(CrystalTypes.HEREDOC_CONTENT); }
}

<MACRO_BODY> {
  // Escaped delimiters (`\{%`, `\{{` — big_int.cr, llvm.cr, ecr/macros.cr):
  // the `\` suppresses expansion; the content stays macro body TEXT.
  // Emit BACKSLASH and push the delimiter back (same pushback discipline as
  // the YYINITIAL rule); the parser pairs BACKSLASH + control as body text.
  "\\" "{{"            { macroBodyAtLineStart = false; yypushback(2); return track(CrystalTypes.BACKSLASH); }
  "\\" "{%"            { macroBodyAtLineStart = false; yypushback(2); return track(CrystalTypes.BACKSLASH); }
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  "#{"                 { return track(CrystalTypes.MACRO_BODY_CONTENT); }
  // A `def` at MACRO_BODY line start is real code, not body text: count the
  // block depth (its `end` closes the def, not the macro). The name/params
  // lex in MACRO_BODY as CONTENT — the params are macro-generated text, NOT
  // parsed params (verified 2026-09-16: arming afterDef flips out of
  // MACRO_BODY on the signature NEWLINE and orphans the def text; and the
  // failure mode is silent whole-file drift, not a local error).
  // Ordering: before the generic block-opener counter below (which would
  // swallow `def` as plain CONTENT without counting).
  // Single rule, no paren guard: `def f`, `def f(`, `def self.f(`, `def f({{`
  // all count identically (the `end` pairing is line-based, unaffected by
  // the header shape).
  // `{{`-on-the-same-line guard: bisected 2026-09-16 on `def k({{` + NEWLINE
  // (macros.cr `record` `initialize`), but REVERTED same day: it trades one
  // error node for 13 NEW whole-file failures (`def`-depth undercount —
  // object.cr, big_int.cr, time.cr, properties.cr, datum.cr, llvm.cr,
  // syscall/*.cr, macros.cr, number.cr, interpreter/compiler.cr, ecr/macros.cr
  // went 7 files/7 errors → 19/21). The `{{`-same-line correlation was a
  // red herring (t32/t33/t34/t37/t38 prove `def ({{...}})` position does not
  // decide it). Keep unconditional counting.
  ^[ \t]* "def" / [ \t]+
                       { macroBodyDepth++; macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  // Track block openers to count depth for END detection
  // We need to detect 'end' at depth 0 as the macro's closing END
  "end"  / [ \t\r\n]  { macroBodyAtLineStart = false; if (macroBodyDepth == 0) { yybegin(YYINITIAL); return track(CrystalTypes.END); }
                         macroBodyDepth--; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  "end"  / [\)\]\},;]  { macroBodyAtLineStart = false; if (macroBodyDepth == 0) { yybegin(YYINITIAL); return track(CrystalTypes.END); }
                         macroBodyDepth--; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  // end at EOF
  "end"               { macroBodyAtLineStart = false; if (macroBodyDepth == 0) { yybegin(YYINITIAL); return track(CrystalTypes.END); }
                         macroBodyDepth--; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  // Block openers that always start blocks (never postfix)
  ("def" | "class" | "module" | "struct" | "enum" | "lib" | "fun" | "macro" | "case" | "begin" | "select" | "annotation") / [ \t\r\n(]
                       { macroBodyDepth++; macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  // `do` opens a block EXCEPT as a call argument's block binding (`foo(x do`
  // — in real Crystal `do` binds to the call's arg list and the `end` closes
  // the macro body, NOT a do-block: `{{ x.map do |f| ... end.splat }}` —
  // macros.cr `record`. Heuristic (verified against stdlib shapes): count it
  // only when NOT preceded by an argument context — i.e. at line start or
  // after `=`, `(`, `,`, `[`, `{`, `=>`, `;`, or a keyword/operator. After a
  // value token (IDENTIFIER/CONSTANT/RPAREN/RBRACKET/STRING/…/`)`) it binds a
  // call, so the matching `end` must close the macro. lastSignificantToken
  // is exactly that token classification (track() skips whitespace).
  "do" / [ \t\r\n(]     {
                         macroBodyAtLineStart = false;
                         if (!isCallArgBlockDo()) { macroBodyDepth++; }
                         return track(CrystalTypes.MACRO_BODY_CONTENT);
                       }
  // Keywords that can be postfix modifiers — only count as block openers at line start
  ("if" | "unless" | "while" | "until") / [ \t\r\n(]
                       { if (macroBodyAtLineStart) { macroBodyDepth++; } macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  {NEWLINE}            { macroBodyAtLineStart = true; return track(CrystalTypes.NEWLINE); }
  {WHITE_SPACE}        { return track(TokenType.WHITE_SPACE); }
  "#" [^\r\n{]*        { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  "%" {IDENTIFIER}     { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_FRESH_VAR); }
  [^ \t\r\n\{\}#]+    { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  "{"                  { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  "}"                  { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  [^]                  { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
}

<MACRO_INTERPOLATION> {
  "}}?"                { popState(); return track(CrystalTypes.MACRO_INTERPOLATION_END); }
  "}}!"                { popState(); return track(CrystalTypes.MACRO_INTERPOLATION_END); }
  "}}"                 { popState(); return track(CrystalTypes.MACRO_INTERPOLATION_END); }
  "`"                  { pushState(BACKTICK); return track(CrystalTypes.COMMAND_BEGIN); }
  "//"                 { return track(CrystalTypes.DOUBLE_SLASH); }
  {WHITE_SPACE}        { return track(TokenType.WHITE_SPACE); }
  {NEWLINE}            { return track(CrystalTypes.NEWLINE); }
  // `do`…`end` blocks inside `{{ }}` (`{{ x.map do |f| ... end.splat }}` —
  // macros.cr `record` `initialize`/`copy_with`): real Crystal attaches the
  // block to the call INSIDE the interpolation (verified 1.21.0 — `end.splat`
  // tails prove it). MACRO_BODY-style lookaheads do NOT apply here (this
  // state pushes via pushState, not the macro-body switch — no
  // macroBodyDepth exists); `do`/`end` are plain code tokens and the parser's
  // block rule pairs them (same as YYINITIAL and INTERPOLATION).
  // Nesting (`{{ x.map do |f| {{ y }} end }}`) needs NO counter: the
  // pushState/popState stack pairs every `{{` with its own `}}` (verified
  // 2026-09-16 — a macroNestingLevel counter tried here broke
  // `\{% if Int{{n}} == X %}` (big_int.cr, llvm.cr): the `{{n}}` bump made
  // `}}` return CONTENT without popping, the state stuck in INTERPOLATION,
  // `%}` lexed as PERCENT+RBRACE, whole-file drift). Escaped `\{{` stays
  // body TEXT (BACKSLASH + pushback — the delimiter re-lexes as code but
  // the parser pairs BACKSLASH + interpolation as opaque body text).
  "\\" "{{"            { yypushback(2); return track(CrystalTypes.BACKSLASH); }
  "do"                 { return track(CrystalTypes.DO); }
  "end"                { return track(CrystalTypes.END); }
  // Control-flow / jump / value keywords (mirror INTERPOLATION above):
  // `{{ if ... else ... end }}` — macros.cr `record` `copy_with`.
  // Without these `if` lexes as IDENTIFIER and the `end` dangles.
  // Before {IDENTIFIER} (tie-break: same length, first rule wins).
  "if"                 { return track(CrystalTypes.IF); }
  "elsif"              { return track(CrystalTypes.ELSIF); }
  "else"               { return track(CrystalTypes.ELSE); }
  "unless"             { return track(CrystalTypes.UNLESS); }
  "while"              { return track(CrystalTypes.WHILE); }
  "until"              { return track(CrystalTypes.UNTIL); }
  "begin"              { return track(CrystalTypes.BEGIN); }
  "ensure"             { return track(CrystalTypes.ENSURE); }
  "rescue"             { return track(CrystalTypes.RESCUE); }
  "case"               { return track(CrystalTypes.CASE); }
  "when"               { return track(CrystalTypes.WHEN); }
  "in"                 { return track(CrystalTypes.IN); }
  "then"               { return track(CrystalTypes.THEN); }
  "return"             { return track(CrystalTypes.RETURN); }
  "break"              { return track(CrystalTypes.BREAK); }
  "next"               { return track(CrystalTypes.NEXT); }
  "yield"              { return track(CrystalTypes.YIELD); }
  "self"               { return track(CrystalTypes.SELF); }
  "true"               { return track(CrystalTypes.TRUE); }
  "false"              { return track(CrystalTypes.FALSE); }
  "nil"                { return track(CrystalTypes.NIL); }
  "super"              { return track(CrystalTypes.SUPER); }
  "previous_def"       { return track(CrystalTypes.PREVIOUS_DEF); }
  "with"               { return track(CrystalTypes.WITH); }
  "typeof"             { return track(CrystalTypes.TYPEOF); }
  "sizeof"             { return track(CrystalTypes.SIZEOF); }
  "instance_sizeof"    { return track(CrystalTypes.INSTANCE_SIZEOF); }
  "pointerof"          { return track(CrystalTypes.POINTEROF); }
  "offsetof"           { return track(CrystalTypes.OFFSETOF); }
  "uninitialized"      { return track(CrystalTypes.UNINITIALIZED); }
  "asm"                { return track(CrystalTypes.ASM); }
  {SYMBOL}             { return track(CrystalTypes.SYMBOL_LITERAL); }
  ":\"" / [^]          { pushState(STRING); return track(CrystalTypes.SYMBOL_COLON); }
  "is_a?"              { return track(CrystalTypes.IS_A); }
  "nil?"               { return track(CrystalTypes.NIL_QUESTION); }
  "responds_to?"       { return track(CrystalTypes.RESPONDS_TO); }
  {IDENTIFIER}         { return track(CrystalTypes.IDENTIFIER); }
  {CONSTANT}           { return track(CrystalTypes.CONSTANT); }
  {INSTANCE_VAR}       { return track(CrystalTypes.INSTANCE_VAR); }
  {INTEGER}            { return track(CrystalTypes.INTEGER_LITERAL); }
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} "_f" ("32" | "64")                                                    { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} "f" ("32" | "64")                                                     { return track(CrystalTypes.FLOAT_LITERAL); }
  \"                   { pushState(STRING); return track(CrystalTypes.STRING_LITERAL); }
  {CHAR_LITERAL}       { return track(CrystalTypes.CHAR_LITERAL); }
  "{"                  { return track(CrystalTypes.LBRACE); }
  "}"                  { return track(CrystalTypes.RBRACE); }
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  "="                  { return track(CrystalTypes.ASSIGN); }
  "<="                 { return track(CrystalTypes.LTE); }
  ">="                 { return track(CrystalTypes.GTE); }
  "^"                  { return track(CrystalTypes.CARET); }
  "~"                  { return track(CrystalTypes.TILDE); }
  "."                  { return track(CrystalTypes.DOT); }
  "("                  { return track(CrystalTypes.LPAREN); }
  ")"                  { return track(CrystalTypes.RPAREN); }
  "["                  { return track(CrystalTypes.LBRACKET); }
  "]"                  { return track(CrystalTypes.RBRACKET); }
  ","                  { return track(CrystalTypes.COMMA); }
  "+"                  { return track(CrystalTypes.PLUS); }
  "-"                  { return track(CrystalTypes.MINUS); }
  "*"                  { return track(CrystalTypes.STAR); }
  "**"                 { return track(CrystalTypes.DOUBLE_STAR); }
  // Shift operators (`{{i >> 2}}` — sha1.cr; mirror rule: INTERPOLATION has
  // them, macro states must too. Verified legal with real crystal 1.21.0.
  // Longest-match keeps `>>`/`<<` disjoint from `>`/`<`/`>=`/`<=`.
  "<<"                 { return track(CrystalTypes.LSHIFT); }
  ">>"                 { return track(CrystalTypes.RSHIFT); }
  "/"                  { if (isRegexAllowed() || isBareRegexSlash()) { pushState(REGEX); return track(CrystalTypes.REGEX_BEGIN); }
                         return track(CrystalTypes.SLASH); }
   "::"                 { return track(CrystalTypes.DOUBLE_COLON); }
   ":"                  { return track(CrystalTypes.COLON); }
   "=="                 { return track(CrystalTypes.EQ); }
  "!="                 { return track(CrystalTypes.NEQ); }
  "<"                  { return track(CrystalTypes.LT); }
  ">"                  { return track(CrystalTypes.GT); }
  "||"                 { return track(CrystalTypes.OR_OR); }
  "&&"                 { return track(CrystalTypes.AND_AND); }
  "|"                  { return track(CrystalTypes.PIPE); }
  "&"                  { return track(CrystalTypes.AMPERSAND); }
  "?"                  { return track(CrystalTypes.QUESTION); }
  "!"                  { return track(CrystalTypes.BANG); }
  // Percent literals (`{{ x.join(%( or )) }}` — float/printer/hexfloat.cr):
  // mirror YYINITIAL. pushState (not yybegin) so PERCENT_LITERAL's end rule
  // popState()s back here instead of YYINITIAL. Longest-match keeps
  // `%w(`/`%(`/`%|` literal vs `%var` fresh-var vs `%` modulo disjoint,
  // exactly as in YYINITIAL (delimiter must immediately follow the letter).
  "%" {IDENTIFIER}     { yypushback(yylength() - 1); return track(CrystalTypes.PERCENT); }
  "%w" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = false;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%i" [\(\[\{<|]     {
                           char c = yycharat(yylength() - 1);
                           percentOpenChar = c;
                           percentCloseChar = closingChar(c);
                           percentDepth = 1;
                           percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                           percentInterpolation = false;
                           percentNoEscape = false;
                           pushState(PERCENT_LITERAL);
                           return track(CrystalTypes.PERCENT_SYMBOL_BEGIN);
                         }
  "%q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = false;
                         percentNoEscape = true;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%Q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%r" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.REGEX_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%x" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.COMMAND_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%" [\(\[\{<|]      {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%"                  { return track(CrystalTypes.PERCENT); }
  [^]                  { return track(TokenType.BAD_CHARACTER); }
}

  <MACRO_CONTROL> {
  "%}"                 { popState(); return track(CrystalTypes.MACRO_CONTROL_END); }
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  // Escaped `\{{` inside `{% %}` stays body TEXT (BACKSLASH + pushback —
  // the delimiter re-lexes as code but the parser pairs BACKSLASH +
  // interpolation as opaque text, same as `\{%` in MACRO_BODY).
  "\\" "{{"            { yypushback(2); return track(CrystalTypes.BACKSLASH); }
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }
  // Heredoc opened inside `{% %}` (`{% raise <<-TXT unless kwargs.empty?`
  // — macros.cr `record`): the opener line stays macro code, but the
  // following lines are raw heredoc body. Record the id (same fields as the
  // YYINITIAL heredoc) and arm macroHeredocPending; the NEWLINE rule below
  // diverts into MACRO_HEREDOC_BODY. Longest-match keeps `<<-TXT` disjoint
  // from LSHIFT (`<<`) and from `a <<-1` (digit after `-` is not an
  // ident-start, so that stays LSHIFT + MINUS + INTEGER).
  "<<-'" [A-Za-z_][A-Za-z0-9_]* "'"  {
                         String text = yytext().toString();
                         heredocId = text.substring(4, text.length() - 1);
                         heredocIndented = true;
                         heredocRaw = true;
                         macroHeredocPending = true;
                         return track(CrystalTypes.HEREDOC_START);
                       }
  "<<-" [A-Za-z_][A-Za-z0-9_]*       {
                         String text = yytext().toString();
                         heredocId = text.substring(3);
                         heredocIndented = true;
                         heredocRaw = false;
                         macroHeredocPending = true;
                         return track(CrystalTypes.HEREDOC_START);
                       }
  "//"                 { return track(CrystalTypes.DOUBLE_SLASH); }
  {NEWLINE}            {
                         // A pending `<<-ID` heredoc starts its body on the
                         // next line: divert into MACRO_HEREDOC_BODY (the
                         // newline itself is body content, mirroring
                         // HEREDOC_START_LINE). popState() at the end marker
                         // returns here.
                         if (macroHeredocPending) {
                           macroHeredocPending = false;
                           pushState(MACRO_HEREDOC_BODY);
                           return track(CrystalTypes.HEREDOC_CONTENT);
                         }
                         return track(CrystalTypes.NEWLINE);
                       }
  {WHITE_SPACE}        { return track(TokenType.WHITE_SPACE); }
  {SYMBOL}             { return track(CrystalTypes.SYMBOL_LITERAL); }
  ":\"" / [^]          { pushState(STRING); return track(CrystalTypes.SYMBOL_COLON); }
  "verbatim"           { return track(CrystalTypes.VERBATIM); }
  "if"                 { return track(CrystalTypes.IF); }
  "else"               { return track(CrystalTypes.ELSE); }
  "elsif"              { return track(CrystalTypes.ELSIF); }
  "end"                { return track(CrystalTypes.END); }
  "for"                { return track(CrystalTypes.FOR); }
  "in"                 { return track(CrystalTypes.IN); }
  "unless"             { return track(CrystalTypes.UNLESS); }
  "begin"              { return track(CrystalTypes.BEGIN); }
  "macro"              { return track(CrystalTypes.MACRO); }
  "yield"              { return track(CrystalTypes.YIELD); }
  "true"               { return track(CrystalTypes.TRUE); }
  "false"              { return track(CrystalTypes.FALSE); }
  "nil"                { return track(CrystalTypes.NIL); }
  {CONSTANT}           { return track(CrystalTypes.CONSTANT); }
  {INSTANCE_VAR}       { return track(CrystalTypes.INSTANCE_VAR); }
  {CLASS_VAR}          { return track(CrystalTypes.CLASS_VAR); }
  {IDENTIFIER}         { return track(CrystalTypes.IDENTIFIER); }
  {INTEGER}            { return track(CrystalTypes.INTEGER_LITERAL); }
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} "_f" ("32" | "64")                                                    { return track(CrystalTypes.FLOAT_LITERAL); }
  {DEC_INT} "f" ("32" | "64")                                                     { return track(CrystalTypes.FLOAT_LITERAL); }
  "`"                  { pushState(BACKTICK); return track(CrystalTypes.COMMAND_BEGIN); }
  \"                   { pushState(STRING); return track(CrystalTypes.STRING_LITERAL); }
  {CHAR_LITERAL}       { return track(CrystalTypes.CHAR_LITERAL); }
  "("                  { return track(CrystalTypes.LPAREN); }
  ")"                  { return track(CrystalTypes.RPAREN); }
  "["                  { return track(CrystalTypes.LBRACKET); }
  "]"                  { return track(CrystalTypes.RBRACKET); }
  ","                  { return track(CrystalTypes.COMMA); }
  "."                  { return track(CrystalTypes.DOT); }
  ":"                  { return track(CrystalTypes.COLON); }
  "=="                 { return track(CrystalTypes.EQ); }
  "!="                 { return track(CrystalTypes.NEQ); }
  "<="                 { return track(CrystalTypes.LTE); }
  ">="                 { return track(CrystalTypes.GTE); }
  "<"                  { return track(CrystalTypes.LT); }
  ">"                  { return track(CrystalTypes.GT); }
  "||"                 { return track(CrystalTypes.OR_OR); }
  "&&"                 { return track(CrystalTypes.AND_AND); }
  "|"                  { return track(CrystalTypes.PIPE); }
  "&"                  { return track(CrystalTypes.AMPERSAND); }
  "="                  { return track(CrystalTypes.ASSIGN); }
  "+"                  { return track(CrystalTypes.PLUS); }
  "-"                  { return track(CrystalTypes.MINUS); }
  "*"                  { return track(CrystalTypes.STAR); }
  "**"                 { return track(CrystalTypes.DOUBLE_STAR); }
  // Shift operators: mirror MACRO_INTERPOLATION above (same sha1.cr shape
  // can appear in `{% %}` code; states must mirror per project rule).
  "<<"                 { return track(CrystalTypes.LSHIFT); }
  ">>"                 { return track(CrystalTypes.RSHIFT); }
  // Regex in macro-control position (`{% x = s.gsub(/re/, "r") %}` — same
  // operator-position rule as YYINITIAL: after `(`/`,`/operator/keyword the
  // `/` opens a regex; `{% ... %}` bodies are code, not strings).
  // Bare-command postfix regex (`doc.gsub /re/ "x"` — see isBareRegexSlash()).
  "/"                  { if (isRegexAllowed() || isBareRegexSlash()) { pushState(REGEX); return track(CrystalTypes.REGEX_BEGIN); }
                         return track(CrystalTypes.SLASH); }
  "?"                  { return track(CrystalTypes.QUESTION); }
  "!"                  { return track(CrystalTypes.BANG); }
  "^"                  { return track(CrystalTypes.CARET); }
  "~"                  { return track(CrystalTypes.TILDE); }
  ".."                 { return track(CrystalTypes.DOTDOT); }
  "..."                { return track(CrystalTypes.DOTDOTDOT); }
   "::"                 { return track(CrystalTypes.DOUBLE_COLON); }
  // Percent literals: mirror MACRO_INTERPOLATION above (same pushState
  // discipline; `{% x = %(a) %}` is legal macro code). Bare `%` also gains
  // a proper PERCENT token here (was BAD_CHARACTER, which broke `{{ a % b }}`
  // modulo too).
  "%" {IDENTIFIER}     { yypushback(yylength() - 1); return track(CrystalTypes.PERCENT); }
  "%w" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = false;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%i" [\(\[\{<|]     {
                           char c = yycharat(yylength() - 1);
                           percentOpenChar = c;
                           percentCloseChar = closingChar(c);
                           percentDepth = 1;
                           percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                           percentInterpolation = false;
                           percentNoEscape = false;
                           pushState(PERCENT_LITERAL);
                           return track(CrystalTypes.PERCENT_SYMBOL_BEGIN);
                         }
  "%q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = false;
                         percentNoEscape = true;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%Q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%r" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.REGEX_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%x" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.COMMAND_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%" [\(\[\{<|]      {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = true;
                         percentNoEscape = false;
                         pushState(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }
  "%"                  { return track(CrystalTypes.PERCENT); }
  "{"                  { return track(CrystalTypes.LBRACE); }
  "}"                  { return track(CrystalTypes.RBRACE); }
  // `do`…`end` blocks in macro code (`{% x.map do |f| ... end %}`): plain
  // tokens, paired by the parser's block rule (no depth tracking needed —
  // same as YYINITIAL and INTERPOLATION, which never needed lookaheads).
  "do"                 { return track(CrystalTypes.DO); }
  "def"                { return track(CrystalTypes.DEF); }
  "class"              { return track(CrystalTypes.CLASS); }
  "module"             { return track(CrystalTypes.MODULE); }
  "struct"             { return track(CrystalTypes.STRUCT); }
  "enum"               { return track(CrystalTypes.ENUM); }
  "macro"              { return track(CrystalTypes.MACRO); }
  "return"             { return track(CrystalTypes.RETURN); }
  "break"              { return track(CrystalTypes.BREAK); }
  "next"               { return track(CrystalTypes.NEXT); }
  "then"               { return track(CrystalTypes.THEN); }
  "when"               { return track(CrystalTypes.WHEN); }
  "case"               { return track(CrystalTypes.CASE); }
  "while"              { return track(CrystalTypes.WHILE); }
  "until"              { return track(CrystalTypes.UNTIL); }
  "rescue"             { return track(CrystalTypes.RESCUE); }
  "ensure"             { return track(CrystalTypes.ENSURE); }
  "fun"                { return track(CrystalTypes.FUN); }
  "lib"                { return track(CrystalTypes.LIB); }
  "type"               { return track(CrystalTypes.TYPE); }
  "alias"              { return track(CrystalTypes.ALIAS); }
  "as"                 { return track(CrystalTypes.AS); }
  "of"                 { return track(CrystalTypes.OF); }
  "is_a?"              { return track(CrystalTypes.IS_A); }
  "nil?"               { return track(CrystalTypes.NIL_QUESTION); }
  "responds_to?"       { return track(CrystalTypes.RESPONDS_TO); }
  "abstract"           { return track(CrystalTypes.ABSTRACT); }
  "protected"          { return track(CrystalTypes.PROTECTED); }
  "private"            { return track(CrystalTypes.PRIVATE); }
  "select"             { return track(CrystalTypes.SELECT); }
  "super"              { return track(CrystalTypes.SUPER); }
  "previous_def"       { return track(CrystalTypes.PREVIOUS_DEF); }
  "with"               { return track(CrystalTypes.WITH); }
  "union"              { return track(CrystalTypes.UNION); }
  "extend"             { return track(CrystalTypes.EXTEND); }
  "include"            { return track(CrystalTypes.INCLUDE); }
  "require"            { return track(CrystalTypes.REQUIRE); }
  "typeof"             { return track(CrystalTypes.TYPEOF); }
  "self"               { return track(CrystalTypes.SELF); }
  "asm"                { return track(CrystalTypes.ASM); }
  "forall"             { return track(CrystalTypes.FORALL); }
  "out"                { return track(CrystalTypes.OUT); }
  "pointerof"          { return track(CrystalTypes.POINTEROF); }
  "sizeof"             { return track(CrystalTypes.SIZEOF); }
  "uninitialized"      { return track(CrystalTypes.UNINITIALIZED); }
  "instance_sizeof"    { return track(CrystalTypes.INSTANCE_SIZEOF); }
  "offsetof"           { return track(CrystalTypes.OFFSETOF); }
  [^]                  { return track(TokenType.BAD_CHARACTER); }
}

[^]                    { return track(TokenType.BAD_CHARACTER); }
