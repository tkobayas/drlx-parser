// DRLX Lexer - minimal extension of MVEL3 lexer

lexer grammar DrlxLexer;

import Mvel3Lexer;

// DRLX-specific keywords
UNIT   : 'unit';
RULE   : 'rule';
NOT      : 'not';
EXISTS   : 'exists';
DRLX_AND : 'and';
DRLX_OR  : 'or';