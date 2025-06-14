// $antlr-format alignTrailingComments true, columnLimit 150, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine true, allowShortBlocksOnASingleLine true, minEmptyLines 0, alignSemicolons ownLine
// $antlr-format alignColons trailing, singleLineOverrulesHangingColon true, alignLexerCommands true, alignLabels true, alignTrailers true

lexer grammar DRLXLexer;

// DRLX-specific keywords (must come before Java keywords for precedence)
UNIT     : 'unit';
RULE     : 'rule';
OR_COND  : 'or';
NOT_COND : 'not';

// Java 20 keywords from Java20Lexer
EXPORTS    : 'exports';
MODULE     : 'module';
NONSEALED  : 'non-sealed';
OACA       : '<>';
OPEN       : 'open';
OPENS      : 'opens';
PERMITS    : 'permits';
PROVIDES   : 'provides';
RECORD     : 'record';
REQUIRES   : 'requires';
SEALED     : 'sealed';
TO         : 'to';
TRANSITIVE : 'transitive';
USES       : 'uses';
VAR        : 'var';
WITH       : 'with';
YIELD      : 'yield';

// §3.9 Keywords
ABSTRACT     : 'abstract';
ASSERT       : 'assert';
BOOLEAN      : 'boolean';
BREAK        : 'break';
BYTE         : 'byte';
CASE         : 'case';
CATCH        : 'catch';
CHAR         : 'char';
CLASS        : 'class';
CONST        : 'const';
CONTINUE     : 'continue';
DEFAULT      : 'default';
DO           : 'do';
DOUBLE       : 'double';
ELSE         : 'else';
ENUM         : 'enum';
EXTENDS      : 'extends';
FINAL        : 'final';
FINALLY      : 'finally';
FLOAT        : 'float';
FOR          : 'for';
IF           : 'if';
GOTO         : 'goto';
IMPLEMENTS   : 'implements';
IMPORT       : 'import';
INSTANCEOF   : 'instanceof';
INT          : 'int';
INTERFACE    : 'interface';
LONG         : 'long';
NATIVE       : 'native';
NEW          : 'new';
PACKAGE      : 'package';
PRIVATE      : 'private';
PROTECTED    : 'protected';
PUBLIC       : 'public';
RETURN       : 'return';
SHORT        : 'short';
STATIC       : 'static';
STRICTFP     : 'strictfp';
SUPER        : 'super';
SWITCH       : 'switch';
SYNCHRONIZED : 'synchronized';
THIS         : 'this';
THROW        : 'throw';
THROWS       : 'throws';
TRANSIENT    : 'transient';
TRY          : 'try';
VOID         : 'void';
VOLATILE     : 'volatile';
WHILE        : 'while';
UNDER_SCORE  : '_';

// §3.10.1 Integer Literals
IntegerLiteral:
    DecimalIntegerLiteral
    | HexIntegerLiteral
    | OctalIntegerLiteral
    | BinaryIntegerLiteral
;

fragment DecimalIntegerLiteral: DecimalNumeral IntegerTypeSuffix?;
fragment HexIntegerLiteral: HexNumeral IntegerTypeSuffix?;
fragment OctalIntegerLiteral: OctalNumeral IntegerTypeSuffix?;
fragment BinaryIntegerLiteral: BinaryNumeral IntegerTypeSuffix?;
fragment IntegerTypeSuffix: [lL];
fragment DecimalNumeral: '0' | NonZeroDigit (Digits? | Underscores Digits);
fragment Digits: Digit (DigitsAndUnderscores? Digit)?;
fragment Digit: '0' | NonZeroDigit;
fragment NonZeroDigit: [1-9];
fragment DigitsAndUnderscores: DigitOrUnderscore+;
fragment DigitOrUnderscore: Digit | '_';
fragment Underscores: '_'+;
fragment HexNumeral: '0' [xX] HexDigits;
fragment HexDigits: HexDigit (HexDigitsAndUnderscores? HexDigit)?;
fragment HexDigit: [0-9a-fA-F];
fragment HexDigitsAndUnderscores: HexDigitOrUnderscore+;
fragment HexDigitOrUnderscore: HexDigit | '_';
fragment OctalNumeral: '0' Underscores? OctalDigits;
fragment OctalDigits: OctalDigit (OctalDigitsAndUnderscores? OctalDigit)?;
fragment OctalDigit: [0-7];
fragment OctalDigitsAndUnderscores: OctalDigitOrUnderscore+;
fragment OctalDigitOrUnderscore: OctalDigit | '_';
fragment BinaryNumeral: '0' [bB] BinaryDigits;
fragment BinaryDigits: BinaryDigit (BinaryDigitsAndUnderscores? BinaryDigit)?;
fragment BinaryDigit: [01];
fragment BinaryDigitsAndUnderscores: BinaryDigitOrUnderscore+;
fragment BinaryDigitOrUnderscore: BinaryDigit | '_';

