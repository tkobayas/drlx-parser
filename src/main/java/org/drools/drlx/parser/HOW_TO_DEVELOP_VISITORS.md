### As of 2025-10-16
`Mvel3Lexer.g4` and `Mvel3Parser.g4` are copied from the mvel3 project (TODO: automate during build?).

`DRLXLexer.g4` and `DRLXParser.g4` import `Mvel3Lexer` and `Mvel3Parser` respectively.

Generated AST nodes are inner classes of `org.drools.drlx.parser.DRLXParser` (e.g. `DRLXParser.InlineCastContext`).

So we cannot use Mvel3ToJavaParserVisitor as-is nor extend it. We need to create a new visitor class `DRLXToJavaParserVisitor` and copy relevant methods from `Mvel3ToJavaParserVisitor`. Find `//--- Below copied from Mvel3ToJavaParserVisitor in mvel3 -----` comment. Those copied should be just pasted and replace `Mvel3Parser.` with `DRLXParser.` for the Context classes. `DRLXToJavaParserVisitor` has other methods to handle DRLX-specific constructs (e.g. `visitCompilationUnit`) above the comment.

`TolerantDRLXToJavaParserVisitor` extends `DRLXToJavaParserVisitor` and overrides some methods to provide more tolerant parsing (e.g. `visitPrimaryExpression`).

### How to merge updates from mvel3 project
1. Copy `../mvel3/src/main/antlr4/org/mvel3/parser/antlr4/Mvel3Lexer.g4` to `src/main/antlr4/org/drools/drlx/parser/Mvel3Lexer.g4`.
2. Copy `../mvel3/src/main/antlr4/org/mvel3/parser/antlr4/Mvel3Parser.g4` to `src/main/antlr4/org/drools/drlx/parser/Mvel3Parser.g4`.
3. Copy all methods in `../mvel3/src/main/java/org/mvel3/parser/antlr4/Mvel3ToJavaParserVisitor.java` to `src/main/java/org/drools/drlx/parser/DRLXToJavaParserVisitor.java` below the `//--- Below copied from Mvel3ToJavaParserVisitor in mvel3 -----` comment.
4. Make below adjustments to the copied methods
   5. Replace `Mvel3Parser.` with `DRLXParser.` for the Context classes in the copied methods.
   6. Change `createTokenRange` method from `private` to `protected`
   7. Change `parseArguments` method from `private` to `protected`