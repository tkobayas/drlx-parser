package org.drools.drlx.builder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import org.mvel3.parser.MvelParser;
import org.mvel3.parser.ast.expr.CompactWithExpression;



public final class DataStoreUpdateRewriter {

    private final MvelParser mvelParser;

    public DataStoreUpdateRewriter(MvelParser mvelParser) {
        this.mvelParser = mvelParser;
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
            parseResult = mvelParser.parseBlock(wrapped);
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
        List<String> extractedProperties = List.of();
        if (arg instanceof CompactWithExpression compactWith) {
            if (call.getParentNode().orElse(null) instanceof ExpressionStmt exprStmt
                    && exprStmt.getParentNode().orElse(null) instanceof BlockStmt block) {
                int index = block.getStatements().indexOf(exprStmt);
                if (index >= 0) {
                    block.addStatement(index, new ExpressionStmt(compactWith.clone()));
                    extractedProperties = extractPropertyNames(compactWith);
                    arg = compactWith.getTarget().clone();
                    call.setArgument(0, arg);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        if (!(arg instanceof NameExpr) && !(arg instanceof FieldAccessExpr)) {
            return false;
        }

        String globalName = scopeName.getNameAsString();
        String argText = arg.toString();
        StringBuilder sb = new StringBuilder();
        sb.append("org.drools.drlx.builder.DataStoreSupport.update(")
                .append(globalName).append(", ")
                .append(argText).append(", __match__, __ruleBase__, ")
                .append("\"").append(globalName).append("\"");
        for (String prop : extractedProperties) {
            sb.append(", \"").append(prop).append("\"");
        }
        sb.append(")");
        Expression updateCall = StaticJavaParser.parseExpression(sb.toString());

        call.replace(updateCall);
        return true;
    }

    private static List<String> extractPropertyNames(CompactWithExpression compactWith) {
        return compactWith.getAssignments().stream()
                .map(assign -> assign.getTarget().toString())
                .toList();
    }
}
