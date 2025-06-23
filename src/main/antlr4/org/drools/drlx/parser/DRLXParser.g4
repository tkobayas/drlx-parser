// DRLX Parser - minimal extension of MVEL3 parser

parser grammar DRLXParser;

import Mvel3Parser;

options {
    tokenVocab = DRLXLexer;
}

// Start rule for DRLX - can be either an expression or a compilation unit
drlxStart
    : drlxUnit EOF
    ;

// DRLX unit - supports both expressions and type declarations
drlxUnit
    : mvelExpression
    | compilationUnit
    ;

// Redefine compilationUnit to avoid EOF conflict
compilationUnit
    : packageDeclaration? (importDeclaration | ';')* (typeDeclaration | ';')*
    | moduleDeclaration
    ;