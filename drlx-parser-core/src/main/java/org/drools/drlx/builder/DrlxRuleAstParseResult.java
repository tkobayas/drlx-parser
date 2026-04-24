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
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
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
                        .setPackageName(data.packageName())
                        .setUnitName(data.unitName() == null ? "" : data.unitName());

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
            List<LhsItemIR> lhs = new ArrayList<>(ruleParseResult.getLhsCount());
            for (DrlxRuleAstProto.LhsItemParseResult itemParseResult : ruleParseResult.getLhsList()) {
                lhs.add(fromProtoLhs(itemParseResult, parseResultFile));
            }
            ConsequenceIR rhs = ruleParseResult.hasRhs()
                    ? new ConsequenceIR(ruleParseResult.getRhs().getBlock())
                    : null;

            List<RuleAnnotationIR> ruleAnnotations = new ArrayList<>(ruleParseResult.getAnnotationsCount());
            for (DrlxRuleAstProto.RuleAnnotationParseResult annPR : ruleParseResult.getAnnotationsList()) {
                ruleAnnotations.add(new RuleAnnotationIR(fromProtoKind(annPR.getKind()), annPR.getRawValue()));
            }

            rules.add(new RuleIR(
                    ruleParseResult.getName(),
                    List.copyOf(ruleAnnotations),
                    List.copyOf(lhs),
                    rhs));
        }

        return new CompilationUnitIR(parseResult.getPackageName(),
                parseResult.getUnitName(),
                List.copyOf(parseResult.getImportsList()),
                List.copyOf(rules));
    }

    static LhsItemIR fromProtoLhs(DrlxRuleAstProto.LhsItemParseResult item, Path file) {
        return switch (item.getKindCase()) {
            case PATTERN -> {
                DrlxRuleAstProto.PatternParseResult pattern = item.getPattern();
                String castTypeName = pattern.getCastTypeName().isEmpty() ? null : pattern.getCastTypeName();
                yield new PatternIR(
                        pattern.getTypeName(),
                        pattern.getBindName(),
                        pattern.getEntryPoint(),
                        List.copyOf(pattern.getConditionsList()),
                        castTypeName,
                        List.copyOf(pattern.getPositionalArgsList()),
                        pattern.getPassive(),
                        List.of());
            }
            case GROUP -> {
                DrlxRuleAstProto.GroupElementParseResult group = item.getGroup();
                List<LhsItemIR> children = new ArrayList<>(group.getChildrenCount());
                for (DrlxRuleAstProto.LhsItemParseResult child : group.getChildrenList()) {
                    children.add(fromProtoLhs(child, file));
                }
                yield new GroupElementIR(fromProtoGroupKind(group.getKind()), List.copyOf(children));
            }
            case KIND_NOT_SET -> throw new IllegalStateException("LHS item without payload in " + file);
        };
    }

    private static DrlxRuleAstProto.RuleParseResult toProtoRule(RuleIR rule) {
        DrlxRuleAstProto.RuleParseResult.Builder builder = DrlxRuleAstProto.RuleParseResult.newBuilder()
                .setName(rule.name());
        rule.lhs().forEach(item -> builder.addLhs(toProtoLhs(item)));
        if (rule.rhs() != null) {
            builder.setRhs(DrlxRuleAstProto.ConsequenceParseResult.newBuilder()
                    .setBlock(rule.rhs().block()));
        }
        for (RuleAnnotationIR ann : rule.annotations()) {
            builder.addAnnotations(DrlxRuleAstProto.RuleAnnotationParseResult.newBuilder()
                    .setKind(toProtoKind(ann.kind()))
                    .setRawValue(ann.rawValue())
                    .build());
        }
        return builder.build();
    }

    static DrlxRuleAstProto.LhsItemParseResult toProtoLhs(LhsItemIR item) {
        DrlxRuleAstProto.LhsItemParseResult.Builder builder = DrlxRuleAstProto.LhsItemParseResult.newBuilder();
        if (item instanceof PatternIR p) {
            DrlxRuleAstProto.PatternParseResult.Builder pb = DrlxRuleAstProto.PatternParseResult.newBuilder()
                    .setTypeName(p.typeName())
                    .setBindName(p.bindName())
                    .setEntryPoint(p.entryPoint())
                    .setPassive(p.passive());
            if (p.castTypeName() != null) {
                pb.setCastTypeName(p.castTypeName());
            }
            p.conditions().forEach(pb::addConditions);
            p.positionalArgs().forEach(pb::addPositionalArgs);
            builder.setPattern(pb);
        } else if (item instanceof GroupElementIR g) {
            DrlxRuleAstProto.GroupElementParseResult.Builder gb = DrlxRuleAstProto.GroupElementParseResult.newBuilder()
                    .setKind(toProtoGroupKind(g.kind()));
            g.children().forEach(child -> gb.addChildren(toProtoLhs(child)));
            builder.setGroup(gb);
        } else {
            throw new IllegalArgumentException("Unsupported LHS item: " + item);
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

    private static GroupElementIR.Kind fromProtoGroupKind(DrlxRuleAstProto.GroupElementKind k) {
        return switch (k) {
            case GROUP_ELEMENT_KIND_NOT    -> GroupElementIR.Kind.NOT;
            case GROUP_ELEMENT_KIND_EXISTS -> GroupElementIR.Kind.EXISTS;
            case GROUP_ELEMENT_KIND_AND    -> GroupElementIR.Kind.AND;
            case GROUP_ELEMENT_KIND_OR     -> GroupElementIR.Kind.OR;
            case GROUP_ELEMENT_KIND_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalStateException("Unknown proto group-element kind: " + k);
        };
    }

    private static DrlxRuleAstProto.GroupElementKind toProtoGroupKind(GroupElementIR.Kind k) {
        return switch (k) {
            case NOT    -> DrlxRuleAstProto.GroupElementKind.GROUP_ELEMENT_KIND_NOT;
            case EXISTS -> DrlxRuleAstProto.GroupElementKind.GROUP_ELEMENT_KIND_EXISTS;
            case AND    -> DrlxRuleAstProto.GroupElementKind.GROUP_ELEMENT_KIND_AND;
            case OR     -> DrlxRuleAstProto.GroupElementKind.GROUP_ELEMENT_KIND_OR;
        };
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
