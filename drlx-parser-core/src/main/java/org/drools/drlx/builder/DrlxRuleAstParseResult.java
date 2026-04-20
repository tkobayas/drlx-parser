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

import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.ConsequenceIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleItemIR;
import org.drools.drlx.builder.proto.DrlxRuleAstProto;

/**
 * Persists a compact DRLX-specific rule AST for runtime rebuilds.
 * All protobuf translation works against {@link DrlxRuleAstModel}; there is
 * no direct ANTLR dependency here.
 */
public final class DrlxRuleAstParseResult {

    private static final String FILE_NAME = "drlx-rule-ast.pb";

    private DrlxRuleAstParseResult() {
    }

    public static Path parseResultFilePath(Path dir) {
        return dir.resolve(FILE_NAME);
    }

    public static void save(String drlxSource, CompilationUnitIR data, Path outputDir) throws IOException {
        DrlxRuleAstProto.CompilationUnitParseResult.Builder builder =
                DrlxRuleAstProto.CompilationUnitParseResult.newBuilder()
                        .setSourceHash(hashSource(drlxSource))
                        .setPackageName(data.packageName());

        data.imports().forEach(builder::addImports);
        data.rules().forEach(rule -> builder.addRules(toProtoRule(rule)));

        Files.createDirectories(outputDir);
        try (OutputStream out = Files.newOutputStream(parseResultFilePath(outputDir))) {
            builder.build().writeTo(out);
        }
    }

    public static CompilationUnitIR load(String drlxSource, Path parseResultFile) throws IOException {
        if (!Files.exists(parseResultFile)) {
            return null;
        }

        DrlxRuleAstProto.CompilationUnitParseResult parseResult;
        try (InputStream in = Files.newInputStream(parseResultFile)) {
            parseResult = DrlxRuleAstProto.CompilationUnitParseResult.parseFrom(in);
        }

        if (!parseResult.getSourceHash().equals(hashSource(drlxSource))) {
            return null;
        }

        List<RuleIR> rules = new ArrayList<>(parseResult.getRulesCount());
        for (DrlxRuleAstProto.RuleParseResult ruleParseResult : parseResult.getRulesList()) {
            List<RuleItemIR> items = new ArrayList<>(ruleParseResult.getItemsCount());
            for (DrlxRuleAstProto.RuleItemParseResult itemParseResult : ruleParseResult.getItemsList()) {
                switch (itemParseResult.getItemCase()) {
                    case PATTERN -> {
                        DrlxRuleAstProto.PatternParseResult pattern = itemParseResult.getPattern();
                        String castTypeName = pattern.getCastTypeName().isEmpty() ? null : pattern.getCastTypeName();
                        items.add(new PatternIR(
                                pattern.getTypeName(),
                                pattern.getBindName(),
                                pattern.getEntryPoint(),
                                List.copyOf(pattern.getConditionsList()),
                                castTypeName,
                                List.copyOf(pattern.getPositionalArgsList())));
                    }
                    case CONSEQUENCE -> items.add(new ConsequenceIR(itemParseResult.getConsequence().getBlock()));
                    case ITEM_NOT_SET -> throw new IllegalStateException("Rule item without payload in " + parseResultFile);
                }
            }
            List<RuleAnnotationIR> ruleAnnotations = new ArrayList<>(ruleParseResult.getAnnotationsCount());
            for (DrlxRuleAstProto.RuleAnnotationParseResult annPR : ruleParseResult.getAnnotationsList()) {
                ruleAnnotations.add(new RuleAnnotationIR(fromProtoKind(annPR.getKind()), annPR.getRawValue()));
            }
            rules.add(new RuleIR(ruleParseResult.getName(), List.copyOf(ruleAnnotations), List.copyOf(items)));
        }

        return new CompilationUnitIR(parseResult.getPackageName(), List.copyOf(parseResult.getImportsList()), List.copyOf(rules));
    }

    private static DrlxRuleAstProto.RuleParseResult toProtoRule(RuleIR rule) {
        DrlxRuleAstProto.RuleParseResult.Builder builder = DrlxRuleAstProto.RuleParseResult.newBuilder()
                .setName(rule.name());
        rule.items().forEach(item -> builder.addItems(toProtoItem(item)));
        for (RuleAnnotationIR ann : rule.annotations()) {
            builder.addAnnotations(DrlxRuleAstProto.RuleAnnotationParseResult.newBuilder()
                    .setKind(toProtoKind(ann.kind()))
                    .setRawValue(ann.rawValue())
                    .build());
        }
        return builder.build();
    }

    private static RuleAnnotationIR.Kind fromProtoKind(DrlxRuleAstProto.AnnotationKind k) {
        return switch (k) {
            case ANNOTATION_KIND_SALIENCE -> RuleAnnotationIR.Kind.SALIENCE;
            case ANNOTATION_KIND_DESCRIPTION -> RuleAnnotationIR.Kind.DESCRIPTION;
            case ANNOTATION_KIND_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalStateException("Unknown proto annotation kind: " + k);
        };
    }

    private static DrlxRuleAstProto.AnnotationKind toProtoKind(RuleAnnotationIR.Kind k) {
        return switch (k) {
            case SALIENCE -> DrlxRuleAstProto.AnnotationKind.ANNOTATION_KIND_SALIENCE;
            case DESCRIPTION -> DrlxRuleAstProto.AnnotationKind.ANNOTATION_KIND_DESCRIPTION;
        };
    }

    private static DrlxRuleAstProto.RuleItemParseResult toProtoItem(RuleItemIR item) {
        DrlxRuleAstProto.RuleItemParseResult.Builder builder = DrlxRuleAstProto.RuleItemParseResult.newBuilder();
        if (item instanceof PatternIR p) {
            DrlxRuleAstProto.PatternParseResult.Builder pb = DrlxRuleAstProto.PatternParseResult.newBuilder()
                    .setTypeName(p.typeName())
                    .setBindName(p.bindName())
                    .setEntryPoint(p.entryPoint());
            if (p.castTypeName() != null) {
                pb.setCastTypeName(p.castTypeName());
            }
            p.conditions().forEach(pb::addConditions);
            p.positionalArgs().forEach(pb::addPositionalArgs);
            builder.setPattern(pb);
        } else if (item instanceof ConsequenceIR c) {
            builder.setConsequence(DrlxRuleAstProto.ConsequenceParseResult.newBuilder().setBlock(c.block()));
        } else {
            throw new IllegalArgumentException("Unsupported rule item: " + item);
        }
        return builder.build();
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
