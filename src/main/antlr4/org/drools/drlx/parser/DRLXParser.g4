// DRLX Parser - minimal extension of MVEL3 parser

parser grammar DRLXParser;

import Mvel3Parser;

options {
    tokenVocab = DRLXLexer;
}

// Start rule for DRLX
drlxStart
    : compilationUnit
    | drlxCompilationUnit
    ;

// Override compilationUnit to accept DRLX-specific constructs (unit/rule)
compilationUnit
    : packageDeclaration? importDeclaration* unitDeclaration? (typeDeclaration | ruleDeclaration)* EOF
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
    : UNIT identifier ';'
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

// OOPath expression - starts with / and can have multiple chunks
// Aligns with: OOPathExpr(NodeList<OOPathChunk> chunks)
oopathExpression
    : '/' oopathChunk ('/' oopathChunk)*
    ;

// OOPath chunk - simplified for now, just identifier
oopathChunk
    : identifier (HASH identifier)? ('[' drlxExpression (',' drlxExpression)* ']')?
    ;

// DRLX expression used inside oopathChunk conditions
// Allows optional binding (name: expression) or a plain expression
drlxExpression
    : identifier ':' expression
    | expression
    ;
