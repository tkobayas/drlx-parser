// DRLX Parser - minimal extension of MVEL3 parser

parser grammar DRLXParser;

import Mvel3Parser;

options {
    tokenVocab = DRLXLexer;
}

// Start rule for DRLX - can be either an expression, compilation unit, or rule
drlxStart
    : mvelExpression EOF
    | compilationUnit
    | ruleCompilationUnit
    ;

// Rule compilation unit - for top-level rule declarations
ruleCompilationUnit
    : drlxRule+ EOF
    ;

// Rule declaration
drlxRule
    : RULE identifier '{' ruleBody '}'
    ;

// Rule body contains patterns and consequences
ruleBody
    : pattern* consequence
    ;

// Pattern: optional binding variable followed by oopathExpression
pattern
    : bindingVariable? oopathExpression ','
    ;

// Binding variable: var identifier :
bindingVariable
    : VAR identifier ':'
    ;

// OOPath expression - starts with /
oopathExpression
    : '/' identifier  // For now, just /identifier
    ;

// Consequence: do blockStatement
consequence
    : DO blockStatement
    ;