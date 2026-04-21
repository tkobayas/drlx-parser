package org.drools.drlx.builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.base.SalienceInteger;
import org.drools.base.definitions.impl.KnowledgePackageImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElementFactory;
import org.drools.base.rule.ImportDeclaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.constraint.Constraint;
import org.drools.drlx.builder.DrlxLambdaCompiler.BoundVariable;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.util.TypeResolver;
import org.kie.api.definition.KiePackage;
import org.mvel3.Type;

/**
 * Rebuilds KiePackages from a compact DRLX-specific rule AST parse result.
 * Uses {@link DrlxLambdaCompiler} via composition for evaluator loading and
 * lambda creation.
 */
public class DrlxRuleAstRuntimeBuilder {

    private final DrlxLambdaCompiler lambdaCompiler;

    public DrlxRuleAstRuntimeBuilder(DrlxLambdaCompiler lambdaCompiler) {
        this.lambdaCompiler = lambdaCompiler;
    }

    public List<KiePackage> build(CompilationUnitIR parseResult) {
        KnowledgePackageImpl pkg = new KnowledgePackageImpl(parseResult.packageName());
        pkg.setClassLoader(Thread.currentThread().getContextClassLoader());

        parseResult.imports().forEach(importName -> pkg.addImport(new ImportDeclaration(importName)));
        parseResult.rules().forEach(rule -> pkg.addRule(buildRule(rule, pkg.getTypeResolver())));

        return List.of(pkg);
    }

    private RuleImpl buildRule(RuleIR parseResult, TypeResolver typeResolver) {
        lambdaCompiler.beginRule(parseResult.name());

        RuleImpl rule = new RuleImpl(parseResult.name());
        rule.setResource(rule.getResource());
        applyAnnotations(rule, parseResult.annotations());

        GroupElement root = GroupElementFactory.newAndInstance();
        Map<String, BoundVariable> boundVariables = new LinkedHashMap<>();

        buildLhs(parseResult.lhs(), root, typeResolver, boundVariables);

        if (parseResult.rhs() != null) {
            Map<String, Type<?>> types = lambdaCompiler.getTypeMap(root);
            rule.setConsequence(lambdaCompiler.createLambdaConsequence(parseResult.rhs().block(), types));
        }

        rule.setLhs(root);
        return rule;
    }

    private void buildLhs(List<LhsItemIR> items, GroupElement parent,
                          TypeResolver typeResolver, Map<String, BoundVariable> boundVariables) {
        for (LhsItemIR item : items) {
            if (item instanceof PatternIR patternIr) {
                Pattern pattern = buildPattern(patternIr, typeResolver, boundVariables);
                parent.addChild(pattern);
                Declaration declaration = pattern.getDeclaration();
                if (declaration != null) {
                    Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
                    boundVariables.put(declaration.getIdentifier(),
                            new BoundVariable(declaration.getIdentifier(), patternClass, pattern));
                }
            } else if (item instanceof GroupElementIR group) {
                GroupElement ge = switch (group.kind()) {
                    case NOT -> GroupElementFactory.newNotInstance();
                };
                buildLhs(group.children(), ge, typeResolver, boundVariables);
                parent.addChild(ge);
            } else {
                throw new IllegalArgumentException("Unsupported LHS item: " + item.getClass().getName());
            }
        }
    }

    private Pattern buildPattern(PatternIR parseResult,
                                 TypeResolver typeResolver,
                                 Map<String, BoundVariable> boundVariables) {
        String effectiveTypeName = parseResult.castTypeName() != null ? parseResult.castTypeName() : parseResult.typeName();
        ObjectType objectType;
        try {
            Class<?> type = typeResolver.resolveType(effectiveTypeName);
            objectType = new ClassObjectType(type, false);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Pattern pattern = new Pattern(lambdaCompiler.nextPatternId(), 0, 0, objectType, parseResult.bindName(), false);
        pattern.setSource(new EntryPointId(parseResult.entryPoint()));

        Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
        org.mvel3.transpiler.context.Declaration<?>[] declarations = DrlxLambdaCompiler.extractDeclarations(patternClass);

        for (int i = 0; i < parseResult.positionalArgs().size(); i++) {
            String argExpr = parseResult.positionalArgs().get(i);
            String fieldName = DrlxLambdaCompiler.resolvePositionalField(patternClass, i);
            String synthesized = fieldName + " == (" + argExpr + ")";
            List<BoundVariable> referencedBindings = lambdaCompiler.findReferencedBindings(synthesized, boundVariables);
            Constraint constraint = referencedBindings.isEmpty()
                    ? lambdaCompiler.createLambdaConstraint(synthesized, patternClass, declarations)
                    : lambdaCompiler.createBetaLambdaConstraint(synthesized, patternClass, declarations, referencedBindings);
            pattern.addConstraint(constraint);
        }

        for (String expression : parseResult.conditions()) {
            List<BoundVariable> referencedBindings = lambdaCompiler.findReferencedBindings(expression, boundVariables);
            Constraint constraint = referencedBindings.isEmpty()
                    ? lambdaCompiler.createLambdaConstraint(expression, patternClass, declarations)
                    : lambdaCompiler.createBetaLambdaConstraint(expression, patternClass, declarations, referencedBindings);
            pattern.addConstraint(constraint);
        }

        return pattern;
    }

    private static void applyAnnotations(RuleImpl rule, List<RuleAnnotationIR> annotations) {
        for (RuleAnnotationIR ann : annotations) {
            switch (ann.kind()) {
                case SALIENCE -> rule.setSalience(new SalienceInteger(Integer.parseInt(ann.rawValue())));
                case DESCRIPTION -> rule.addMetaAttribute("Description", ann.rawValue());
            }
        }
    }
}
