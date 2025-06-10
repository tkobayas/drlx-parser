// $antlr-format alignTrailingComments true, columnLimit 150, minEmptyLines 1, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine false, allowShortBlocksOnASingleLine true, alignSemicolons hanging, alignColons hanging

parser grammar DRLXParser;

options {
    tokenVocab = DRLXLexer;
}

// Override start rule to support DRLX compilation units
start_
    : drlxCompilationUnit EOF
    ;

// DRLX compilation unit
drlxCompilationUnit
    : packageDeclaration? importDeclaration* unitDeclaration topLevelDeclaration*
    ;

// Unit declaration (DRLX-specific)
unitDeclaration
    : 'unit' identifier ';'
    ;

// Top-level declarations (rules, classes, etc.)
topLevelDeclaration
    : ruleDeclaration
    | classDeclaration
    | interfaceDeclaration
    | fieldDeclaration
    | methodDeclaration
    | ';'
    ;

// Rule declaration (DRLX-specific)
ruleDeclaration
    : 'rule' ruleName '{' 
      ruleBody 
      '}'
    ;

// Rule name must follow Java class naming conventions (uppercase first letter)
// Note: Grammar accepts any identifier, naming convention should be enforced at semantic level
ruleName
    : Identifier
    ;

ruleBody
    : ruleElement*
      ruleAction?
    ;

// Rule elements can be patterns or conditional elements
ruleElement
    : rulePattern ','?
    | conditionalElement ','?
    ;

// Rule pattern with optional variable binding and OOPath
rulePattern
    : ('var' identifier ':')? oopathExpression
    ;

// Group Conditional Elements
conditionalElement
    : orElement
    | notElement
    ;

// OR conditional element
orElement
    : OR_COND '(' ruleElement+ ')'        // Multiple elements in parentheses
    | OR_COND rulePattern                 // Single element without parentheses
    ;

// NOT conditional element  
notElement
    : NOT_COND '(' ruleElement+ ')'       // Multiple elements in parentheses
    | NOT_COND rulePattern                // Single element without parentheses
    ;

// OOPath expression
oopathExpression
    : '/' identifier oopathConstraint? propertyReactive?
    ;

// OOPath constraint (filter condition)
oopathConstraint
    : '[' expression (',' expression)* ']'
    ;

// Property Reactive (properties for rule engine re-evaluation)
propertyReactive
    : '[' identifier (',' identifier)* ']'
    ;

// Rule action (multiple forms supported)
ruleAction
    : 'do' block                          // Traditional: do { statements }
    | block                               // Without do: { statements }
    | expressionStatement                 // Single statement with semicolon
    | expression                          // Single expression without semicolon
    ;

// Essential Java rules needed for DRLX
identifier
    : Identifier
    ;

packageDeclaration
    : 'package' identifier ('.' identifier)* ';'
    ;

importDeclaration
    : 'import' 'static'? identifier ('.' identifier)* ('.' '*')? ';'
    ;

typeType
    : (classOrInterfaceType | primitiveType) ('[' ']')*
    ;

classOrInterfaceType
    : identifier ('.' identifier)* typeArguments?
    ;

primitiveType
    : 'boolean'
    | 'char'
    | 'byte'
    | 'short'
    | 'int'
    | 'long'
    | 'float'
    | 'double'
    ;

typeArguments
    : '<' typeArgument (',' typeArgument)* '>'
    ;

typeArgument
    : typeType
    | '?' (('extends' | 'super') typeType)?
    ;

fieldDeclaration
    : typeType variableDeclarators ';'
    ;

variableDeclarators
    : variableDeclarator (',' variableDeclarator)*
    ;

variableDeclarator
    : variableDeclaratorId ('=' variableInitializer)?
    ;

variableDeclaratorId
    : identifier ('[' ']')*
    ;

variableInitializer
    : arrayInitializer
    | expression
    ;

arrayInitializer
    : '{' (variableInitializer (',' variableInitializer)* (',')?)? '}'
    ;

methodDeclaration
    : typeTypeOrVoid identifier formalParameters ('[' ']')* methodBody
    ;

typeTypeOrVoid
    : typeType
    | 'void'
    ;

formalParameters
    : '(' formalParameterList? ')'
    ;

formalParameterList
    : formalParameter (',' formalParameter)*
    ;

formalParameter
    : typeType variableDeclaratorId
    ;

methodBody
    : block
    | ';'
    ;

constructorDeclaration
    : identifier formalParameters block
    ;

classDeclaration
    : 'class' identifier ('extends' typeType)? ('implements' typeList)? classBody
    ;

