package org.drools.drlx.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class TolerantDrlxParser {

    public static ParseTree parseCompilationUnitAsAntlrAST(final String compilationUnit) {
        try {
            // Create ANTLR4 lexer and parser
            CharStream charStream = CharStreams.fromString(compilationUnit);
            DrlxLexer lexer = new DrlxLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DrlxParser parser = new DrlxParser(tokens);

            // We may remove the default console error listener to avoid printing errors to the console
            //parser.removeErrorListeners();

            return parser.compilationUnit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse compilation unit: " + compilationUnit, e);
        }
    }
}
