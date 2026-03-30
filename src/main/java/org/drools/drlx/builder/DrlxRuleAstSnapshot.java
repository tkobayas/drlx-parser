package org.drools.drlx.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.drools.drlx.builder.proto.DrlxRuleAstProto;
import org.drools.drlx.parser.DrlxParser;

/**
 * Persists a compact DRLX-specific rule AST for runtime rebuilds.
 */
public final class DrlxRuleAstSnapshot {

    private static final String FILE_NAME = "drlx-rule-ast.pb";

    private DrlxRuleAstSnapshot() {
    }

    public static Path snapshotFilePath(Path dir) {
        return dir.resolve(FILE_NAME);
    }

    public static void save(String drlxSource,
                            DrlxParser.DrlxCompilationUnitContext ctx,
                            CommonTokenStream tokens,
                            Path outputDir) throws IOException {
        tokens.fill();

        DrlxRuleAstProto.CompilationUnitSnapshot.Builder builder =
                DrlxRuleAstProto.CompilationUnitSnapshot.newBuilder()
                        .setSourceHash(hashSource(drlxSource))
                        .setPackageName(packageName(ctx));

        if (ctx.importDeclaration() != null) {
            ctx.importDeclaration().forEach(importCtx -> {
                String target = importCtx.qualifiedName().getText();
                if (importCtx.MUL() != null) {
                    target = target + ".*";
                }
                builder.addImports(target);
            });
        }

        if (ctx.ruleDeclaration() != null) {
            ctx.ruleDeclaration().forEach(ruleCtx -> builder.addRules(toProtoRule(ruleCtx, tokens)));
        }

        Files.createDirectories(outputDir);
        try (OutputStream out = Files.newOutputStream(snapshotFilePath(outputDir))) {
            builder.build().writeTo(out);
        }
    }

    public static CompilationUnitData load(String drlxSource, Path snapshotFile) throws IOException {
        if (!Files.exists(snapshotFile)) {
            return null;
        }

        DrlxRuleAstProto.CompilationUnitSnapshot snapshot;
        try (InputStream in = Files.newInputStream(snapshotFile)) {
            snapshot = DrlxRuleAstProto.CompilationUnitSnapshot.parseFrom(in);
        }

        if (!snapshot.getSourceHash().equals(hashSource(drlxSource))) {
            return null;
        }

        List<RuleData> rules = new ArrayList<>(snapshot.getRulesCount());
        for (DrlxRuleAstProto.RuleSnapshot ruleSnapshot : snapshot.getRulesList()) {
            List<RuleItemData> items = new ArrayList<>(ruleSnapshot.getItemsCount());
            for (DrlxRuleAstProto.RuleItemSnapshot itemSnapshot : ruleSnapshot.getItemsList()) {
                switch (itemSnapshot.getItemCase()) {
                    case PATTERN -> {
                        DrlxRuleAstProto.PatternSnapshot pattern = itemSnapshot.getPattern();
                        items.add(new PatternData(
                                pattern.getTypeName(),
                                pattern.getBindName(),
                                pattern.getEntryPoint(),
                                List.copyOf(pattern.getConditionsList())));
                    }
                    case CONSEQUENCE -> items.add(new ConsequenceData(itemSnapshot.getConsequence().getBlock()));
                    case ITEM_NOT_SET -> throw new IllegalStateException("Rule item without payload in " + snapshotFile);
                }
            }
            rules.add(new RuleData(ruleSnapshot.getName(), List.copyOf(items)));
        }

        return new CompilationUnitData(snapshot.getPackageName(), List.copyOf(snapshot.getImportsList()), List.copyOf(rules));
    }

    public record CompilationUnitData(String packageName, List<String> imports, List<RuleData> rules) {
    }

    public record RuleData(String name, List<RuleItemData> items) {
    }

    public sealed interface RuleItemData permits PatternData, ConsequenceData {
    }

    public record PatternData(String typeName, String bindName, String entryPoint, List<String> conditions) implements RuleItemData {
    }

    public record ConsequenceData(String block) implements RuleItemData {
    }

    private static DrlxRuleAstProto.RuleSnapshot toProtoRule(DrlxParser.RuleDeclarationContext ctx, CommonTokenStream tokens) {
        DrlxRuleAstProto.RuleSnapshot.Builder builder = DrlxRuleAstProto.RuleSnapshot.newBuilder()
                .setName(ctx.identifier().getText());

        if (ctx.ruleBody() != null) {
            ctx.ruleBody().ruleItem().forEach(itemCtx -> builder.addItems(toProtoRuleItem(itemCtx, tokens)));
        }
        return builder.build();
    }

    private static DrlxRuleAstProto.RuleItemSnapshot toProtoRuleItem(DrlxParser.RuleItemContext ctx, CommonTokenStream tokens) {
        DrlxRuleAstProto.RuleItemSnapshot.Builder builder = DrlxRuleAstProto.RuleItemSnapshot.newBuilder();
        if (ctx.rulePattern() != null) {
            builder.setPattern(toProtoPattern(ctx.rulePattern(), tokens));
        } else if (ctx.ruleConsequence() != null) {
            builder.setConsequence(DrlxRuleAstProto.ConsequenceSnapshot.newBuilder()
                    .setBlock(trimBraces(getText(tokens, ctx.ruleConsequence().statement())))
                    .build());
        } else {
            throw new IllegalArgumentException("Unsupported rule item: " + ctx.getText());
        }
        return builder.build();
    }

    private static DrlxRuleAstProto.PatternSnapshot toProtoPattern(DrlxParser.RulePatternContext ctx, CommonTokenStream tokens) {
        DrlxRuleAstProto.PatternSnapshot.Builder builder = DrlxRuleAstProto.PatternSnapshot.newBuilder()
                .setTypeName(ctx.identifier(0).getText())
                .setBindName(ctx.identifier(1).getText())
                .setEntryPoint(extractEntryPointFromOopath(getText(tokens, ctx.oopathExpression())));

        extractConditions(ctx.oopathExpression(), tokens).forEach(builder::addConditions);
        return builder.build();
    }

    private static List<String> extractConditions(DrlxParser.OopathExpressionContext ctx, CommonTokenStream tokens) {
        List<DrlxParser.OopathChunkContext> chunks = ctx.oopathChunk();
        if (chunks.isEmpty()) {
            return List.of();
        }
        DrlxParser.OopathChunkContext lastChunk = chunks.get(chunks.size() - 1);
        return lastChunk.drlxExpression().stream()
                .map(expression -> getText(tokens, expression))
                .toList();
    }

    private static String extractEntryPointFromOopath(String oopath) {
        String result = oopath;
        if (result.startsWith("/")) {
            result = result.substring(1);
        }
        int bracketIndex = result.indexOf('[');
        if (bracketIndex >= 0) {
            result = result.substring(0, bracketIndex);
        }
        return result;
    }

    private static String trimBraces(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text;
        if (text.startsWith("{") && text.endsWith("}")) {
            stripped = text.substring(1, text.length() - 1);
        }
        return stripped.trim();
    }

    private static String getText(CommonTokenStream tokens, ParserRuleContext ctx) {
        return tokens.getText(ctx);
    }

    private static String packageName(DrlxParser.DrlxCompilationUnitContext ctx) {
        if (ctx.packageDeclaration() == null) {
            return "";
        }
        return ctx.packageDeclaration().qualifiedName().getText();
    }

    private static String hashSource(String drlxSource) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(drlxSource.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
