package org.drools.drlx.builder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.drools.base.rule.TypeDeclaration;
import org.drools.base.rule.constraint.Constraint;
import org.drools.base.util.PropertyReactivityUtil;
import org.drools.drlx.builder.DrlxLambdaCompiler.BoundVariable;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.ruleunits.api.DataSource;
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
        // Mirror the same imports into the lambda compiler so MVEL3's eval/consequence
        // batch compilation can resolve external types (e.g. enum constants) that the
        // pattern path resolves implicitly via MVEL.pojo(patternType, ...).
        lambdaCompiler.addImports(parseResult.imports());

        Class<?> unitClass = resolveUnitClass(parseResult.unitName(),
                                              parseResult.imports(),
                                              pkg.getTypeResolver());
        Map<String, Class<?>> entryPointTypes = buildEntryPointTypeMap(unitClass);

        Map<String, KnowledgePackageImpl> typeDeclPackages = new LinkedHashMap<>();
        registerTypeDeclarations(typeDeclPackages, parseResult, pkg.getTypeResolver(), entryPointTypes, unitClass);

        parseResult.rules().forEach(rule ->
                pkg.addRule(buildRule(rule, pkg.getTypeResolver(), entryPointTypes, unitClass)));

        List<KiePackage> out = new ArrayList<>();
        out.add(pkg);
        out.addAll(typeDeclPackages.values());
        return out;
    }

    // Register a TypeDeclaration per pattern class so property reactivity
    // (watch list) works at runtime. Mirrors KiePackagesBuilder (executable
    // model): the declaration goes into the class's OWN package, defaults to
    // PropertySpecificOption.ALWAYS.
    private static void registerTypeDeclarations(Map<String, KnowledgePackageImpl> typeDeclPackages,
                                                 CompilationUnitIR parseResult,
                                                 TypeResolver typeResolver,
                                                 Map<String, Class<?>> entryPointTypes,
                                                 Class<?> unitClass) {
        Set<Class<?>> patternClasses = new HashSet<>();
        for (RuleIR rule : parseResult.rules()) {
            collectPatternClasses(rule.lhs(), patternClasses, typeResolver, entryPointTypes, unitClass);
        }
        for (Class<?> cls : patternClasses) {
            if (cls.getPackage() == null) {
                continue;
            }
            String pkgName = cls.getPackage().getName();
            KnowledgePackageImpl cls_pkg = typeDeclPackages.computeIfAbsent(pkgName, n -> {
                KnowledgePackageImpl p = new KnowledgePackageImpl(n);
                p.setClassLoader(Thread.currentThread().getContextClassLoader());
                return p;
            });
            if (cls_pkg.getExactTypeDeclaration(cls) != null) {
                continue;
            }
            TypeDeclaration td = TypeDeclaration.createTypeDeclarationForBean(
                    cls, PropertySpecificOption.ALWAYS);
            cls_pkg.addTypeDeclaration(td);
        }
    }

    private static void collectPatternClasses(List<LhsItemIR> items,
                                              Set<Class<?>> classes,
                                              TypeResolver typeResolver,
                                              Map<String, Class<?>> entryPointTypes,
                                              Class<?> unitClass) {
        for (LhsItemIR item : items) {
            if (item instanceof PatternIR p) {
                classes.add(resolvePatternType(p, typeResolver, entryPointTypes, unitClass));
            } else if (item instanceof GroupElementIR g) {
                collectPatternClasses(g.children(), classes, typeResolver, entryPointTypes, unitClass);
            }
        }
    }

    private static Class<?> resolveUnitClass(String unitName,
                                             List<String> imports,
                                             TypeResolver typeResolver) {
        if (unitName == null || unitName.isEmpty()) {
            throw new RuntimeException(
                    "DRLX compilation unit is missing a 'unit <Name>;' declaration");
        }
        if (unitName.contains(".")) {
            return resolveOrThrow(unitName, typeResolver);
        }
        for (String imp : imports) {
            String simple = imp.substring(imp.lastIndexOf('.') + 1);
            if (simple.equals(unitName)) {
                return resolveOrThrow(imp, typeResolver);
            }
        }
        throw new RuntimeException(
                "Unit class '" + unitName + "' not found — add `import <fqn>." + unitName
                + ";` to the DRLX source.");
    }

    private static Class<?> resolveOrThrow(String fqn, TypeResolver typeResolver) {
        try {
            return typeResolver.resolveType(fqn);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unit class '" + fqn + "' not on classpath", e);
        }
    }

    private static Map<String, Class<?>> buildEntryPointTypeMap(Class<?> unitClass) {
        Map<String, Class<?>> map = new LinkedHashMap<>();
        for (Field field : unitClass.getDeclaredFields()) {
            if (!DataSource.class.isAssignableFrom(field.getType())) {
                continue;
            }
            Class<?> element = resolveDataSourceTypeArg(field.getGenericType());
            if (element == null) {
                throw new RuntimeException(
                        "Field '" + unitClass.getName() + "." + field.getName()
                        + "' is a raw DataSource — must be parameterised (e.g. DataStore<Person>)");
            }
            map.put(field.getName(), element);
        }
        return map;
    }

    private static Class<?> resolveDataSourceTypeArg(java.lang.reflect.Type type) {
        if (!(type instanceof ParameterizedType pt)) {
            return null;
        }
        if (!(pt.getRawType() instanceof Class<?> rawClass)) {
            return null;
        }
        if (!DataSource.class.isAssignableFrom(rawClass)) {
            return null;
        }
        if (DataSource.class.equals(rawClass)) {
            java.lang.reflect.Type[] args = pt.getActualTypeArguments();
            return (args.length == 1 && args[0] instanceof Class<?> c) ? c : null;
        }
        return resolveAgainstSuperInterface(rawClass, pt.getActualTypeArguments());
    }

    private static Class<?> resolveAgainstSuperInterface(Class<?> rawClass, java.lang.reflect.Type[] actualArgs) {
        TypeVariable<?>[] typeParams = rawClass.getTypeParameters();
        Map<String, java.lang.reflect.Type> bindings = new HashMap<>();
        for (int i = 0; i < typeParams.length && i < actualArgs.length; i++) {
            bindings.put(typeParams[i].getName(), actualArgs[i]);
        }
        for (java.lang.reflect.Type iface : rawClass.getGenericInterfaces()) {
            if (!(iface instanceof ParameterizedType ptIface)) continue;
            java.lang.reflect.Type raw = ptIface.getRawType();
            if (!DataSource.class.equals(raw)) continue;
            java.lang.reflect.Type tArg = ptIface.getActualTypeArguments()[0];
            if (tArg instanceof TypeVariable<?> tv) {
                java.lang.reflect.Type bound = bindings.get(tv.getName());
                return (bound instanceof Class<?> c) ? c : null;
            }
            if (tArg instanceof Class<?> c) {
                return c;
            }
        }
        java.lang.reflect.Type superType = rawClass.getGenericSuperclass();
        if (superType instanceof ParameterizedType ptSuper
                && ptSuper.getRawType() instanceof Class<?> superClass) {
            return resolveAgainstSuperInterface(superClass, ptSuper.getActualTypeArguments());
        }
        return null;
    }

    private static Class<?> resolvePatternType(PatternIR p,
                                               TypeResolver typeResolver,
                                               Map<String, Class<?>> entryPointTypes,
                                               Class<?> unitClass) {
        if (p.castTypeName() != null) {
            return resolveOrThrow(p.castTypeName(), typeResolver);
        }

        Class<?> inferred = entryPointTypes.get(p.entryPoint());
        boolean isBare = p.typeName() == null || p.typeName().isBlank() || "var".equals(p.typeName());

        if (isBare) {
            if (inferred == null) {
                throw new RuntimeException(
                        "no type could be inferred for entry point '" + p.entryPoint()
                        + "' — declare `DataSource<T> " + p.entryPoint() + ";` on unit class '"
                        + unitClass.getName() + "'");
            }
            return inferred;
        }

        if (inferred == null) {
            throw new RuntimeException(
                    "entry point '" + p.entryPoint() + "' is not declared on unit class '"
                    + unitClass.getName() + "' — add `DataSource<T> " + p.entryPoint() + ";`");
        }

        Class<?> explicit;
        try {
            explicit = typeResolver.resolveType(p.typeName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "pattern type '" + p.typeName() + "' could not be resolved", e);
        }
        if (!inferred.equals(explicit)) {
            throw new RuntimeException(
                    "pattern type '" + p.typeName() + "' on entry point '" + p.entryPoint()
                    + "' does not match unit-class declaration '" + inferred.getName() + "'");
        }
        return explicit;
    }

    private RuleImpl buildRule(RuleIR parseResult,
                               TypeResolver typeResolver,
                               Map<String, Class<?>> entryPointTypes,
                               Class<?> unitClass) {
        lambdaCompiler.beginRule(parseResult.name());

        RuleImpl rule = new RuleImpl(parseResult.name());
        rule.setResource(rule.getResource());
        applyAnnotations(rule, parseResult.annotations());

        GroupElement root = GroupElementFactory.newAndInstance();
        Map<String, BoundVariable> boundVariables = new LinkedHashMap<>();

        buildLhs(parseResult.lhs(), root, typeResolver, entryPointTypes, unitClass, boundVariables);

        if (parseResult.rhs() != null) {
            Map<String, Type<?>> types = lambdaCompiler.getTypeMap(root);
            rule.setConsequence(lambdaCompiler.createLambdaConsequence(parseResult.rhs().block(), types));
        }

        rule.setLhs(root);
        return rule;
    }

    private void buildLhs(List<LhsItemIR> items,
                          GroupElement parent,
                          TypeResolver typeResolver,
                          Map<String, Class<?>> entryPointTypes,
                          Class<?> unitClass,
                          Map<String, BoundVariable> boundVariables) {
        for (LhsItemIR item : items) {
            if (item instanceof PatternIR patternIr) {
                Pattern pattern = buildPattern(patternIr, typeResolver, entryPointTypes, unitClass, boundVariables);
                parent.addChild(pattern);
                Declaration declaration = pattern.getDeclaration();
                if (declaration != null) {
                    Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
                    boundVariables.put(declaration.getIdentifier(),
                            new BoundVariable(declaration.getIdentifier(), patternClass, pattern));
                }
            } else if (item instanceof GroupElementIR group) {
                GroupElement ge = switch (group.kind()) {
                    case NOT    -> GroupElementFactory.newNotInstance();
                    case EXISTS -> GroupElementFactory.newExistsInstance();
                    case AND    -> GroupElementFactory.newAndInstance();
                    case OR     -> GroupElementFactory.newOrInstance();
                };
                // Drools enforces exactly one child on both NOT and EXISTS
                // (GroupElement.addChild). For multi-element forms wrap the
                // children in an AND so the group element has exactly one
                // child that represents the conjunction.
                if ((group.kind() == GroupElementIR.Kind.NOT
                        || group.kind() == GroupElementIR.Kind.EXISTS)
                        && group.children().size() > 1) {
                    GroupElement andInstance = GroupElementFactory.newAndInstance();
                    buildLhs(group.children(), andInstance, typeResolver, entryPointTypes, unitClass, boundVariables);
                    ge.addChild(andInstance);
                } else {
                    buildLhs(group.children(), ge, typeResolver, entryPointTypes, unitClass, boundVariables);
                }
                parent.addChild(ge);
            } else if (item instanceof EvalIR evalIr) {
                buildEvalCondition(evalIr, parent, boundVariables);
            } else {
                throw new IllegalArgumentException("Unsupported LHS item: " + item.getClass().getName());
            }
        }
    }

    private void buildEvalCondition(EvalIR evalIr,
                                    GroupElement parent,
                                    Map<String, BoundVariable> boundVariables) {
        // Resolve referenced binding names against the live boundVariables map.
        // Names that don't resolve are silently dropped (e.g., Java keywords or
        // type names picked up by the visitor's identifier regex).
        List<BoundVariable> referenced = new ArrayList<>();
        List<Declaration> declarations = new ArrayList<>();
        for (String name : evalIr.referencedBindings()) {
            BoundVariable bv = boundVariables.get(name);
            if (bv != null) {
                referenced.add(bv);
                declarations.add(bv.pattern().getDeclaration());
            }
        }

        DrlxEvalExpression evalExpression =
                lambdaCompiler.createEvalExpression(evalIr.expression(), referenced);

        org.drools.base.rule.EvalCondition evalCondition =
                new org.drools.base.rule.EvalCondition(
                        evalExpression,
                        declarations.toArray(new Declaration[0]));
        parent.addChild(evalCondition);
    }

    private Pattern buildPattern(PatternIR parseResult,
                                 TypeResolver typeResolver,
                                 Map<String, Class<?>> entryPointTypes,
                                 Class<?> unitClass,
                                 Map<String, BoundVariable> boundVariables) {
        Class<?> type = resolvePatternType(parseResult, typeResolver, entryPointTypes, unitClass);
        ObjectType objectType = new ClassObjectType(type, false);

        Pattern pattern = new Pattern(lambdaCompiler.nextPatternId(), 0, 0, objectType, parseResult.bindName(), false);
        pattern.setPassive(parseResult.passive());
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

        if (!parseResult.watchedProperties().isEmpty()) {
            List<String> validated = validateWatchedProperties(
                    parseResult.watchedProperties(), patternClass, parseResult.typeName());
            pattern.addWatchedProperties(validated);
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

    private static List<String> validateWatchedProperties(List<String> raw,
                                                          Class<?> patternClass,
                                                          String typeLabel) {
        List<String> accessible = PropertyReactivityUtil.getAccessibleProperties(patternClass);
        List<String> result = new ArrayList<>();
        for (String item : raw) {
            if (item.equals("*") || item.equals("!*")) {
                if (result.contains("*") || result.contains("!*")) {
                    throw new RuntimeException("Duplicate usage of wildcard " + item
                            + " in watch list on " + typeLabel);
                }
                result.add(item);
                continue;
            }
            boolean neg = item.startsWith("!");
            String name = neg ? item.substring(1) : item;
            if (!accessible.contains(name)) {
                throw new RuntimeException("Unknown property '" + name
                        + "' in watch list on " + typeLabel);
            }
            if (result.contains(name) || result.contains("!" + name)) {
                throw new RuntimeException("Duplicate property '" + name
                        + "' in watch list on " + typeLabel);
            }
            result.add(item);
        }
        return result;
    }
}
