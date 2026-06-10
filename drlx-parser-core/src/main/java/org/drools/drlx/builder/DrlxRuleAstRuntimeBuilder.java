package org.drools.drlx.builder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.io.Serializable;
import java.util.function.Function;

import org.drools.core.rule.consequence.InternalMatch;
import org.drools.base.base.ClassObjectType;
import org.drools.base.base.EnabledBoolean;
import org.drools.base.base.ObjectType;
import org.drools.base.base.SalienceInteger;
import org.drools.base.base.extractors.ArrayElementReader;
import org.drools.base.base.extractors.SelfReferenceClassFieldReader;
import org.drools.base.definitions.impl.KnowledgePackageImpl;
import org.drools.base.definitions.rule.impl.QueryImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElementFactory;
import org.drools.base.rule.ImportDeclaration;
import org.drools.base.rule.MultiAccumulate;
import org.drools.base.rule.MutableTypeConstraint;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.SingleAccumulate;
import org.drools.base.rule.TypeDeclaration;
import org.drools.base.time.TimeUtils;
import org.drools.core.time.impl.DurationTimer;
import org.drools.core.time.TimerExpression;
import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.compiler.rule.builder.RuleBuilder;
import org.drools.core.rule.SlidingLengthWindow;
import org.drools.core.rule.SlidingTimeWindow;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.rule.QueryArgument;
import org.drools.base.rule.QueryElement;
import org.drools.base.rule.constraint.Constraint;
import org.drools.base.rule.constraint.QueryNameConstraint;
import org.drools.base.util.PropertyReactivityUtil;
import org.drools.drlx.builder.DrlxLambdaCompiler.BoundVariable;
import org.drools.drlx.runtime.QueryResultRow;
import org.drools.drlx.runtime.QueryResultRowReader;
import org.kie.api.definition.type.Role;
import org.kie.api.runtime.rule.AccumulateFunction;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.drools.drlx.builder.DrlxRuleAstModel.AccumulatePatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.AccumulatorIR;
import org.drools.drlx.builder.DrlxRuleAstModel.CustomAccumulateIR;
import org.drools.drlx.builder.DrlxRuleAstModel.InitVarIR;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleParameterIR;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.util.TypeResolver;

