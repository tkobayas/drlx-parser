package org.drools.drlx.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.drools.drlx.parser.DrlxToDescrVisitor;
import org.drools.drlx.parser.DrlxToJavaParserVisitor;
import org.drools.drlx.parser.TolerantDrlxToJavaParserVisitor;

/**
 * Helper class for DRLX parsing and processing.
 * This class is intended to provide wrapper methods to be used by clients and tests.
 * Small utility methods should be added to a different util class.
 */
public class DrlxHelper {

    private DrlxHelper() {
        // Prevent instantiation
    }

    //--- return Antlr4 AST ---
    public static DrlxParser.ExpressionContext parseExpressionAsAntlrAST(final String input) {
        return (DrlxParser.ExpressionContext) parseAntlrAST(input, DrlxParser::expression);
    }

    public static DrlxParser.CompilationUnitContext parseCompilationUnitAsAntlrAST(final String input) {
        return (DrlxParser.CompilationUnitContext) parseAntlrAST(input, DrlxParser::compilationUnit);
    }

    public static DrlxParser.DrlxCompilationUnitContext parseDrlxCompilationUnitAsAntlrAST(final String input) {
        return (DrlxParser.DrlxCompilationUnitContext) parseAntlrAST(input, DrlxParser::drlxCompilationUnit);
    }

    //--- return JavaParser AST ---
    public static com.github.javaparser.ast.expr.Expression parseExpressionAsJavaParserAST(final String input) {
        ParseTree parseTree = parseAntlrAST(input, DrlxParser::expression);

        DrlxToJavaParserVisitor visitor = new DrlxToJavaParserVisitor();
        return (com.github.javaparser.ast.expr.Expression) visitor.visit(parseTree);
    }

    public static com.github.javaparser.ast.CompilationUnit parseCompilationUnitAsJavaParserAST(final String input) {
        ParseTree parseTree = parseAntlrAST(input, DrlxParser::compilationUnit);

        DrlxToJavaParserVisitor visitor = new DrlxToJavaParserVisitor();
        return (com.github.javaparser.ast.CompilationUnit) visitor.visit(parseTree);
    }

    public static com.github.javaparser.ast.CompilationUnit parseDrlxCompilationUnitAsJavaParserAST(final String input) {
        ParseTree parseTree = parseAntlrAST(input, DrlxParser::drlxCompilationUnit);

        DrlxToJavaParserVisitor visitor = new DrlxToJavaParserVisitor();
        return (com.github.javaparser.ast.CompilationUnit) visitor.visit(parseTree); // result is a CompilationUnit
    }

    //--- return drools descr model ---
    public static PackageDescr parseDrlxCompilationUnitAsPackageDescr(final String input) {
        CharStream charStream = CharStreams.fromString(input);
        DrlxLexer lexer = new DrlxLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);

        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        DrlxToDescrVisitor visitor = new DrlxToDescrVisitor(tokens);
        return (PackageDescr) visitor.visit(ctx);
    }

    //--- return JavaParser AST with tolerance (for code completion) ---
    public static TolerantParseResult<com.github.javaparser.ast.expr.Expression> parseExpressionAsJavaParserASTWithTolerance(final String input) {
        ParseTree parseTree = parseAntlrAST(input, DrlxParser::expression, true);

        TolerantDrlxToJavaParserVisitor visitor = new TolerantDrlxToJavaParserVisitor();
        com.github.javaparser.ast.expr.Expression expression = (com.github.javaparser.ast.expr.Expression) visitor.visit(parseTree);
        return new TolerantParseResult<>(expression, visitor.getTokenIdJPNodeMap());
    }

    public static TolerantParseResult<com.github.javaparser.ast.CompilationUnit> parseCompilationUnitAsJavaParserASTWithTolerance(final String input) {
        ParseTree parseTree = parseAntlrAST(input, DrlxParser::compilationUnit, true);

        TolerantDrlxToJavaParserVisitor visitor = new TolerantDrlxToJavaParserVisitor();
        com.github.javaparser.ast.CompilationUnit compilationUnit = (com.github.javaparser.ast.CompilationUnit) visitor.visit(parseTree);
        return new TolerantParseResult<>(compilationUnit, visitor.getTokenIdJPNodeMap());
    }

    public static TolerantParseResult<com.github.javaparser.ast.CompilationUnit> parseDrlxCompilationUnitAsJavaParserASTWithTolerance(final String input) {
        ParseTree parseTree = parseAntlrAST(input, DrlxParser::drlxCompilationUnit, true);

        TolerantDrlxToJavaParserVisitor visitor = new TolerantDrlxToJavaParserVisitor();
        com.github.javaparser.ast.CompilationUnit compilationUnit = (com.github.javaparser.ast.CompilationUnit) visitor.visit(parseTree); // result is a CompilationUnit
        return new TolerantParseResult<>(compilationUnit, visitor.getTokenIdJPNodeMap());
    }

    //---

    public static ParseTree parseAntlrAST(final String input,
                                          java.util.function.Function<DrlxParser, ParseTree> parseFunction) {
        return parseAntlrAST(input, parseFunction, false); // default is not to tolerate errors
    }

    public static ParseTree parseAntlrAST(final String input,
                                          java.util.function.Function<DrlxParser, ParseTree> parseFunction, boolean tolerateErrors) {
        try {
            // Create ANTLR4 lexer and parser
            CharStream charStream = CharStreams.fromString(input);
            DrlxLexer lexer = new DrlxLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DrlxParser parser = new DrlxParser(tokens);

            // We may remove the default console error listener to avoid printing errors to the console
            //parser.removeErrorListeners();

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

            // If tolerateErrors is true, we ignore errors, so we can work on code completion scenarios
            if (!tolerateErrors && !errors.isEmpty()) {
                throw new RuntimeException("Parser errors: " + String.join(", ", errors));
            }

            return tree;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse : " + input, e);
        }
    }

    public static class TolerantParseResult<T> {
        public final T resultNode;
        public final Map<Integer, com.github.javaparser.ast.Node> tokenIdJPNodeMap;

        public TolerantParseResult(T resultNode, Map<Integer, com.github.javaparser.ast.Node> tokenIdJPNodeMap) {
            this.resultNode = resultNode;
            this.tokenIdJPNodeMap = tokenIdJPNodeMap;
        }

        public T getResultNode() {
            return resultNode;
        }

        public Map<Integer, com.github.javaparser.ast.Node> getTokenIdJPNodeMap() {
            return tokenIdJPNodeMap;
        }
    }
}
