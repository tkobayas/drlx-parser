// DRLX Parser - minimal extension of MVEL3 parser

parser grammar DrlxParser;

import Mvel3Parser;

options {
    tokenVocab = DrlxLexer;
}

// Start rule for DRLX
drlxStart
    : compilationUnit
    | drlxCompilationUnit
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

drlxCompilationUnit
    : packageDeclaration? importDeclaration* unitDeclaration ruleDeclaration*
    ;

unitDeclaration
    : UNIT qualifiedName ';'
    ;

// Rule declaration
ruleDeclaration
    : RULE identifier '{' ruleBody '}'
    ;

// Rule body contains rule items (patterns and consequences)
// Aligns with: RuleBody(NodeList<RuleItem> items)
ruleBody
    : ruleItem*
    ;

// Rule item can be a pattern or consequence
ruleItem
    : rulePattern
    | ruleConsequence
    ;

// Pattern: type bind : oopathExpression ,
// Aligns with: RulePattern(SimpleName type, SimpleName bind, OOPathExpr expr)
rulePattern
    : identifier identifier (':' | '=') oopathExpression ','
    ;

// Consequence: do statement
// Aligns with: RuleConsequence(Statement statement)
ruleConsequence
    : DO statement
    ;

// OOPath expression - starts with / followed by a root chunk that may carry
// positional args; subsequent chunks are navigation-only and cannot carry positional.
// Aligns with: OOPathExpr(NodeList<OOPathChunk> chunks)
oopathExpression
    : '/' oopathRoot ('/' oopathChunk)*
    ;

// OOPath root chunk - the only place positional (...) is grammatically valid
oopathRoot
    : identifier (HASH identifier)?
      ('(' expression (',' expression)* ')')?
      ('[' drlxExpression (',' drlxExpression)* ']')?
    ;

// OOPath chunk - navigation segments after the root; no positional
oopathChunk
    : identifier (HASH identifier)? ('[' drlxExpression (',' drlxExpression)* ']')?
    ;

// DRLX expression used inside oopathChunk conditions
// Allows optional binding (name: expression) or a plain expression
drlxExpression
    : identifier ':' expression
    | expression
    ;
