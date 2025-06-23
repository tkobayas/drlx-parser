package org.drools.drlx.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DRLXParserTest {

    @Test
    public void testParseSimpleExpr() {
        String expr = "name == \"Mark\"";
        ParseTree tree = parseExpressionAsAntlrAST(expr);
        
        assertNotNull(tree);
        assertTrue(tree instanceof DRLXParser.DrlxStartContext);
        
        DRLXParser.DrlxStartContext startContext = (DRLXParser.DrlxStartContext) tree;
        assertEquals("name==\"Mark\"", startContext.mvelExpression().getText());
    }

    private static ParseTree parseExpressionAsAntlrAST(final String expression) {
        try {
            // Create ANTLR4 lexer and parser
            CharStream charStream = CharStreams.fromString(expression);
            DRLXLexer lexer = new DRLXLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DRLXParser parser = new DRLXParser(tokens);
            
            // Add error handling
            List<String> errors = new ArrayList<>();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                      int line, int charPositionInLine, String msg, RecognitionException e) {
                    errors.add("Line " + line + ":" + charPositionInLine + " " + msg);
                }
            });
            
            // Parse starting from drlxStart rule
            ParseTree tree = parser.drlxStart();
            
            if (!errors.isEmpty()) {
                throw new RuntimeException("Parser errors: " + String.join(", ", errors));
            }


            return tree;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse expression: " + expression, e);
        }
    }
}