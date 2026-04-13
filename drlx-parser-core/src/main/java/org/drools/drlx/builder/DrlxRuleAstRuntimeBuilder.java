package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.definitions.impl.KnowledgePackageImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElementFactory;
import org.drools.base.rule.ImportDeclaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.constraint.Constraint;
import org.drools.util.TypeResolver;
import org.kie.api.definition.KiePackage;
import org.mvel3.Type;

/**
 * Rebuilds KiePackages from a compact DRLX-specific rule AST parse result while
 * reusing the evaluator-loading logic from {@link DrlxToRuleImplVisitor}.
 */
public class DrlxRuleAstRuntimeBuilder extends DrlxToRuleImplVisitor {

    public List<KiePackage> build(DrlxRuleAstParseResult.CompilationUnitData parseResult) {
        KnowledgePackageImpl pkg = new KnowledgePackageImpl(parseResult.packageName());
        pkg.setClassLoader(Thread.currentThread().getContextClassLoader());

        parseResult.imports().forEach(importName -> pkg.addImport(new ImportDeclaration(importName)));
        parseResult.rules().forEach(rule -> pkg.addRule(buildRule(rule, pkg.getTypeResolver())));

        return List.of(pkg);
    }

    private RuleImpl buildRule(DrlxRuleAstParseResult.RuleData parseResult, TypeResolver typeResolver) {
        currentRuleName = parseResult.name();
        lambdaCounter = 0;

        RuleImpl rule = new RuleImpl(parseResult.name());
        rule.setResource(rule.getResource());

        GroupElement ge = GroupElementFactory.newAndInstance();
        Map<String, DrlxToRuleImplVisitor.BoundVariable> boundVariables = new LinkedHashMap<>();

        for (DrlxRuleAstParseResult.RuleItemData item : parseResult.items()) {
            if (item instanceof DrlxRuleAstParseResult.PatternData patternData) {
                Pattern pattern = buildPattern(patternData, typeResolver, boundVariables);
                ge.addChild(pattern);

                Declaration declaration = pattern.getDeclaration();
                if (declaration != null) {
                    Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
                    boundVariables.put(declaration.getIdentifier(),
                            new DrlxToRuleImplVisitor.BoundVariable(declaration.getIdentifier(), patternClass, pattern));
                }
            } else if (item instanceof DrlxRuleAstParseResult.ConsequenceData consequenceData) {
                Map<String, Type<?>> types = getTypeMap(ge);
                rule.setConsequence(createLambdaConsequence(consequenceData.block(), types));
            } else {
                throw new IllegalArgumentException("Unsupported rule item: " + item.getClass().getName());
            }
        }

        rule.setLhs(ge);
        return rule;
    }

    private Pattern buildPattern(DrlxRuleAstParseResult.PatternData parseResult,
                                 TypeResolver typeResolver,
                                 Map<String, DrlxToRuleImplVisitor.BoundVariable> boundVariables) {
        // Use cast type if present, otherwise use declared type
        String effectiveTypeName = parseResult.castTypeName() != null ? parseResult.castTypeName() : parseResult.typeName();
        ObjectType objectType;
        try {
            Class<?> type = typeResolver.resolveType(effectiveTypeName);
            objectType = new ClassObjectType(type, false);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Pattern pattern = new Pattern(patternId++, 0, 0, objectType, parseResult.bindName(), false);
        pattern.setSource(new EntryPointId(parseResult.entryPoint()));

        Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
        org.mvel3.transpiler.context.Declaration<?>[] declarations = extractDeclarations(patternClass);
        for (String expression : parseResult.conditions()) {
            List<DrlxToRuleImplVisitor.BoundVariable> referencedBindings = findReferencedBindings(expression, boundVariables);
            Constraint constraint = referencedBindings.isEmpty()
                    ? createLambdaConstraint(expression, patternClass, declarations)
                    : createBetaLambdaConstraint(expression, patternClass, declarations, referencedBindings);
            pattern.addConstraint(constraint);
        }

        return pattern;
    }

    private List<DrlxToRuleImplVisitor.BoundVariable> findReferencedBindings(
            String expression,
            Map<String, DrlxToRuleImplVisitor.BoundVariable> boundVariables) {
        List<DrlxToRuleImplVisitor.BoundVariable> referenced = new ArrayList<>();
        for (Map.Entry<String, DrlxToRuleImplVisitor.BoundVariable> entry : boundVariables.entrySet()) {
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(
                    "(?<![a-zA-Z0-9_])" + java.util.regex.Pattern.quote(entry.getKey()) + "(?![a-zA-Z0-9_])");
            if (regex.matcher(expression).find()) {
                referenced.add(entry.getValue());
            }
        }
        return referenced;
    }

    private Map<String, Type<?>> getTypeMap(GroupElement ge) {
        Map<String, Type<?>> types = new LinkedHashMap<>();
        ge.getChildren().stream()
                .filter(element -> element instanceof Pattern)
                .map(Pattern.class::cast)
                .forEach(pattern -> {
                    Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
                    Declaration declaration = pattern.getDeclaration();
                    types.put(declaration.getIdentifier(), Type.type(patternClass));
                });
        return types;
    }
}
