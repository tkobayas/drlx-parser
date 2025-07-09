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
    : identifier identifier ':' oopathExpression ','
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
    : identifier
    ;