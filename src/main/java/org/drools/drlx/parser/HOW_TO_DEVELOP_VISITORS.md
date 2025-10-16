### As of 2025-10-16
`Mvel3Lexer.g4` and `Mvel3Parser.g4` are copied from the mvel3 project (TODO: automate during build?).

`DRLXLexer.g4` and `DRLXParser.g4` import `Mvel3Lexer` and `Mvel3Parser` respectively.

Generated AST nodes are inner classes of `org.drools.drlx.parser.DRLXParser` (e.g. `DRLXParser.InlineCastContext`).

So we cannot use Mvel3ToJavaParserVisitor as-is nor extend it. We need to create a new visitor class `DRLXToJavaParserVisitor` and copy relevant methods from `Mvel3ToJavaParserVisitor`. Find `//--- Below copied from Mvel3ToJavaParserVisitor in mvel3 -----` comment. Those copied should be just pasted and replace `Mvel3Parser.` with `DRLXParser.` for the Context classes. `DRLXToJavaParserVisitor` has other methods to handle DRLX-specific constructs (e.g. `visitCompilationUnit`) above the comment.

`TolerantDRLXToJavaParserVisitor` extends `DRLXToJavaParserVisitor` and overrides some methods to provide more tolerant parsing (e.g. `visitPrimaryExpression`).