import org.mvel3.parser.antlr4.Antlr4MvelParser;
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
        entryPointTypes.keySet().forEach(pkg::addEntryPointId);
        Map<String, java.lang.reflect.Type> globalTypes = buildGlobalTypeMap(unitClass);
        globalTypes.forEach(pkg::addGlobal);

        Set<String> dataStoreGlobalNames = globalTypes.entrySet().stream()
                .filter(e -> {
                    Class<?> raw = erasure(e.getValue());
                    return raw != null && DataStore.class.isAssignableFrom(raw);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        DataStoreUpdateRewriter updateRewriter = new DataStoreUpdateRewriter(new Antlr4MvelParser());

        Map<String, KnowledgePackageImpl> typeDeclPackages = new LinkedHashMap<>();
        registerTypeDeclarations(typeDeclPackages, parseResult, pkg.getTypeResolver(), entryPointTypes, unitClass);

        Map<String, QueryImpl> queryRegistry = new LinkedHashMap<>();

        for (RuleIR rule : parseResult.rules()) {
            if (!rule.parameters().isEmpty()) {
                String defaultName = Character.toLowerCase(rule.name().charAt(0)) + rule.name().substring(1);
                String entryPointName = rule.annotations().stream()
                        .filter(a -> a.kind() == RuleAnnotationIR.Kind.DATASOURCE)
                        .map(RuleAnnotationIR::rawValue)
                        .findFirst()
                        .orElse(defaultName);
                QueryImpl query = new QueryImpl(rule.name());
                queryRegistry.put(entryPointName, query);
                if (!entryPointName.equals(defaultName)) {
                    queryRegistry.put(defaultName, query);
                }
                buildQuery(query, rule, pkg.getTypeResolver(), entryPointTypes, unitClass, queryRegistry);
                pkg.addRule(query);
            }
        }

        for (RuleIR rule : parseResult.rules()) {
            if (rule.parameters().isEmpty()) {
                if (rule.annotations().stream().anyMatch(a -> a.kind() == RuleAnnotationIR.Kind.DATASOURCE)) {
                    throw new RuntimeException(
                            "@DataSource is only allowed on query rules (rules with parameters)"
                            + " — rule '" + rule.name() + "' has no parameters");
                }
                pkg.addRule(buildRule(rule, pkg.getTypeResolver(), entryPointTypes, unitClass,
                                     globalTypes, dataStoreGlobalNames, updateRewriter, queryRegistry));
            }
        }

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
                // Skip patterns that look like query invocations (have positional args
                // and their entry point is not a known data source). Type declarations
                // for query results are not needed since QueryElement produces Object[].
                if (!p.positionalArgs().isEmpty() && !entryPointTypes.containsKey(p.entryPoint())) {
                    continue;
                }
                classes.add(resolvePatternType(p, typeResolver, entryPointTypes, unitClass));
            } else if (item instanceof GroupElementIR g) {
                collectPatternClasses(g.children(), classes, typeResolver, entryPointTypes, unitClass);
            } else if (item instanceof AccumulatePatternIR accPat) {
                if (accPat.source() instanceof PatternIR p) {
                    classes.add(resolvePatternType(p, typeResolver, entryPointTypes, unitClass));
                } else if (accPat.source() instanceof GroupElementIR g) {
                    collectPatternClasses(g.children(), classes, typeResolver, entryPointTypes, unitClass);
                }
            } else if (item instanceof CustomAccumulateIR customAcc) {
                if (customAcc.source() instanceof PatternIR p) {
                    classes.add(resolvePatternType(p, typeResolver, entryPointTypes, unitClass));
                } else if (customAcc.source() instanceof GroupElementIR g) {
                    collectPatternClasses(g.children(), classes, typeResolver, entryPointTypes, unitClass);
                }
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

    static Map<String, java.lang.reflect.Type> buildGlobalTypeMap(Class<?> unitClass) {
        Map<String, java.lang.reflect.Type> map = new LinkedHashMap<>();
        for (Field field : unitClass.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isPublic(mods) || Modifier.isStatic(mods)) {
                continue;
            }
            map.put(field.getName(), field.getGenericType());
        }
        return map;
    }

    private static Class<?> erasure(java.lang.reflect.Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            return c;
        }
        return null;
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
                               Class<?> unitClass,
                               Map<String, java.lang.reflect.Type> globalTypes,
                               Set<String> dataStoreGlobalNames,
                               DataStoreUpdateRewriter updateRewriter,
                               Map<String, QueryImpl> queryRegistry) {
        lambdaCompiler.beginRule(parseResult.name());

        RuleImpl rule = new RuleImpl(parseResult.name());
        rule.setResource(rule.getResource());
        applyAnnotations(rule, parseResult.annotations());

        GroupElement root = GroupElementFactory.newAndInstance();
        Map<String, BoundVariable> boundVariables = new LinkedHashMap<>();

        buildLhs(parseResult.lhs(), root, typeResolver, entryPointTypes, unitClass, boundVariables, queryRegistry, null);

        if (parseResult.rhs() != null) {
            Map<String, Type<?>> types = lambdaCompiler.getTypeMap(root);
            for (Map.Entry<String, java.lang.reflect.Type> e : globalTypes.entrySet()) {
                Class<?> raw = erasure(e.getValue());
                if (raw != null) {
                    types.put(e.getKey(), Type.type(raw));
                }
            }
            if (!dataStoreGlobalNames.isEmpty()) {
                types.put("__match__", Type.type(InternalMatch.class));
            }
            String body = updateRewriter.rewrite(parseResult.rhs().block(), dataStoreGlobalNames);
            rule.setConsequence(lambdaCompiler.createLambdaConsequence(body, types, globalTypes.keySet()));
        }

        rule.setLhs(root);
        return rule;
    }

    private void buildQuery(QueryImpl query,
                            RuleIR parseResult,
                            TypeResolver typeResolver,
                            Map<String, Class<?>> entryPointTypes,
                            Class<?> unitClass,
                            Map<String, QueryImpl> queryRegistry) {
        lambdaCompiler.beginRule(parseResult.name());

        Pattern prefixPattern = new Pattern(lambdaCompiler.nextPatternId(), 0, 0,
                ClassObjectType.DroolsQuery_ObjectType, null);

        QueryNameConstraint nameConstraint = new QueryNameConstraint(null, query.getName());
        prefixPattern.addConstraint(nameConstraint);

        List<RuleParameterIR> params = parseResult.parameters();
        Declaration[] paramDecls = new Declaration[params.size()];
        for (int i = 0; i < params.size(); i++) {
            RuleParameterIR param = params.get(i);
            Class<?> paramType = resolveOrThrow(param.typeName(), typeResolver);
            Declaration decl = prefixPattern.addDeclaration(param.paramName());
            ArrayElementReader reader = new ArrayElementReader(
                    DroolsQueryElementsReader.INSTANCE, i, paramType);
            decl.setReadAccessor(reader);
            paramDecls[i] = decl;
        }
        query.setParameters(paramDecls);

        GroupElement root = GroupElementFactory.newAndInstance();
        root.addChild(prefixPattern);

        Map<String, BoundVariable> boundVariables = new LinkedHashMap<>();
        for (int i = 0; i < params.size(); i++) {
            RuleParameterIR param = params.get(i);
            Class<?> paramType = resolveOrThrow(param.typeName(), typeResolver);
            boundVariables.put(param.paramName(),
                    new BoundVariable(param.paramName(), paramType, prefixPattern, paramDecls[i]));
        }

        buildLhs(parseResult.lhs(), root, typeResolver, entryPointTypes, unitClass, boundVariables, queryRegistry, query);

        query.setLhs(root);
    }

    private void buildLhs(List<LhsItemIR> items,
                          GroupElement parent,
                          TypeResolver typeResolver,
                          Map<String, Class<?>> entryPointTypes,
                          Class<?> unitClass,
                          Map<String, BoundVariable> boundVariables,
                          Map<String, QueryImpl> queryRegistry,
                          QueryImpl currentQuery) {
        for (LhsItemIR item : items) {
            if (item instanceof PatternIR patternIr) {
                QueryImpl targetQuery = queryRegistry.get(patternIr.entryPoint());
                if (targetQuery != null && !patternIr.positionalArgs().isEmpty()) {
                    if (!patternIr.conditions().isEmpty()) {
                        throw new RuntimeException(
                                "query '" + targetQuery.getName()
                                + "' cannot mix positional arguments (...) with named access [...]");
                    }
                    // Self-referencing query disambiguation heuristic:
                    // When a query's LHS references its own entry point with positional args,
                    // decide Pattern vs QueryElement based on whether any INPUT argument is
                    // a variable NOT in the query's parameter list.
                    //
                    // - All input variable args are query params -> Pattern (base case fact match)
                    // - Any input variable arg is locally bound (not a param) -> QueryElement (recursive call)
                    //
                    // This heuristic produces the same RETE structure as old DRL where
                    // Trust(;a, b) is a Pattern and trusts(;z, b) is a QueryElement.
                    // It may be revisited in the future for more complex recursive patterns.
                    if (targetQuery == currentQuery
                            && !hasNonParameterInput(patternIr.positionalArgs(), currentQuery, boundVariables)) {
                        // Pattern path: positional match on DataStore type (base case)
                        Pattern pattern = buildSelfReferencePattern(
                                patternIr, typeResolver, entryPointTypes, unitClass, boundVariables, currentQuery);
                        parent.addChild(pattern);
                        Declaration declaration = pattern.getDeclaration();
                        if (declaration != null) {
                            Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
                            boundVariables.put(declaration.getIdentifier(),
                                    new BoundVariable(declaration.getIdentifier(), patternClass, pattern, declaration));
                        }
                        continue;
                    }
                    // QueryElement path: recursive query call or invocation of a different query
                    QueryElement queryElement = buildQueryElement(
                            patternIr, targetQuery, boundVariables);
                    parent.addChild(queryElement);
                    Declaration[] queryParams = targetQuery.getParameters();
                    List<String> args = patternIr.positionalArgs();
                    for (int i = 0; i < args.size(); i++) {
                        String arg = args.get(i);
                        String varName = null;
                        if (arg.startsWith("var ")) {
                            varName = arg.substring(4).trim();
                        } else if (!boundVariables.containsKey(arg) && isSimpleIdentifier(arg)) {
                            varName = arg;
                        }
                        if (varName != null) {
                            Class<?> paramType = queryParams[i].getDeclarationClass();
                            Pattern resultPattern = queryElement.getResultPattern();
                            Declaration decl = resultPattern.getDeclarations().get(varName);
                            if (decl != null) {
                                boundVariables.put(varName,
                                        new BoundVariable(varName, paramType, resultPattern, decl));
                            }
                        }
                    }
                    if (patternIr.bindName() != null) {
                        Map<String, Integer> nameToIndex = new LinkedHashMap<>();
                        Declaration[] qParams = targetQuery.getParameters();
                        for (int i = 0; i < qParams.length; i++) {
                            nameToIndex.put(qParams[i].getIdentifier(), i);
                        }
                        QueryResultRowReader rowReader = new QueryResultRowReader(nameToIndex);
                        Pattern resultPattern = queryElement.getResultPattern();
                        Declaration rowDecl = new Declaration(patternIr.bindName(), rowReader, resultPattern);
                        resultPattern.addDeclaration(rowDecl);
                        boundVariables.put(patternIr.bindName(),
                                new BoundVariable(patternIr.bindName(), QueryResultRow.class, resultPattern, rowDecl));
                    }
                    continue;
                }
                if (targetQuery != null && !patternIr.conditions().isEmpty()) {
                    // Named access path
                    if (targetQuery == currentQuery) {
                        throw new RuntimeException(
                                "self-referencing query '" + targetQuery.getName()
                                + "' cannot use named access; use positional syntax instead");
                    }
                    List<String> orderedArgs = buildNamedQueryArgs(patternIr.conditions(), targetQuery);
                    PatternIR positionalIr = new PatternIR(
                            patternIr.typeName(), patternIr.bindName(), patternIr.entryPoint(),
                            List.of(), patternIr.temporalConditions(), patternIr.castTypeName(),
                            orderedArgs, patternIr.passive(), patternIr.watchedProperties(),
                            patternIr.windowType(), patternIr.windowParameter());
                    QueryElement queryElement = buildQueryElement(positionalIr, targetQuery, boundVariables);
                    parent.addChild(queryElement);
                    Declaration[] queryParams = targetQuery.getParameters();
                    List<String> args = orderedArgs;
                    for (int i = 0; i < args.size(); i++) {
                        String arg = args.get(i);
                        String varName = null;
                        if (arg.startsWith("var ")) {
                            varName = arg.substring(4).trim();
                        } else if (!boundVariables.containsKey(arg) && isSimpleIdentifier(arg)) {
                            varName = arg;
                        }
                        if (varName != null) {
                            Class<?> paramType = queryParams[i].getDeclarationClass();
                            Pattern resultPattern = queryElement.getResultPattern();
                            Declaration decl = resultPattern.getDeclarations().get(varName);
                            if (decl != null) {
                                boundVariables.put(varName,
                                        new BoundVariable(varName, paramType, resultPattern, decl));
                            }
                        }
                    }
                    if (patternIr.bindName() != null) {
                        Map<String, Integer> nameToIndex = new LinkedHashMap<>();
                        Declaration[] qParams = targetQuery.getParameters();
                        for (int i = 0; i < qParams.length; i++) {
                            nameToIndex.put(qParams[i].getIdentifier(), i);
                        }
                        QueryResultRowReader rowReader = new QueryResultRowReader(nameToIndex);
                        Pattern resultPattern = queryElement.getResultPattern();
                        Declaration rowDecl = new Declaration(patternIr.bindName(), rowReader, resultPattern);
                        resultPattern.addDeclaration(rowDecl);
                        boundVariables.put(patternIr.bindName(),
                                new BoundVariable(patternIr.bindName(), QueryResultRow.class, resultPattern, rowDecl));
                    }
                    continue;
                }
                Pattern pattern = buildPattern(patternIr, typeResolver, entryPointTypes, unitClass, boundVariables);
                parent.addChild(pattern);
                Declaration declaration = pattern.getDeclaration();
                if (declaration != null) {
                    Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
                    boundVariables.put(declaration.getIdentifier(),
                            new BoundVariable(declaration.getIdentifier(), patternClass, pattern, declaration));
                }
            } else if (item instanceof GroupElementIR group) {
                GroupElement ge = switch (group.kind()) {
                    case NOT    -> GroupElementFactory.newNotInstance();
                    case EXISTS -> GroupElementFactory.newExistsInstance();
                    case AND    -> GroupElementFactory.newAndInstance();
                    case OR     -> GroupElementFactory.newOrInstance();
                };
                // OR/NOT/EXISTS inner bindings are branch-local and must not
                // leak to the outer scope. AND bindings propagate normally.
                boolean scopeIsolated = group.kind() != GroupElementIR.Kind.AND;
                Map<String, BoundVariable> innerScope = scopeIsolated
                        ? new LinkedHashMap<>(boundVariables) : boundVariables;
                // Drools enforces exactly one child on both NOT and EXISTS
                // (GroupElement.addChild). For multi-element forms wrap the
                // children in an AND so the group element has exactly one
                // child that represents the conjunction.
                if ((group.kind() == GroupElementIR.Kind.NOT
                        || group.kind() == GroupElementIR.Kind.EXISTS)
                        && group.children().size() > 1) {
                    GroupElement andInstance = GroupElementFactory.newAndInstance();
                    buildLhs(group.children(), andInstance, typeResolver, entryPointTypes, unitClass, innerScope, queryRegistry, currentQuery);
                    ge.addChild(andInstance);
                } else {
                    buildLhs(group.children(), ge, typeResolver, entryPointTypes, unitClass, innerScope, queryRegistry, currentQuery);
                }
                parent.addChild(ge);
            } else if (item instanceof EvalIR evalIr) {
                buildEvalCondition(evalIr, parent, boundVariables);
            } else if (item instanceof AccumulatePatternIR accPat) {
                buildAccumulatePattern(accPat, parent, typeResolver, entryPointTypes,
                                       unitClass, boundVariables, queryRegistry, currentQuery);
            } else if (item instanceof CustomAccumulateIR customAcc) {
                buildCustomAccumulatePattern(customAcc, parent, typeResolver, entryPointTypes,
                                              unitClass, boundVariables, queryRegistry, currentQuery);
            } else {
                throw new IllegalArgumentException("Unsupported LHS item: " + item.getClass().getName());
            }
        }
    }

    /**
     * Lower an {@link AccumulatePatternIR} into either one {@link SingleAccumulate}
     * (N=1) wrapped in a typed result Pattern, or one {@link MultiAccumulate}
     * (N>1) wrapped in an unnamed {@code Object[]} Pattern carrying N
     * {@link ArrayElementReader}-backed declarations — mirrors Drools'
     * {@code KiePackagesBuilder.createAccumulate} convention.
     * <p>
     * The source pattern's binding ({@code p}) is internal to the accumulate
     * scope and is NOT added to {@code outerScope}; only the result bindings
     * (e.g. {@code avgAge}) are.
     */
    private void buildAccumulatePattern(AccumulatePatternIR accPat,
                                        GroupElement parent,
                                        TypeResolver typeResolver,
                                        Map<String, Class<?>> entryPointTypes,
                                        Class<?> unitClass,
                                        Map<String, BoundVariable> outerScope,
                                        Map<String, QueryImpl> queryRegistry,
                                        QueryImpl currentQuery) {
        LhsItemIR srcIr = accPat.source();

        Map<String, BoundVariable> innerScope = new java.util.LinkedHashMap<>(outerScope);
        org.drools.base.rule.RuleConditionElement srcElement;
        boolean multiSource;

        if (srcIr instanceof GroupElementIR groupIr
                && groupIr.children().size() == 1
                && groupIr.children().get(0) instanceof PatternIR) {
            srcIr = groupIr.children().get(0);
        }

        if (srcIr instanceof PatternIR patIr) {
            Pattern srcPattern = buildPattern(patIr, typeResolver, entryPointTypes, unitClass, outerScope);
            srcElement = srcPattern;
            multiSource = false;
            Declaration srcDecl = srcPattern.getDeclaration();
            if (srcDecl != null) {
                Class<?> srcClass = ((ClassObjectType) srcPattern.getObjectType()).getClassType();
                innerScope.put(srcDecl.getIdentifier(),
                        new BoundVariable(srcDecl.getIdentifier(), srcClass, srcPattern, srcDecl));
            }
        } else if (srcIr instanceof GroupElementIR groupIr) {
            GroupElement andGroup = GroupElementFactory.newAndInstance();
            buildLhs(groupIr.children(), andGroup, typeResolver, entryPointTypes,
                     unitClass, innerScope, queryRegistry, currentQuery);
            srcElement = andGroup;
            multiSource = true;
        } else {
            throw new IllegalArgumentException("Unsupported accumulate source: " + srcIr.getClass());
        }

        List<AccumulatorIR> accumulators = accPat.accumulators();
        int n = accumulators.size();

        Map<String, BoundVariable> sourceScope = null;
        if (multiSource) {
            sourceScope = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, BoundVariable> e : innerScope.entrySet()) {
                if (!outerScope.containsKey(e.getKey())) {
                    sourceScope.put(e.getKey(), e.getValue());
                }
            }
        }

        org.drools.base.rule.accessor.Accumulator[] accs =
                new org.drools.base.rule.accessor.Accumulator[n];
        for (int i = 0; i < n; i++) {
            if (multiSource) {
                accs[i] = buildSingleAccumulatorMulti(accumulators.get(i), sourceScope, typeResolver);
            } else {
                Declaration srcDecl = ((Pattern) srcElement).getDeclaration();
                Class<?> srcClass = ((ClassObjectType) ((Pattern) srcElement).getObjectType()).getClassType();
                String srcBindingName = srcDecl != null ? srcDecl.getIdentifier() : null;
                accs[i] = buildSingleAccumulator(accumulators.get(i), srcClass, srcBindingName, typeResolver);
            }
        }

        Pattern wrap;
        if (n == 1) {
            Declaration[] required = multiSource
                    ? new Declaration[0]
                    : requiredFor(accumulators.get(0), innerScope);
            SingleAccumulate single = new SingleAccumulate(srcElement, required, accs[0]);
            wrap = wrapResultPattern(accumulators.get(0), single, typeResolver);
        } else {
            MultiAccumulate multi = new MultiAccumulate(srcElement, new Declaration[0], accs, n);
            wrap = wrapMultiResultPattern(accumulators, multi, typeResolver);
        }

        parent.addChild(wrap);

        for (int i = 0; i < n; i++) {
            AccumulatorIR acc = accumulators.get(i);
            Class<?> resultClass = resultClassFor(acc, typeResolver);
            Declaration decl = wrap.getDeclarations().get(acc.resultBindName());
            outerScope.put(acc.resultBindName(),
                    new BoundVariable(acc.resultBindName(), resultClass, wrap, decl));
        }
    }

    private void buildCustomAccumulatePattern(CustomAccumulateIR customAcc,
                                               GroupElement parent,
                                               TypeResolver typeResolver,
                                               Map<String, Class<?>> entryPointTypes,
                                               Class<?> unitClass,
                                               Map<String, BoundVariable> outerScope,
                                               Map<String, QueryImpl> queryRegistry,
                                               QueryImpl currentQuery) {
        LhsItemIR srcIr = customAcc.source();

        if (srcIr instanceof GroupElementIR groupIr
                && groupIr.children().size() == 1
                && groupIr.children().get(0) instanceof PatternIR) {
            srcIr = groupIr.children().get(0);
        }

        Map<String, BoundVariable> innerScope = new java.util.LinkedHashMap<>(outerScope);
        org.drools.base.rule.RuleConditionElement srcElement;
        boolean multiSource;

        if (srcIr instanceof PatternIR patIr) {
            Pattern srcPattern = buildPattern(patIr, typeResolver, entryPointTypes, unitClass, outerScope);
            srcElement = srcPattern;
            multiSource = false;
            Declaration srcDecl = srcPattern.getDeclaration();
            if (srcDecl != null) {
                Class<?> srcClass = ((ClassObjectType) srcPattern.getObjectType()).getClassType();
                innerScope.put(srcDecl.getIdentifier(),
                        new BoundVariable(srcDecl.getIdentifier(), srcClass, srcPattern, srcDecl));
            }
        } else if (srcIr instanceof GroupElementIR groupIr) {
            GroupElement andGroup = GroupElementFactory.newAndInstance();
            buildLhs(groupIr.children(), andGroup, typeResolver, entryPointTypes,
                     unitClass, innerScope, queryRegistry, currentQuery);
            srcElement = andGroup;
            multiSource = true;
        } else {
            throw new IllegalArgumentException("Unsupported accumulate source: " + srcIr.getClass());
        }

        // Reject outer-binding references in action/reverse/result blocks (#54)
        java.util.Set<String> allowedNames = new java.util.LinkedHashSet<>();
        if (multiSource) {
            for (Map.Entry<String, BoundVariable> e : innerScope.entrySet()) {
                if (!outerScope.containsKey(e.getKey())) {
                    allowedNames.add(e.getKey());
                }
            }
        } else {
            Declaration srcDecl = ((Pattern) srcElement).getDeclaration();
            if (srcDecl != null) allowedNames.add(srcDecl.getIdentifier());
        }
        for (InitVarIR iv : customAcc.initVars()) {
            allowedNames.add(iv.name());
        }
        for (String ref : customAcc.referencedBindings()) {
            if (!allowedNames.contains(ref) && outerScope.containsKey(ref)) {
                throw new RuntimeException(
                        "outer-binding reference '" + ref + "' in custom accumulate is not yet supported (see #54)");
            }
        }

        DrlxCustomAccumulator accumulator;
        if (multiSource) {
            Map<String, BoundVariable> sourceScope = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, BoundVariable> e : innerScope.entrySet()) {
                if (!outerScope.containsKey(e.getKey())) {
                    sourceScope.put(e.getKey(), e.getValue());
                }
            }
            accumulator = lambdaCompiler.createCustomAccumulator(customAcc, sourceScope);
        } else {
            Class<?> srcClass = ((ClassObjectType) ((Pattern) srcElement).getObjectType()).getClassType();
            String srcBindingName = ((Pattern) srcElement).getDeclaration() != null
                    ? ((Pattern) srcElement).getDeclaration().getIdentifier() : null;
            accumulator = lambdaCompiler.createCustomAccumulator(customAcc, srcClass, srcBindingName);
        }

        Declaration[] required = new Declaration[0];
        SingleAccumulate single = new SingleAccumulate(srcElement, required, accumulator);

        Class<?> resultClass = resolveCustomResultType(customAcc.resultTypeName(), typeResolver);
        Pattern wrap = new Pattern(lambdaCompiler.nextPatternId(), new ClassObjectType(resultClass),
                                   customAcc.resultBindName());
        wrap.addDeclaration(new Declaration(customAcc.resultBindName(),
                new SelfReferenceClassFieldReader(resultClass), wrap, true));
        wrap.setSource(single);

        parent.addChild(wrap);

        Declaration decl = wrap.getDeclarations().get(customAcc.resultBindName());
        outerScope.put(customAcc.resultBindName(),
                new BoundVariable(customAcc.resultBindName(), resultClass, wrap, decl));
    }

    // Returns boxed classes for Drools Pattern ObjectType wrappers.
    private static Class<?> resolveCustomResultType(String typeName, TypeResolver typeResolver) {
        return switch (typeName) {
            case "int"     -> Integer.class;
            case "long"    -> Long.class;
            case "double"  -> Double.class;
            case "float"   -> Float.class;
            case "short"   -> Short.class;
            case "byte"    -> Byte.class;
            case "boolean" -> Boolean.class;
            case "char"    -> Character.class;
            default -> {
                try { yield typeResolver.resolveType(typeName); }
                catch (ClassNotFoundException e) {
                    throw new RuntimeException(
                            "cannot resolve type '" + typeName + "' in custom accumulate result — use a fully-qualified name or add an import", e);
                }
            }
        };
    }

    /**
     * Per-function construction shared by SingleAccumulate (N=1) and
     * MultiAccumulate (N>1) paths. Validates arity, builds the extractor,
     * instantiates the AccumulateFunction.
     *
     * @param srcBindingName the source binding name (e.g. {@code "p"}); may
     *                       be {@code null}, in which case any expression
     *                       argument is rejected.
     */
    private org.drools.base.rule.accessor.Accumulator buildSingleAccumulator(
            AccumulatorIR acc,
            Class<?> srcClass,
            String srcBindingName,
            TypeResolver typeResolver) {
        ResolvedFunction resolved = resolveFunction(acc.functionName(), typeResolver);

        int argCount = acc.argExpressions().size();
        if (resolved.acceptsZeroArgs()) {
            if (argCount > 1) {
                throw new RuntimeException("function '" + acc.functionName()
                        + "' accepts 0 or 1 argument, got " + argCount);
            }
        } else if (argCount != 1) {
            throw new RuntimeException("function '" + acc.functionName()
                    + "' requires exactly 1 argument, got " + argCount);
        }

        Function<Object, Object> extractor = null;
        if (argCount == 1 && !resolved.acceptsZeroArgs()) {
            if (srcBindingName == null) {
                throw new RuntimeException(
                        "accumulate source must have a binding to use expression argument '"
                                + acc.argExpressions().get(0) + "'");
            }
            extractor = lambdaCompiler.createValueExtractor(
                    acc.argExpressions().get(0), srcClass, srcBindingName);
        }

        return new DrlxLambdaAccumulator(resolved.instance(), extractor);
    }

    private org.drools.base.rule.accessor.Accumulator buildSingleAccumulatorMulti(
            AccumulatorIR acc,
            Map<String, BoundVariable> sourceScope,
            TypeResolver typeResolver) {
        ResolvedFunction resolved = resolveFunction(acc.functionName(), typeResolver);

        int argCount = acc.argExpressions().size();
        if (resolved.acceptsZeroArgs()) {
            if (argCount > 1) {
                throw new RuntimeException("function '" + acc.functionName()
                        + "' accepts 0 or 1 argument, got " + argCount);
            }
        } else if (argCount != 1) {
            throw new RuntimeException("function '" + acc.functionName()
                    + "' requires exactly 1 argument, got " + argCount);
        }

        DrlxValueExtractor multiExtractor = null;
        if (argCount == 1 && !resolved.acceptsZeroArgs()) {
            multiExtractor = lambdaCompiler.createValueExtractor(
                    acc.argExpressions().get(0), sourceScope);
        }

        return new DrlxLambdaAccumulator(resolved.instance(), multiExtractor, true);
    }

    /** Map referenced bindings through the inner scope to a Declaration[] for SingleAccumulate. */
    private static Declaration[] requiredFor(AccumulatorIR acc,
                                             Map<String, BoundVariable> innerScope) {
        return acc.referencedBindings().stream()
                .map(innerScope::get)
                .filter(java.util.Objects::nonNull)
                .map(BoundVariable::declaration)
                .filter(java.util.Objects::nonNull)
                .toArray(Declaration[]::new);
    }

    private Pattern wrapMultiResultPattern(List<AccumulatorIR> accs,
                                          MultiAccumulate multi,
                                          TypeResolver typeResolver) {
        ReadAccessor selfReader = new SelfReferenceClassFieldReader(Object[].class);
        Pattern p = new Pattern(0, new ClassObjectType(Object[].class));
        for (int i = 0; i < accs.size(); i++) {
            Class<?> rType = resultClassFor(accs.get(i), typeResolver);
            p.addDeclaration(new Declaration(
                    accs.get(i).resultBindName(),
                    new ArrayElementReader(selfReader, i, rType),
                    p,
                    true));
        }
        p.setSource(multi);
        return p;
    }

    private Pattern wrapResultPattern(AccumulatorIR acc, SingleAccumulate single,
                                      TypeResolver typeResolver) {
        Class<?> resultType = resultClassFor(acc, typeResolver);
        Pattern p = new Pattern(0, new ClassObjectType(resultType), acc.resultBindName());
        p.addDeclaration(new Declaration(acc.resultBindName(),
                new SelfReferenceClassFieldReader(resultType), p, true));
        p.setSource(single);
        return p;
    }


    private Class<?> resultClassFor(AccumulatorIR acc, TypeResolver typeResolver) {
        ResolvedFunction resolved = resolveFunction(acc.functionName(), typeResolver);
        if ("var".equals(acc.resultTypeName())) {
            return resolved.resultType();
        }
        return switch (acc.resultTypeName()) {
            case "int"     -> Integer.class;
            case "long"    -> Long.class;
            case "double"  -> Double.class;
            case "float"   -> Float.class;
            case "short"   -> Short.class;
            case "byte"    -> Byte.class;
            case "boolean" -> Boolean.class;
            case "char"    -> Character.class;
            default        -> {
                try {
                    yield Class.forName(acc.resultTypeName());
                } catch (ClassNotFoundException primitiveOrUnqualified) {
                    yield resolved.resultType();
                }
            }
        };
    }

    private record ResolvedFunction(AccumulateFunction<Serializable> instance,
                                    Class<?> resultType,
                                    boolean acceptsZeroArgs) {
    }

    @SuppressWarnings("unchecked")
    private ResolvedFunction resolveFunction(String functionName, TypeResolver typeResolver) {
        AccumulateFunctionRegistry.Resolution builtIn = AccumulateFunctionRegistry.resolve(functionName);
        if (builtIn != null) {
            try {
                AccumulateFunction<Serializable> fn =
                        (AccumulateFunction<Serializable>) builtIn.functionClass()
                                .getDeclaredConstructor().newInstance();
                return new ResolvedFunction(fn, builtIn.resultType(), builtIn.acceptsZeroArgs());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("cannot instantiate " + builtIn.functionClass(), e);
            }
        }

        int dot = functionName.lastIndexOf('.');
        String className = functionName.substring(0, dot);
        String fieldName = functionName.substring(dot + 1);

        Class<?> containerClass;
        try {
            containerClass = typeResolver.resolveType(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "cannot resolve accumulate function class '" + className
                    + "' — ensure it is imported");
        }

        java.lang.reflect.Field field;
        try {
            field = containerClass.getField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(
                    "class '" + className + "' has no static AccumulateFunction field named '"
                    + fieldName + "'");
        }

        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            throw new RuntimeException(
                    "class '" + className + "' has no static AccumulateFunction field named '"
                    + fieldName + "'");
        }

        Object value;
        try {
            value = field.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "cannot access field '" + className + "." + fieldName + "'", e);
        }

        if (!(value instanceof AccumulateFunction)) {
            throw new RuntimeException(
                    "field '" + className + "." + fieldName + "' is not an AccumulateFunction");
        }

        AccumulateFunction<Serializable> fn = (AccumulateFunction<Serializable>) value;
        return new ResolvedFunction(fn, fn.getResultType(), false);
    }

    private QueryElement buildQueryElement(PatternIR patternIr,
                                          QueryImpl targetQuery,
                                          Map<String, BoundVariable> boundVariables) {
        Declaration[] queryParams = targetQuery.getParameters();
        List<String> args = patternIr.positionalArgs();

        if (args.size() != queryParams.length) {
            throw new RuntimeException(
                    "query '" + targetQuery.getName() + "' expects " + queryParams.length
                    + " arguments but got " + args.size());
        }

        QueryArgument[] arguments = new QueryArgument[args.size()];
        List<Integer> varIndexes = new ArrayList<>();
        List<Declaration> requiredDeclarations = new ArrayList<>();

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("var ")) {
                arguments[i] = QueryArgument.VAR;
                varIndexes.add(i);
            } else {
                BoundVariable bv = boundVariables.get(arg);
                if (bv != null) {
                    arguments[i] = new QueryArgument.Declr(bv.declaration());
                    requiredDeclarations.add(bv.declaration());
                } else if (isSimpleIdentifier(arg)) {
                    arguments[i] = QueryArgument.VAR;
                    varIndexes.add(i);
                } else {
                    arguments[i] = new QueryArgument.Literal(parseLiteral(arg));
                }
            }
        }

        ReadAccessor selfReader = new SelfReferenceClassFieldReader(Object[].class);
        Pattern resultPattern = new Pattern(lambdaCompiler.nextPatternId(), 0, 0,
                new ClassObjectType(Object[].class), null);

        for (int idx : varIndexes) {
            String varName = args.get(idx);
            if (varName.startsWith("var ")) {
                varName = varName.substring(4).trim();
            }
            Class<?> paramType = queryParams[idx].getDeclarationClass();
            Declaration decl = resultPattern.addDeclaration(varName);
            decl.setReadAccessor(new ArrayElementReader(selfReader, idx, paramType));
        }

        int[] varIndexArray = varIndexes.stream().mapToInt(Integer::intValue).toArray();

        if (patternIr.passive() && targetQuery.getConsequence() != null) {
            throw new RuntimeException(
                    "Cannot passively invoke query '" + targetQuery.getName()
                    + "': the query has an agenda-based 'do' block which is incompatible with passive invocation");
        }

        return new QueryElement(
                resultPattern,
                targetQuery.getName(),
                arguments,
                varIndexArray,
                requiredDeclarations.toArray(new Declaration[0]),
                !patternIr.passive(),
                false);
    }

    private static List<String> buildNamedQueryArgs(List<String> conditions, QueryImpl targetQuery) {
        Declaration[] queryParams = targetQuery.getParameters();
        Map<String, Integer> nameToIndex = new LinkedHashMap<>();
        for (int i = 0; i < queryParams.length; i++) {
            nameToIndex.put(queryParams[i].getIdentifier(), i);
        }

        String[] args = new String[queryParams.length];
        for (String condition : conditions) {
            condition = condition.trim();
            if (condition.startsWith("var ")) {
                // "var bindName : paramName"
                int colonIdx = condition.indexOf(':');
                if (colonIdx < 0) {
                    throw new RuntimeException("invalid named query argument: '" + condition + "'");
                }
                String bindName = condition.substring(4, colonIdx).trim();
                String paramName = condition.substring(colonIdx + 1).trim();
                Integer index = nameToIndex.get(paramName);
                if (index == null) {
                    throw new RuntimeException(
                            "unknown query parameter '" + paramName + "' in named access for query '"
                            + targetQuery.getName() + "'. Known parameters: " + nameToIndex.keySet());
                }
                if (args[index] != null) {
                    throw new RuntimeException(
                            "duplicate assignment to query parameter '" + paramName + "' in named access for query '"
                            + targetQuery.getName() + "'");
                }
                args[index] = "var " + bindName;
            } else {
                // "paramName == expr"
                int eqIdx = condition.indexOf("==");
                if (eqIdx < 0) {
                    throw new RuntimeException("invalid named query argument: '" + condition
                            + "'. Input arguments must use '==' (e.g., paramName == value)");
                }
                String paramName = condition.substring(0, eqIdx).trim();
                String expr = condition.substring(eqIdx + 2).trim();
                Integer index = nameToIndex.get(paramName);
                if (index == null) {
                    throw new RuntimeException(
                            "unknown query parameter '" + paramName + "' in named access for query '"
                            + targetQuery.getName() + "'. Known parameters: " + nameToIndex.keySet());
                }
                if (args[index] != null) {
                    throw new RuntimeException(
                            "duplicate assignment to query parameter '" + paramName + "' in named access for query '"
                            + targetQuery.getName() + "'");
                }
                args[index] = expr;
            }
        }

        // Validate all parameters assigned
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                throw new RuntimeException(
                        "named access for query '" + targetQuery.getName()
                        + "' is missing parameter '" + queryParams[i].getIdentifier() + "'");
            }
        }

        return List.of(args);
    }

    /**
     * Finds the JavaBeans getter for a named field on the given class.
     */
    private static java.lang.reflect.Method findGetterForField(Class<?> clazz, String fieldName) {
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            return clazz.getMethod("get" + capitalized);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getMethod("is" + capitalized);
            } catch (NoSuchMethodException e2) {
                throw new RuntimeException("No getter found for field '" + fieldName + "' on " + clazz.getName());
            }
        }
    }

    private static boolean isSimpleIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Checks whether any input argument in a positional query invocation is a
     * variable that is NOT in the query's parameter list. Used by the
     * self-reference disambiguation heuristic: inside a query body, when a
     * pattern matches the current query's own entry point, all-param inputs
     * means a positional fact match (Pattern / base case), while any non-param
     * input means a recursive call (QueryElement).
     */
    private static boolean hasNonParameterInput(List<String> positionalArgs,
                                                 QueryImpl currentQuery,
                                                 Map<String, BoundVariable> boundVariables) {
        Set<String> paramNames = new HashSet<>();
        for (Declaration d : currentQuery.getParameters()) {
            paramNames.add(d.getIdentifier());
        }
        for (String arg : positionalArgs) {
            if (arg.startsWith("var ")) continue; // explicit output
            if (!isSimpleIdentifier(arg)) continue; // expression or literal — not a simple variable
            if (!boundVariables.containsKey(arg)) continue; // unbound identifier -> implicit output
            // Bound variable that is NOT a query parameter -> local binding
            if (!paramNames.contains(arg)) return true;
        }
        return false;
    }

    private static Object parseLiteral(String literal) {
        if (literal.startsWith("\"") && literal.endsWith("\"")) {
            return literal.substring(1, literal.length() - 1);
        }
        try { return Integer.parseInt(literal); }
        catch (NumberFormatException e1) {
            try { return Long.parseLong(literal); }
            catch (NumberFormatException e2) {
                try { return Double.parseDouble(literal); }
                catch (NumberFormatException e3) { return literal; }
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
                declarations.add(bv.declaration());
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

    /**
     * Builds a Pattern for the self-referencing base case in a recursive query.
     * Unlike {@link #buildPattern}, this handles positional args specially:
     * <ul>
     *   <li>{@code var z} args create output bindings (Declarations) instead of constraints</li>
     *   <li>When a positional arg name collides with the pattern field name (e.g. both are "a"),
     *       the bound variable reference is aliased to avoid duplicate MVEL declarations</li>
     * </ul>
     */
    private Pattern buildSelfReferencePattern(PatternIR parseResult,
                                               TypeResolver typeResolver,
                                               Map<String, Class<?>> entryPointTypes,
                                               Class<?> unitClass,
                                               Map<String, BoundVariable> boundVariables,
                                               QueryImpl currentQuery) {
        Class<?> type = resolvePatternType(parseResult, typeResolver, entryPointTypes, unitClass);
        ObjectType objectType = new ClassObjectType(type, false);

        Pattern pattern = new Pattern(lambdaCompiler.nextPatternId(), 0, 0, objectType, parseResult.bindName(), false);
        pattern.setPassive(parseResult.passive());
        pattern.setSource(new EntryPointId(parseResult.entryPoint()));

        Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
        org.mvel3.transpiler.context.Declaration<?>[] declarations = DrlxLambdaCompiler.extractDeclarations(patternClass);

        // Collect pattern field names for collision detection
        Set<String> fieldNames = new HashSet<>();
        for (org.mvel3.transpiler.context.Declaration<?> d : declarations) {
            fieldNames.add(d.name());
        }

        // Map query parameter names to their index for unification
        Declaration[] queryParams = currentQuery.getParameters();
        Map<String, Integer> paramIndexMap = new HashMap<>();
        for (int p = 0; p < queryParams.length; p++) {
            paramIndexMap.put(queryParams[p].getIdentifier(), p);
        }

        for (int i = 0; i < parseResult.positionalArgs().size(); i++) {
            String argExpr = parseResult.positionalArgs().get(i);
            String fieldName = DrlxLambdaCompiler.resolvePositionalField(patternClass, i);

            if (argExpr.startsWith("var ")) {
                // Output binding: bind the positional field to the variable name
                String varName = argExpr.substring(4).trim();
                Declaration decl = pattern.addDeclaration(varName);
                java.lang.reflect.Method getter = findGetterForField(patternClass, fieldName);
                Class<?> fieldType = getter.getReturnType();
                decl.setReadAccessor(new DrlxBeanFieldReader(getter, fieldType));
                boundVariables.put(varName, new BoundVariable(varName, fieldType, pattern, decl));
                continue;
            }

            // Build the inner beta constraint, handling name collisions
            @SuppressWarnings("rawtypes")
            MutableTypeConstraint innerConstraint;
            BoundVariable bv = boundVariables.get(argExpr);
            if (bv != null && fieldNames.contains(argExpr)) {
                // Name collision: use an aliased name for the bound variable reference
                // so MVEL can distinguish between the pattern field and the external variable.
                // Create a synthetic Declaration on the same source pattern with the alias
                // name, so buildEvalMap populates the map key as "__qp_<name>" instead of
                // overwriting the fact field entry.
                String alias = "__qp_" + argExpr;
                Declaration aliasDecl = bv.pattern().addDeclaration(alias);
                aliasDecl.setReadAccessor(bv.declaration().getExtractor());
                String synthesized = fieldName + " == (" + alias + ")";
                BoundVariable aliased = new BoundVariable(alias, bv.type(), bv.pattern(), aliasDecl);
                List<BoundVariable> refs = List.of(aliased);
                innerConstraint = (MutableTypeConstraint) lambdaCompiler.createBetaLambdaConstraint(
                        synthesized, patternClass, declarations, refs);
            } else {
                // Normal constraint synthesis (no collision)
                String synthesized = fieldName + " == (" + argExpr + ")";
                List<BoundVariable> referencedBindings = lambdaCompiler.findReferencedBindings(synthesized, boundVariables);
                innerConstraint = referencedBindings.isEmpty()
                        ? lambdaCompiler.createLambdaConstraint(synthesized, patternClass, declarations)
                        : (MutableTypeConstraint) lambdaCompiler.createBetaLambdaConstraint(synthesized, patternClass, declarations, referencedBindings);
            }

            // Wrap with unification: if the query parameter is unbound at runtime,
            // skip the constraint (allow any value). This mirrors old DRL's
            // unification semantics where output parameters don't constrain.
            Integer paramIdx = paramIndexMap.get(argExpr);
            if (paramIdx != null) {
                pattern.addConstraint(new DrlxUnificationConstraint(innerConstraint, paramIdx));
                // Add an output binding declaration on the body Pattern so the query
                // terminal node can read the matched fact's field value for unbound
                // parameters. This mirrors LogicTransformer's redeclaredDeclr mechanism.
                java.lang.reflect.Method getter = findGetterForField(patternClass, fieldName);
                Class<?> fieldType = getter.getReturnType();
                Declaration outputDecl = pattern.addDeclaration(argExpr);
                outputDecl.setReadAccessor(new DrlxBeanFieldReader(getter, fieldType));
            } else {
                pattern.addConstraint(innerConstraint);
            }
        }

        // Add regular conditions (non-positional)
        for (String expression : parseResult.conditions()) {
            List<BoundVariable> referencedBindings = lambdaCompiler.findReferencedBindings(expression, boundVariables);
            Constraint constraint = referencedBindings.isEmpty()
                    ? lambdaCompiler.createLambdaConstraint(expression, patternClass, declarations)
                    : lambdaCompiler.createBetaLambdaConstraint(expression, patternClass, declarations, referencedBindings);
            pattern.addConstraint(constraint);
        }

        return pattern;
    }

    private Pattern buildPattern(PatternIR parseResult,
                                 TypeResolver typeResolver,
                                 Map<String, Class<?>> entryPointTypes,
                                 Class<?> unitClass,
                                 Map<String, BoundVariable> boundVariables) {
        Class<?> type = resolvePatternType(parseResult, typeResolver, entryPointTypes, unitClass);
        Role roleAnnotation = type.getAnnotation(Role.class);
        boolean isEvent = roleAnnotation != null && roleAnnotation.value() == Role.Type.EVENT;
        ObjectType objectType = new ClassObjectType(type, isEvent);

        Pattern pattern = new Pattern(lambdaCompiler.nextPatternId(), 0, 0, objectType, parseResult.bindName(), false);
        pattern.setPassive(parseResult.passive());
        pattern.setSource(new EntryPointId(parseResult.entryPoint()));

        Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
        org.mvel3.transpiler.context.Declaration<?>[] declarations = DrlxLambdaCompiler.extractDeclarations(patternClass);

        for (int i = 0; i < parseResult.positionalArgs().size(); i++) {
            String argExpr = parseResult.positionalArgs().get(i);
            String fieldName = DrlxLambdaCompiler.resolvePositionalField(patternClass, i);

            if (argExpr.startsWith("var ")) {
                String varName = argExpr.substring(4).trim();
                Declaration decl = pattern.addDeclaration(varName);
                java.lang.reflect.Method getter = findGetterForField(patternClass, fieldName);
                Class<?> fieldType = getter.getReturnType();
                decl.setReadAccessor(new DrlxBeanFieldReader(getter, fieldType));
                boundVariables.put(varName, new BoundVariable(varName, fieldType, pattern, decl));
                continue;
            }

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

        for (DrlxRuleAstModel.TemporalConditionIR tc : parseResult.temporalConditions()) {
            DrlxLambdaCompiler.BoundVariable ref = boundVariables.get(tc.rightBinding());
            if (ref == null) {
                throw new RuntimeException(
                        "Temporal constraint references unknown binding '" + tc.rightBinding() + "'");
            }
            if (!pattern.getObjectType().isEvent()) {
                throw new RuntimeException(
                        "Temporal operator '" + tc.operator()
                        + "' requires event types (@Role(Type.EVENT)) but '"
                        + parseResult.typeName() + "' is not an event");
            }
            if (!ref.pattern().getObjectType().isEvent()) {
                throw new RuntimeException(
                        "Temporal operator '" + tc.operator()
                        + "' requires event types (@Role(Type.EVENT)) but the referenced binding '"
                        + tc.rightBinding() + "' is not an event");
            }
            org.drools.model.functions.temporal.TemporalPredicate predicate =
                    TemporalPredicateFactory.create(tc.operator(), tc.negated(), tc.parameters());
            pattern.addConstraint(
                    new DrlxTemporalConstraint(predicate, new Declaration[] { ref.declaration() }));
        }

        if (!parseResult.watchedProperties().isEmpty()) {
            List<String> validated = validateWatchedProperties(
                    parseResult.watchedProperties(), patternClass, parseResult.typeName());
            pattern.addWatchedProperties(validated);
        }

        if (parseResult.windowType() != null) {
            switch (parseResult.windowType()) {
                case "time" -> pattern.addBehavior(
                        new SlidingTimeWindow(TimeUtils.parseTimeString(parseResult.windowParameter())));
                case "length" -> pattern.addBehavior(
                        new SlidingLengthWindow(Integer.parseInt(parseResult.windowParameter())));
            }
        }

        return pattern;
    }

    private static void applyAnnotations(RuleImpl rule, List<RuleAnnotationIR> annotations) {
        for (RuleAnnotationIR ann : annotations) {
            switch (ann.kind()) {
                case SALIENCE -> rule.setSalience(new SalienceInteger(Integer.parseInt(ann.rawValue())));
                case DESCRIPTION -> rule.addMetaAttribute("Description", ann.rawValue());
                case DATASOURCE -> { }
                case NO_LOOP -> { rule.setNoLoop(true); rule.setEager(true); }
                case LOCK_ON_ACTIVE -> { rule.setLockOnActive(true); rule.setEager(true); }
                case DISABLED -> rule.setEnabled(EnabledBoolean.ENABLED_FALSE);
                case ACTIVATION_GROUP -> rule.setActivationGroup(ann.rawValue());
                case TIMER -> {
                    org.drools.base.time.impl.Timer timer = RuleBuilder.buildTimer(
                            ann.rawValue(),
                            null,
                            s -> {
                                if (s == null) return null;
                                long ms = TimeUtils.parseTimeString(s);
                                return new TimerExpression() {
                                    @Override public Declaration[] getDeclarations() { return new Declaration[0]; }
                                    @Override public Object getValue(BaseTuple leftTuple, Declaration[] declrs, ValueResolver valueResolver) { return ms; }
                                };
                            },
                            err -> { throw new RuntimeException(err); }
                    );
                    if (timer == null) {
                        throw new RuntimeException("Failed to build timer from: " + ann.rawValue());
                    }
                    rule.setTimer(timer);
                }
                case DURATION -> {
                    long ms = TimeUtils.parseTimeString(ann.rawValue());
                    rule.setTimer(new DurationTimer(ms));
                }
                case DATE_EFFECTIVE -> {
                    LocalDate date = LocalDate.parse(ann.rawValue());
                    Calendar cal = GregorianCalendar.from(
                            date.atStartOfDay(ZoneId.systemDefault()));
                    rule.setDateEffective(cal);
                }
                case DATE_EXPIRES -> {
                    LocalDate date = LocalDate.parse(ann.rawValue());
                    Calendar cal = GregorianCalendar.from(
                            date.atStartOfDay(ZoneId.systemDefault()));
                    rule.setDateExpires(cal);
                }
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
