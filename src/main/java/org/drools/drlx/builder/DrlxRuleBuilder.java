package org.drools.drlx.builder;

import java.io.IOException;
import java.nio.file.Path;
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
import org.mvel3.ClassManager;
import org.mvel3.lambdaextractor.LambdaRegistry;

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
     * Pre-builds DRLX source: compiles all lambdas and records metadata for later reuse.
     * Saves metadata to the given output directory.
     */
    public DrlxLambdaMetadata preBuild(String drlxSource, Path outputDir) throws IOException {
        CharStream charStream = CharStreams.fromString(drlxSource);
        DrlxLexer lexer = new DrlxLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);

        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        DrlxPreBuildVisitor visitor = new DrlxPreBuildVisitor(tokens);
        visitor.visitDrlxCompilationUnit(ctx);

        DrlxLambdaMetadata metadata = visitor.getMetadata();
        metadata.save(outputDir);
        return metadata;
    }

    /**
     * Builds a KieBase using pre-compiled lambda metadata (in-memory).
     */
    public KieBase build(String drlxSource, DrlxLambdaMetadata metadata) {
        CharStream charStream = CharStreams.fromString(drlxSource);
        DrlxLexer lexer = new DrlxLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);

        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        DrlxToRuleImplVisitor visitor = new DrlxToRuleImplVisitor(tokens);
        visitor.setPreBuildMetadata(metadata);
        List<KiePackage> kiePackages = visitor.visitDrlxCompilationUnit(ctx);
        return createKieBase(kiePackages);
    }

    /**
     * Builds a KieBase using pre-compiled lambda metadata loaded from a file.
     */
    public KieBase build(String drlxSource, Path metadataFile) throws IOException {
        DrlxLambdaMetadata metadata = DrlxLambdaMetadata.load(metadataFile);
        return build(drlxSource, metadata);
    }

    /**
     * Parses DRLX source into List&lt;KiePackage&gt; using the direct visitor with batch compilation.
     */
    public List<KiePackage> parse(String drlxSource) {
        CharStream charStream = CharStreams.fromString(drlxSource);
        DrlxLexer lexer = new DrlxLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);

        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        DrlxToRuleImplVisitor visitor = new DrlxToRuleImplVisitor(tokens);

        // Enable batch mode only when persistence is disabled (no-persist path).
        // When persistence is enabled, the eager per-lambda path handles LambdaRegistry dedup/persistence.
        if (!LambdaRegistry.PERSISTENCE_ENABLED) {
            ClassManager sharedClassManager = new ClassManager();
            visitor.enableBatchMode(sharedClassManager);
        }

        List<KiePackage> kiePackages = visitor.visitDrlxCompilationUnit(ctx);

        // Batch compile all collected lambda sources in a single javac call
        visitor.compileBatch(Thread.currentThread().getContextClassLoader());

        return kiePackages;
    }
}
