package org.drools.drlx.builder;

import java.util.Optional;
import java.util.Set;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

/**
 * Rewrites {@code <global>.update(<arg>)} in DRLX consequence bodies to the
 * handle-aware two-arg form
 * {@code <global>.update(java.util.Objects.requireNonNull(<global>.lookup(<arg>), "..."), <arg>)}.
 *
 * <p>Stateless apart from a reusable {@link JavaParser}. Pure: same input
 * yields same output. Cheap string guards skip the parse for bodies where
 * no rewrite could apply.
 */
public final class DataStoreUpdateRewriter {

    private final JavaParser javaParser;

    public DataStoreUpdateRewriter(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    public String rewrite(String consequenceBody, Set<String> dataStoreGlobalNames) {
        if (dataStoreGlobalNames.isEmpty()) {
            return consequenceBody;
        }
        boolean anyCandidateSubstring = false;
        for (String name : dataStoreGlobalNames) {
            if (consequenceBody.contains(name + ".update(")) {
                anyCandidateSubstring = true;
                break;
            }
        }
        if (!anyCandidateSubstring) {
            return consequenceBody;
        }

        String wrapped = "{\n" + consequenceBody + "\n}";
        ParseResult<BlockStmt> parseResult;
        try {
            parseResult = javaParser.parseBlock(wrapped);
        } catch (RuntimeException e) {
            return consequenceBody;
        }
        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            return consequenceBody;
        }
        BlockStmt block = parseResult.getResult().get();

        boolean modified = false;
        for (MethodCallExpr call : block.findAll(MethodCallExpr.class)) {
            if (rewriteCallIfMatch(call, dataStoreGlobalNames)) {
                modified = true;
            }
        }

        if (!modified) {
            return consequenceBody;
        }

        String emitted = block.toString();
        int firstBrace = emitted.indexOf('{');
        int lastBrace = emitted.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace < 0 || lastBrace <= firstBrace) {
            return consequenceBody;
        }
        return emitted.substring(firstBrace + 1, lastBrace);
    }

    private boolean rewriteCallIfMatch(MethodCallExpr call, Set<String> dataStoreGlobalNames) {
        if (!"update".equals(call.getNameAsString())) {
            return false;
        }
        if (call.getArguments().size() != 1) {
            return false;
        }
        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty() || !(scope.get() instanceof NameExpr scopeName)) {
            return false;
        }
        if (!dataStoreGlobalNames.contains(scopeName.getNameAsString())) {
            return false;
        }
        Expression arg = call.getArgument(0);
        if (!(arg instanceof NameExpr) && !(arg instanceof FieldAccessExpr)) {
            return false;
        }

        String globalName = scopeName.getNameAsString();
        String argText = arg.toString();
        String message = "\"DataStore '" + globalName + "' has no DataHandle for the given fact\"";
        Expression requireNonNullCall = StaticJavaParser.parseExpression(
                "java.util.Objects.requireNonNull("
                        + "org.drools.drlx.builder.DataStoreSupport.lookup("
                        + globalName + ", " + argText + "), "
                        + message + ")");

        NodeList<Expression> newArgs = new NodeList<>();
        newArgs.add(requireNonNullCall);
        newArgs.add(arg.clone());
        call.setArguments(newArgs);
        return true;
    }
}
