package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
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
import org.drools.drlx.parser.DrlxParser;
import org.drools.drlx.parser.DrlxParserBaseVisitor;
import org.drools.util.TypeResolver;
import org.kie.api.definition.KiePackage;
import org.mvel3.Type;

/**
 * Visitor that walks a DRLX parse tree and directly builds RuleImpl/KiePackage,
 * skipping the intermediate Descr generation step.
 * Returns List&lt;KiePackage&gt; from the top-level visit.
 */
public class DrlxToRuleImplVisitor extends DrlxParserBaseVisitor<Object> {

    private final TokenStream tokens;

    private int patternId = 0;

    public DrlxToRuleImplVisitor() {
        this(null);
    }

    public DrlxToRuleImplVisitor(TokenStream tokens) {
        this.tokens = tokens;
    }

    @Override
    public List<KiePackage> visitDrlxCompilationUnit(DrlxParser.DrlxCompilationUnitContext ctx) {
        // package name
        String packageName = "";
        if (ctx.packageDeclaration() != null) {
            packageName = ctx.packageDeclaration().qualifiedName().getText();
        }

        // create package
        KnowledgePackageImpl pkg = new KnowledgePackageImpl(packageName);
        pkg.setClassLoader(Thread.currentThread().getContextClassLoader());

        // imports
        if (ctx.importDeclaration() != null) {
            ctx.importDeclaration().forEach(importCtx -> {
                String target = importCtx.qualifiedName().getText();
                if (importCtx.MUL() != null) {
                    target = target + ".*";
                }
                pkg.addImport(new ImportDeclaration(target));
            });
        }

        // rules
        if (ctx.ruleDeclaration() != null) {
            ctx.ruleDeclaration().forEach(ruleCtx -> {
                RuleImpl rule = buildRule(ruleCtx, pkg.getTypeResolver());
                pkg.addRule(rule);
            });
        }

        List<KiePackage> kiePackages = new ArrayList<>();
        kiePackages.add(pkg);
        return kiePackages;
    }

    private RuleImpl buildRule(DrlxParser.RuleDeclarationContext ctx, TypeResolver typeResolver) {
        String ruleName = ctx.identifier().getText();
        RuleImpl rule = new RuleImpl(ruleName);
        rule.setResource(rule.getResource());

        GroupElement ge = GroupElementFactory.newAndInstance();

        if (ctx.ruleBody() != null) {
            ctx.ruleBody().ruleItem().forEach(item -> {
                if (item.rulePattern() != null) {
                    Pattern pattern = buildPattern(item.rulePattern(), typeResolver);
                    ge.addChild(pattern);
                } else if (item.ruleConsequence() != null) {
                    String consequence = extractConsequence(item.ruleConsequence());
                    Map<String, Type<?>> types = getTypeMap(ge);
                    rule.setConsequence(new DrlxLambdaConsequence(consequence, types));
                }
            });
        }

        rule.setLhs(ge);

        return rule;
    }

    private Pattern buildPattern(DrlxParser.RulePatternContext ctx, TypeResolver typeResolver) {
        String typeName = ctx.identifier(0).getText();
        String bindName = ctx.identifier(1).getText();

        // resolve type
        ObjectType objectType;
        try {
            Class<?> type = typeResolver.resolveType(typeName);
            objectType = new ClassObjectType(type, false);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Pattern pattern = new Pattern(patternId++, 0, 0, objectType, bindName, false);

        // entry point from oopath
        String entryPointText = extractEntryPointFromOopath(getText(ctx.oopathExpression()));
        pattern.setSource(new EntryPointId(entryPointText));

        // constraints from oopath
        List<String> conditions = extractConditions(ctx.oopathExpression());
        for (String expression : conditions) {
            Constraint constraint = new DrlxLambdaConstraint(expression, ((ClassObjectType) pattern.getObjectType()).getClassType());
            pattern.addConstraint(constraint);
        }

        return pattern;
    }

    private String extractConsequence(DrlxParser.RuleConsequenceContext ctx) {
        String statementText = getText(ctx.statement());
        return trimBraces(statementText);
    }

    private String extractEntryPointFromOopath(String oopath) {
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

    private List<String> extractConditions(DrlxParser.OopathExpressionContext ctx) {
        List<DrlxParser.OopathChunkContext> oopathChunkContexts = ctx.oopathChunk();
        if (oopathChunkContexts.isEmpty()) {
            return List.of();
        }
        DrlxParser.OopathChunkContext lastOopathChunkContext = oopathChunkContexts.get(oopathChunkContexts.size() - 1);
        return lastOopathChunkContext.drlxExpression().stream()
                .map(this::getText)
                .collect(Collectors.toList());
    }

    private Map<String, Type<?>> getTypeMap(GroupElement ge) {
        Map<String, Type<?>> types = new HashMap<>();
        ge.getChildren().stream().filter(element -> element instanceof Pattern).forEach(pattern -> {
            Pattern p = (Pattern) pattern;
            Class<?> patternClass = ((ClassObjectType) p.getObjectType()).getClassType();
            Declaration declaration = p.getDeclaration();
            types.put(declaration.getIdentifier(), Type.type(patternClass));
        });
        return types;
    }

    private String trimBraces(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text;
        if (text.startsWith("{") && text.endsWith("}")) {
            stripped = text.substring(1, text.length() - 1);
        }
        return stripped.trim();
    }

    private String getText(ParserRuleContext ctx) {
        return tokens != null ? tokens.getText(ctx) : ctx.getText();
    }
}
