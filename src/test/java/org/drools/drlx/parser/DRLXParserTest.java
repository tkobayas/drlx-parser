package org.drools.drlx.parser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DRLXParserTest {

    @Test
    public void testParseSimpleExpr() {
        String expr = "name == \"Mark\"";
        ParseTree tree = parseExpressionAsAntlrAST(expr);
        
        assertThat(tree).isNotNull();
        assertThat(tree).isInstanceOf(DRLXParser.DrlxStartContext.class);
        
        DRLXParser.DrlxStartContext startContext = (DRLXParser.DrlxStartContext) tree;
        assertThat(startContext.drlxUnit().getText()).isEqualToIgnoringWhitespace(expr);
    }

    @Test
    public void testParseSimpleClass() {
        String expr = """
                public class X {
                }
                """;

        ParseTree tree = parseClassAsAntlrAST(expr);

        assertThat(tree).isNotNull();
        assertThat(tree).isInstanceOf(DRLXParser.CompilationUnitContext.class);

        DRLXParser.CompilationUnitContext compilationUnit = (DRLXParser.CompilationUnitContext) tree;
        assertThat(compilationUnit.getText()).isEqualToIgnoringWhitespace(expr);
    }

    private static ParseTree parseExpressionAsAntlrAST(final String expression) {
        return parseAntlrAST(expression, DRLXParser::drlxStart, "expression");
    }

    private static ParseTree parseClassAsAntlrAST(final String classExpression) {
        return parseAntlrAST(classExpression, DRLXParser::compilationUnit, "class");
    }

    private static ParseTree parseAntlrAST(final String input, 
                                         java.util.function.Function<DRLXParser, ParseTree> parseFunction,
                                         final String inputType) {
        try {
            // Create ANTLR4 lexer and parser
            CharStream charStream = CharStreams.fromString(input);
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

            // Parse using the provided parse function
            ParseTree tree = parseFunction.apply(parser);

            if (!errors.isEmpty()) {
                throw new RuntimeException("Parser errors: " + String.join(", ", errors));
            }

            return tree;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse " + inputType + ": " + input, e);
        }
    }
}