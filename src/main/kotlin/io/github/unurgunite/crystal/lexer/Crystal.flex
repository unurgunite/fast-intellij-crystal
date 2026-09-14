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
  private int interpolationDepth = 0;
  private int percentDepth = 0;
  private char percentOpenChar = 0;
  private char percentCloseChar = 0;
  private IElementType percentTokenType = null;
  private boolean percentInterpolation = false;
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

  // Macro body state tracking
  private boolean macroHeaderSeen = false;
  private int macroBodyDepth = 0;
  private boolean macroBodyAtLineStart = false;
  private int macroNestingLevel = 0;
  private StringBuilder macroBodyBuffer = new StringBuilder();

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
GLOBAL_VAR = "$" ({IDENTIFIER} | {DIGIT}+ | "~" | "?")

// Character literal escape sequences
CHAR_ESCAPE = "\\" ( [abefnrtv\\'0] | "x" {HEX_DIGIT}{2} | "u" "{" {HEX_DIGIT}+ "}" | "u" {HEX_DIGIT}{4} | {OCT_DIGIT}{1,3} )
CHAR_LITERAL = "'" ( [^'\\] | {CHAR_ESCAPE} ) "'"

// Symbol (simple forms only — :"string" handled separately for interpolation support).
// Crystal symbol names may carry a method-suffix operator: `:foo?`, `:foo!`, `:foo=`,
// `:[]`, `:[]=`, `:()`. The trailing `?`/`!`/`=` and bracket forms are common in stdlib
// (e.g. `delegate :pos=, :closed?`, `getter :foo?`).
SYMBOL = ":" ( {IDENTIFIER} | {CONSTANT} ) ( [?!=] | "[]" | "()" )?

%state STRING INTERPOLATION REGEX BACKTICK PERCENT_LITERAL HEREDOC_BODY HEREDOC_START_LINE MACRO_BODY MACRO_INTERPOLATION MACRO_CONTROL

%%

<YYINITIAL> {
  // Whitespace and comments
  {WHITE_SPACE}        { return track(TokenType.WHITE_SPACE); }
  "\\" (\r\n | \r | \n) { return track(TokenType.WHITE_SPACE); }
  {NEWLINE}            { afterDef = false; if (macroHeaderSeen) { macroHeaderSeen = false; macroBodyDepth = 0; macroBodyAtLineStart = true; yybegin(MACRO_BODY); } return track(CrystalTypes.NEWLINE); }
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
  "macro"              { macroHeaderSeen = true; afterDef = true; return track(CrystalTypes.MACRO); }
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

  // Macro control at top level: {% ... %}
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  // Macro interpolation at top level: {{ ... }}
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }

  // Percent literals: %w(...), %i(...), %(...), %[...], %{...}, %<...>, %|...|
  "%w" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         percentInterpolation = false;
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
                         yybegin(PERCENT_LITERAL);
                         return track(CrystalTypes.PERCENT_LITERAL_BEGIN);
                       }

  // String start
  \"                   { pushState(STRING); return track(CrystalTypes.STRING_LITERAL); }

  // Command literal
  "`"                    { pushState(BACKTICK); return track(CrystalTypes.COMMAND_BEGIN); }

  // Regex literal (only in operator position — not after identifiers, constants, literals, ) or ])
  "/"                    { if (isRegexAllowed()) { pushState(REGEX); return track(CrystalTypes.REGEX_BEGIN); }
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
  "&**="               { return track(CrystalTypes.WRAP_DOUBLE_STAR_ASSIGN); }
  "&**"                { return track(CrystalTypes.WRAP_DOUBLE_STAR); }
  "&*="                { return track(CrystalTypes.WRAP_STAR_ASSIGN); }
  "&*"                 { return track(CrystalTypes.WRAP_STAR); }
  "&+="                { return track(CrystalTypes.WRAP_PLUS_ASSIGN); }
  "&+"                 { return track(CrystalTypes.WRAP_PLUS); }
  "&-="                { return track(CrystalTypes.WRAP_MINUS_ASSIGN); }
  "&-"                 { return track(CrystalTypes.WRAP_MINUS); }
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
  "("                  { afterDef = false; return track(CrystalTypes.LPAREN); }
  ")"                  { return track(CrystalTypes.RPAREN); }
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
  ":\"" / [^]          { pushState(STRING); return track(CrystalTypes.SYMBOL_COLON); }
  {SYMBOL}             { return track(CrystalTypes.SYMBOL_LITERAL); }
  {IDENTIFIER}         { return track(CrystalTypes.IDENTIFIER); }
  {CONSTANT}           { return track(CrystalTypes.CONSTANT); }
  {INSTANCE_VAR}       { return track(CrystalTypes.INSTANCE_VAR); }
  {CLASS_VAR}          { return track(CrystalTypes.CLASS_VAR); }
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
  "*"                  { return track(CrystalTypes.STAR); }
  "/"                  { return track(CrystalTypes.SLASH); }
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
                              yybegin(YYINITIAL);
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
  "\\" .               { if (percentTokenType == CrystalTypes.STRING_LITERAL || percentTokenType == CrystalTypes.COMMAND_LITERAL) { return track(CrystalTypes.STRING_ESCAPE); } return track(percentTokenType); }
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

<MACRO_BODY> {
  "\\" "{{"            { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  "\\" "{%"            { macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  "#{"                 { return track(CrystalTypes.MACRO_BODY_CONTENT); }
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
  ("def" | "class" | "module" | "struct" | "enum" | "lib" | "fun" | "macro" | "case" | "begin" | "do" | "select" | "annotation") / [ \t\r\n(]
                       { macroBodyDepth++; macroBodyAtLineStart = false; return track(CrystalTypes.MACRO_BODY_CONTENT); }
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
  {SYMBOL}             { return track(CrystalTypes.SYMBOL_LITERAL); }
  ":\"" / [^]          { pushState(STRING); return track(CrystalTypes.SYMBOL_COLON); }
  "is_a?"              { return track(CrystalTypes.IS_A); }
  "nil?"               { return track(CrystalTypes.NIL_QUESTION); }
  "responds_to?"       { return track(CrystalTypes.RESPONDS_TO); }
  {IDENTIFIER}         { return track(CrystalTypes.IDENTIFIER); }
  {CONSTANT}           { return track(CrystalTypes.CONSTANT); }
  {INSTANCE_VAR}       { return track(CrystalTypes.INSTANCE_VAR); }
  {DEC_INT}            { return track(CrystalTypes.INTEGER_LITERAL); }
  \"                   { pushState(STRING); return track(CrystalTypes.STRING_LITERAL); }
  {CHAR_LITERAL}       { return track(CrystalTypes.CHAR_LITERAL); }
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
  "/"                  { return track(CrystalTypes.SLASH); }
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
  [^]                  { return track(TokenType.BAD_CHARACTER); }
}

 <MACRO_CONTROL> {
  "%}"                 { popState(); return track(CrystalTypes.MACRO_CONTROL_END); }
  "{%"                 { pushState(MACRO_CONTROL); return track(CrystalTypes.MACRO_CONTROL_BEGIN); }
  "{{"                 { pushState(MACRO_INTERPOLATION); return track(CrystalTypes.MACRO_INTERPOLATION_BEGIN); }
  "//"                 { return track(CrystalTypes.DOUBLE_SLASH); }
  "\n"                 { return track(CrystalTypes.NEWLINE); }
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
  {DEC_INT}            { return track(CrystalTypes.INTEGER_LITERAL); }
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
  "/"                  { return track(CrystalTypes.SLASH); }
  "?"                  { return track(CrystalTypes.QUESTION); }
  "!"                  { return track(CrystalTypes.BANG); }
  ".."                 { return track(CrystalTypes.DOTDOT); }
  "..."                { return track(CrystalTypes.DOTDOTDOT); }
  "::"                 { return track(CrystalTypes.DOUBLE_COLON); }
  "%"                  { return track(CrystalTypes.PERCENT); }
  "{"                  { return track(CrystalTypes.LBRACE); }
  "}"                  { return track(CrystalTypes.RBRACE); }
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
