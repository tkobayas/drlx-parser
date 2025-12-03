package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.RuleBase;
import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.definitions.impl.KnowledgePackageImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElementFactory;
import org.drools.base.rule.ImportDeclaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.RuleConditionElement;
import org.drools.base.rule.constraint.Constraint;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.drl.ast.descr.AndDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.ConditionalElementDescr;
import org.drools.drl.ast.descr.EntryPointDescr;
import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.PatternSourceDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.util.TypeResolver;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.mvel3.Type;

/**
 * Create KiePackage and RuleImpl from PackageDescr
 * This may be integrated into KieBuilderImpl, KnowledgeBuilderImpl or other components in drools-compiler in the future,
 * because these components have lots of details about RuleImpl creation.
 * For now, this is a very light-weight version, kept separated and concise as possible.
 */
public class DrlxRuleBuilder {

    private DrlxRuleUnit ruleUnit; // Do we need to keep this??

    private int patternId = 0;

    public DrlxRuleBuilder() {
    }

    public KieBase createKieBase(PackageDescr packageDescr) {
        KiePackage kiePackage = buildKnowledgePackage(packageDescr);
        List<KiePackage> kiePackages = new ArrayList<>();
        kiePackages.add(kiePackage);

        RuleBase kBase = RuleBaseFactory.newRuleBase("myKBase", RuleBaseFactory.newKnowledgeBaseConfiguration());
        kBase.addPackages(kiePackages);
        return KnowledgeBaseFactory.newKnowledgeBase(kBase);
    }

    public KiePackage buildKnowledgePackage(PackageDescr packageDescr) {
        // create package
        KnowledgePackageImpl pkg = new KnowledgePackageImpl(packageDescr.getName());
        pkg.setClassLoader(Thread.currentThread().getContextClassLoader()); // This instantiates ClassTypeResolver

        // import
        packageDescr.getImports().forEach(importDescr -> pkg.addImport(new ImportDeclaration(importDescr.getTarget())));

        // rule unit
        ruleUnit = new DrlxRuleUnit();

        // rule
        packageDescr.getRules().forEach(ruleDescr -> {
            RuleImpl rule = buildRule(ruleDescr, pkg.getTypeResolver());
            pkg.addRule(rule);
        });

        return pkg;
    }

    private RuleImpl buildRule(RuleDescr ruleDescr, TypeResolver typeResolver) {
        RuleImpl rule = new RuleImpl(ruleDescr.getName());
        rule.setResource(rule.getResource());

        // corresponds to RuleBuilder.build()
        AndDescr lhs = ruleDescr.getLhs();

        // corresponds to GroupElementBuilder.build()
        GroupElement ge = GroupElementFactory.newAndInstance();

        // Pattern
        for (BaseDescr child : ((ConditionalElementDescr) lhs).getDescrs()) {
            child.setResource(ruleDescr.getResource());
            child.setNamespace(ruleDescr.getNamespace());

            if (child instanceof PatternDescr patternDescr) {
                // corresponds to PatternBuilder.build()
                ObjectType objectType;
                try {
                    Class<?> type = typeResolver.resolveType(patternDescr.getObjectType());
                    objectType = new ClassObjectType(type, false);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

                // corresponds to PatternBuilder.buildPattern()
                Pattern pattern = new Pattern(patternId++, 0, 0, objectType, patternDescr.getIdentifier(), false);

                // corresponds to PatternBuilder.processSource()
                PatternSourceDescr source = patternDescr.getSource();
                if (source instanceof EntryPointDescr entryPointDescr) {
                    pattern.setSource(new EntryPointId(entryPointDescr.getEntryId()));
                }

                // corresponds to PatternBuilder.processConstraintsAndBinds()
                for (BaseDescr descr : patternDescr.getDescrs()) {
                    if (descr instanceof ExprConstraintDescr exprConstraintDescr) {
                        String expression = exprConstraintDescr.getExpression();
                        // corresponds to new MVELConstraint()
                        Constraint constraint = new DrlxLambdaConstraint(expression, ((ClassObjectType) pattern.getObjectType()).getClassType());
                        pattern.addConstraint(constraint);
                    }
                }
                ge.addChild(pattern);
            }
        }

        rule.setLhs(ge);

        // corresponds to ConsequenceBuilder.build()
        // TODO: manage TypeMap (e.g. "p", Person.class)
        Map<String, Type<?>> types = getTypeMap(ge);
        rule.setConsequence(new DrlxLambdaConsequence((String) ruleDescr.getConsequence(), types));

        // to populate requiredDeclarations, we may need to analyze bind variable names and call rule.setRequiredDeclarationsForConsequence()
        // For now, we can use allDeclarations

        return rule;
    }

    // a quick hack
    private  Map<String, Type<?>> getTypeMap(GroupElement ge) {
        Map<String, Type<?>> types = new HashMap<>();
        Pattern pattern = (Pattern) ge.getChildren().get(0);
        Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
        types.put("p", Type.type(patternClass));
        return types;
    }
}