interfaceDeclaration
    : 'interface' identifier ('extends' typeList)? interfaceBody
    ;

typeList
    : typeType (',' typeType)*
    ;

classBody
    : '{' classBodyDeclaration* '}'
    ;

interfaceBody
    : '{' interfaceBodyDeclaration* '}'
    ;

classBodyDeclaration
    : fieldDeclaration
    | methodDeclaration
    | constructorDeclaration
    | classDeclaration
    | interfaceDeclaration
    | ';'
    ;

interfaceBodyDeclaration
    : fieldDeclaration
    | methodDeclaration
    | classDeclaration
    | interfaceDeclaration
    | ';'
    ;

block
    : '{' blockStatement* '}'
    ;

blockStatement
    : localVariableDeclaration ';'
    | statement
    | typeDeclaration
    ;

localVariableDeclaration
    : typeType variableDeclarators
    ;

typeDeclaration
    : classDeclaration
    | interfaceDeclaration
    | ';'
    ;

statement
    : block
    | expressionStatement
    | ifStatement
    | forStatement
    | whileStatement
    | doWhileStatement
    | tryStatement
    | switchStatement
    | returnStatement
    | throwStatement
    | breakStatement
    | continueStatement
    | ';'
    ;

expressionStatement
    : expression ';'
    ;

ifStatement
    : 'if' parExpression statement ('else' statement)?
    ;

forStatement
    : 'for' '(' forControl ')' statement
    ;

whileStatement
    : 'while' parExpression statement
    ;

doWhileStatement
    : 'do' statement 'while' parExpression ';'
    ;

tryStatement
    : 'try' block (catchClause+ finallyBlock? | finallyBlock)
    ;

switchStatement
    : 'switch' parExpression '{' switchBlockStatementGroup* '}'
    ;

returnStatement
    : 'return' expression? ';'
    ;

throwStatement
    : 'throw' expression ';'
    ;

breakStatement
    : 'break' identifier? ';'
    ;

continueStatement
    : 'continue' identifier? ';'
    ;

forControl
    : forInit? ';' expression? ';' forUpdate?
    ;

forInit
    : localVariableDeclaration
    | expressionList
    ;

forUpdate
    : expressionList
    ;

parExpression
    : '(' expression ')'
    ;

expressionList
    : expression (',' expression)*
    ;

catchClause
    : 'catch' '(' variableModifier* catchType identifier ')' block
    ;

catchType
    : identifier ('|' identifier)*
    ;

finallyBlock
    : 'finally' block
    ;

switchBlockStatementGroup
    : switchLabel+ blockStatement*
    ;

switchLabel
    : 'case' constantExpression ':'
    | 'case' enumConstantName ':'
    | 'default' ':'
    ;

constantExpression
    : expression
    ;

enumConstantName
    : identifier
    ;

variableModifier
    : 'final'
    ;

expression
    : primary
    | expression '.' identifier
    | expression '.' identifier '(' expressionList? ')'
    | expression '[' expression ']'
    | expression ('++' | '--')
    | ('+'|'-'|'++'|'--') expression
    | ('~'|'!') expression
    | expression ('*'|'/'|'%') expression
    | expression ('+'|'-') expression
    | expression ('<' '<' | '>' '>' '>' | '>' '>') expression
    | expression ('<=' | '>=' | '>' | '<') expression
    | expression ('==' | '!=') expression
    | expression '&' expression
    | expression '^' expression
    | expression '|' expression
    | expression '&&' expression
    | expression '||' expression
    | expression '?' expression ':' expression
    | expression ('=' | '+=' | '-=' | '*=' | '/=' | '&=' | '|=' | '^=' | '>>=' | '>>>=' | '<<=' | '%=') expression
    ;

primary
    : '(' expression ')'
    | 'this'
    | 'super'
    | literal
    | identifier
    | typeTypeOrVoid '.' 'class'
    | 'new' creator
    ;

creator
    : createdName (arrayCreatorRest | classCreatorRest)
    ;

createdName
    : identifier typeArgumentsOrDiamond? ('.' identifier typeArgumentsOrDiamond?)*
    | primitiveType
    ;

typeArgumentsOrDiamond
    : '<' '>'
    | typeArguments
    ;

arrayCreatorRest
    : '[' (']' ('[' ']')* arrayInitializer | expression ']' ('[' expression ']')* ('[' ']')*)
    ;

classCreatorRest
    : arguments classBody?
    ;

arguments
    : '(' expressionList? ')'
    ;

literal
    : IntegerLiteral
    | FloatingPointLiteral
    | CharacterLiteral
    | StringLiteral
    | BooleanLiteral
    | NullLiteral
    ;