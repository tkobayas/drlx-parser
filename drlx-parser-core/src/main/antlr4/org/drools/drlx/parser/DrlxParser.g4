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

// Rule declaration — annotations may prefix the RULE keyword (e.g. @Salience(10))
ruleDeclaration
    : annotation* RULE identifier '{' ruleBody '}'
    ;

// Rule body contains rule items (patterns and consequences)
// Aligns with: RuleBody(NodeList<RuleItem> items)
ruleBody
    : ruleItem*
    ;

// Rule item can be a pattern, a `not` / `exists` / `and` / `or` group
// element, or a consequence. CE terminator `,` is owned here.
ruleItem
    : rulePattern
    | notElement ','
    | existsElement ','
    | andElement ','
    | orElement ','
    | ruleConsequence
    ;

// Unified child for any group element. No trailing `,` — the parent
// group (paren form) uses `,` as a list separator, and the top-level
// `ruleItem` owns the CE terminator `,`.
// `boundOopath` MUST come before `oopathExpression`: ANTLR picks first
// match, and a bound form (identifier identifier ':' ...) has a strictly
// more constrained prefix than a bare oopath (which starts with '/').
groupChild
    : boundOopath
    | oopathExpression
    | notElement
    | existsElement
    | andElement
    | orElement
    ;

// 'not' group element. Bare form `not /a` for a single child; paren
// form `not(groupChild[, groupChild, ...])` for single-in-parens or
// multi-element, now allowing nested CEs. DRLX spec §"'not' / 'exists'"
// line 597.
notElement
    : NOT oopathExpression
    | NOT '(' groupChild (',' groupChild)* ')'
    ;

// 'exists' group element. Structurally identical to notElement.
existsElement
    : EXISTS oopathExpression
    | EXISTS '(' groupChild (',' groupChild)* ')'
    ;

// 'and' group element. Parentheses required (no paren-omission sugar
// per DRLXXXX §"'and' / 'or' structures"). DRL10 symmetry.
andElement
    : DRLX_AND '(' groupChild (',' groupChild)* ')'
    ;

// 'or' group element. Parentheses required.
orElement
    : DRLX_OR '(' groupChild (',' groupChild)* ')'
    ;

// Bound pattern body without trailing `,`. Reused by `rulePattern`
// (top-level, with terminator `,`) and by `groupChild` (inside a CE
// paren form, where `,` is the sibling separator).
boundOopath
    : identifier identifier (':' | '=') oopathExpression
    ;

// Top-level pattern: `boundOopath ,`. CE terminator owned by ruleItem.
// Aligns with: RulePattern(SimpleName type, SimpleName bind, OOPathExpr expr)
rulePattern
    : boundOopath ','
    ;

// Consequence: do statement
// Aligns with: RuleConsequence(Statement statement)
ruleConsequence
    : DO statement
    ;

// OOPath expression - starts with / followed by a root chunk that may carry
// positional args; subsequent chunks are navigation-only and cannot carry positional.
// Optional leading '?' marks the pattern as passive (DRLXXXX §"Passive elements"):
// the pattern does not wake the rule on its own insertions; only prior reactive
// data pushed into it causes propagation.
// Aligns with: OOPathExpr(NodeList<OOPathChunk> chunks) + Pattern.setPassive(bool)
oopathExpression
    : QUESTION? '/' oopathRoot ('/' oopathChunk)*
    ;

// OOPath root chunk - the only place positional (...) is grammatically valid.
// Second [...] block is the property-reactive watch list. First block may
// be empty to allow `[][watch]` form.
oopathRoot
    : identifier (HASH identifier)?
      ('(' expression (',' expression)* ')')?
      ('[' (drlxExpression (',' drlxExpression)*)? ']')?
      ('[' watchItem (',' watchItem)* ']')?
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

// Watch-list item on property-reactive pattern:
//   '*'         → watch all
//   '!' '*'     → watch none
//   '!'? name   → include / exclude one property
watchItem
    : '!'? ('*' | identifier)
    ;
