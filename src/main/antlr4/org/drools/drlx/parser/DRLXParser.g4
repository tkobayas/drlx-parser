// DRLX Parser - minimal extension of MVEL3 parser

parser grammar DRLXParser;

import Mvel3Parser;

options {
    tokenVocab = DRLXLexer;
}

// Start rule for DRLX
drlxStart
    : compilationUnit
    ;

memberDeclaration
    : recordDeclaration //Java17
    | methodDeclaration
    | genericMethodDeclaration
    | fieldDeclaration
    | constructorDeclaration
    | genericConstructorDeclaration
    | interfaceDeclaration
    | annotationTypeDeclaration
    | classDeclaration
    | enumDeclaration
    | ruleDeclaration
    ;

// Rule declaration
ruleDeclaration
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