// §3.10.2 Floating-Point Literals
FloatingPointLiteral: DecimalFloatingPointLiteral | HexadecimalFloatingPointLiteral;

fragment DecimalFloatingPointLiteral:
    Digits '.' Digits? ExponentPart? FloatTypeSuffix?
    | '.' Digits ExponentPart? FloatTypeSuffix?
    | Digits ExponentPart FloatTypeSuffix?
    | Digits FloatTypeSuffix
;

fragment ExponentPart: ExponentIndicator SignedInteger;
fragment ExponentIndicator: [eE];
fragment SignedInteger: Sign? Digits;
fragment Sign: [+-];
fragment FloatTypeSuffix: [fFdD];
fragment HexadecimalFloatingPointLiteral: HexSignificand BinaryExponent FloatTypeSuffix?;
fragment HexSignificand: HexNumeral '.'? | '0' [xX] HexDigits? '.' HexDigits;
fragment BinaryExponent: BinaryExponentIndicator SignedInteger;
fragment BinaryExponentIndicator: [pP];

// §3.10.3 Boolean Literals
BooleanLiteral: 'true' | 'false';

// §3.10.4 Character Literals
CharacterLiteral: '\'' SingleCharacter '\'' | '\'' EscapeSequence '\'';
fragment SingleCharacter: ~['\\\r\n];

// §3.10.5 String Literals
StringLiteral: '"' StringCharacters? '"';
fragment StringCharacters: StringCharacter+;
fragment StringCharacter: ~["\\\r\n] | EscapeSequence;
TextBlock: '"""' [ \t]* [\n\r] [.\r\b]* '"""';

// §3.10.6 Escape Sequences for Character and String Literals
fragment EscapeSequence:
    '\\' [btnfr"'\\]
    | OctalEscape
    | UnicodeEscape
;

fragment OctalEscape:
    '\\' OctalDigit
    | '\\' OctalDigit OctalDigit
    | '\\' ZeroToThree OctalDigit OctalDigit
;

fragment ZeroToThree: [0-3];
fragment UnicodeEscape: '\\' 'u'+ HexDigit HexDigit HexDigit HexDigit;

// §3.10.7 The Null Literal
NullLiteral: 'null';

// §3.11 Separators
LPAREN     : '(';
RPAREN     : ')';
LBRACE     : '{';
RBRACE     : '}';
LBRACK     : '[';
RBRACK     : ']';
SEMI       : ';';
COMMA      : ',';
DOT        : '.';
ELLIPSIS   : '...';
AT         : '@';
COLONCOLON : '::';

// §3.12 Operators
ASSIGN   : '=';
GT       : '>';
LT       : '<';
BANG     : '!';
TILDE    : '~';
QUESTION : '?';
COLON    : ':';
ARROW    : '->';
EQUAL    : '==';
LE       : '<=';
GE       : '>=';
NOTEQUAL : '!=';
AND      : '&&';
OR       : '||';
INC      : '++';
DEC      : '--';
ADD      : '+';
SUB      : '-';
MUL      : '*';
DIV      : '/';
BITAND   : '&';
BITOR    : '|';
CARET    : '^';
MOD      : '%';

ADD_ASSIGN     : '+=';
SUB_ASSIGN     : '-=';
MUL_ASSIGN     : '*=';
DIV_ASSIGN     : '/=';
AND_ASSIGN     : '&=';
OR_ASSIGN      : '|=';
XOR_ASSIGN     : '^=';
MOD_ASSIGN     : '%=';
LSHIFT_ASSIGN  : '<<=';
RSHIFT_ASSIGN  : '>>=';
URSHIFT_ASSIGN : '>>>=';

// §3.8 Identifiers (must appear after all keywords in the grammar)
Identifier: IdentifierStart IdentifierPart*;

fragment IdentifierStart: [a-zA-Z$_];
fragment IdentifierPart: [a-zA-Z0-9$_];

// Whitespace and comments
WS: [ \t\r\n\u000C]+ -> skip;
COMMENT: '/*' .*? '*/' -> channel(HIDDEN);
LINE_COMMENT: '//' ~[\r\n]* -> channel(HIDDEN);