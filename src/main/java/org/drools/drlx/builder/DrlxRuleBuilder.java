package org.drools.drlx.builder;

import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.base.RuleBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;

/**
 * Builder that creates KieBase from DRLX source, skipping Descr generation.
 * It uses DrlxDirectVisitor to walk the ANTLR parse tree directly into RuleImpl/KiePackage.
 */
public class DrlxRuleBuilder {

    public DrlxRuleBuilder() {
    }

    /**
     * Creates a KieBase from a list of KiePackages.
     */
    public KieBase createKieBase(List<KiePackage> kiePackages) {
        RuleBase kBase = RuleBaseFactory.newRuleBase("myKBase", RuleBaseFactory.newKnowledgeBaseConfiguration());
        kBase.addPackages(kiePackages);
        return KnowledgeBaseFactory.newKnowledgeBase(kBase);
    }

    /**
     * Parses DRLX source and creates a KieBase end-to-end, skipping Descr generation.
     */
    public KieBase build(String drlxSource) {
        List<KiePackage> kiePackages = parse(drlxSource);
        return createKieBase(kiePackages);
    }

    /**
     * Parses DRLX source into List&lt;KiePackage&gt; using the direct visitor.
     */
    public List<KiePackage> parse(String drlxSource) {
        CharStream charStream = CharStreams.fromString(drlxSource);
        DrlxLexer lexer = new DrlxLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);

        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        DrlxToRuleImplVisitor visitor = new DrlxToRuleImplVisitor(tokens);
        return visitor.visitDrlxCompilationUnit(ctx);
    }
}
