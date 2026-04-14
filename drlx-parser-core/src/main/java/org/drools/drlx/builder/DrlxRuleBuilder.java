package org.drools.drlx.builder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.base.RuleBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.mvel3.ClassManager;
import org.mvel3.MVELBatchCompiler;
import org.mvel3.lambdaextractor.LambdaRegistry;

/**
 * Builder that creates KieBase from DRLX source through a single pipeline:
 * ANTLR → {@link DrlxToRuleAstVisitor} → {@link DrlxRuleAstModel} records
 * → {@link DrlxRuleAstRuntimeBuilder} → RuleImpl.
 *
 * Proto persistence (via {@link DrlxRuleAstParseResult}) is an optional output
 * of the pre-build step; it is not part of the normal runtime build.
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
     * Parses DRLX source and creates a KieBase end-to-end.
     */
    public KieBase build(String drlxSource) {
        List<KiePackage> kiePackages = parse(drlxSource);
        return createKieBase(kiePackages);
    }

    /**
     * Pre-builds DRLX source: compiles all lambdas and records metadata for later reuse.
     * Saves metadata to the given output directory; may additionally persist a proto
     * cache of the RuleAST depending on the active cache strategy.
     */
    public DrlxLambdaMetadata preBuild(String drlxSource, Path outputDir) throws IOException {
        CompilationUnitIR ast = parseToRuleAst(drlxSource);
        persistBuildCache(drlxSource, ast, outputDir);

        DrlxPreBuildLambdaCompiler preBuildCompiler = new DrlxPreBuildLambdaCompiler();
        MVELBatchCompiler batchCompiler = new MVELBatchCompiler(new ClassManager(), outputDir);
        preBuildCompiler.enableBatchMode(batchCompiler);

        DrlxRuleAstRuntimeBuilder builder = new DrlxRuleAstRuntimeBuilder(preBuildCompiler);
        builder.build(ast);

        preBuildCompiler.compileBatch(Thread.currentThread().getContextClassLoader());

        DrlxLambdaMetadata metadata = preBuildCompiler.getMetadata();
        metadata.save(outputDir);
        return metadata;
    }

    /**
     * Builds a KieBase using pre-compiled lambda metadata (in-memory).
     */
    public KieBase build(String drlxSource, DrlxLambdaMetadata metadata) {
        return build(drlxSource, metadata, null);
    }

    /**
     * Builds a KieBase using pre-compiled lambda metadata and an optional cached build artifact directory.
     */
    public KieBase build(String drlxSource, DrlxLambdaMetadata metadata, Path cacheDir) {
        KieBase cachedBuild = buildFromCache(drlxSource, metadata, cacheDir);
        if (cachedBuild != null) {
            return cachedBuild;
        }

        CompilationUnitIR ast = parseToRuleAst(drlxSource);
        DrlxLambdaCompiler lambdaCompiler = new DrlxLambdaCompiler();
        lambdaCompiler.setPreBuildMetadata(metadata);
        DrlxRuleAstRuntimeBuilder builder = new DrlxRuleAstRuntimeBuilder(lambdaCompiler);
        return createKieBase(builder.build(ast));
    }

    /**
     * Builds a KieBase using pre-compiled lambda metadata loaded from a file.
     */
    public KieBase build(String drlxSource, Path metadataFile) throws IOException {
        DrlxLambdaMetadata metadata = DrlxLambdaMetadata.load(metadataFile);
        return build(drlxSource, metadata, metadataFile.getParent());
    }

    /**
     * Parses DRLX source into List&lt;KiePackage&gt; with batch lambda compilation.
     */
    public List<KiePackage> parse(String drlxSource) {
        CompilationUnitIR ast = parseToRuleAst(drlxSource);

        DrlxLambdaCompiler lambdaCompiler = new DrlxLambdaCompiler();
        Path persistDir = LambdaRegistry.PERSISTENCE_ENABLED ? LambdaRegistry.DEFAULT_PERSISTENCE_PATH : null;
        MVELBatchCompiler batchCompiler = new MVELBatchCompiler(new ClassManager(), persistDir);
        lambdaCompiler.enableBatchMode(batchCompiler);

        DrlxRuleAstRuntimeBuilder builder = new DrlxRuleAstRuntimeBuilder(lambdaCompiler);
        List<KiePackage> kiePackages = builder.build(ast);

        lambdaCompiler.compileBatch(Thread.currentThread().getContextClassLoader());
        return kiePackages;
    }

    private static CompilationUnitIR parseToRuleAst(String drlxSource) {
        CharStream charStream = CharStreams.fromString(drlxSource);
        DrlxLexer lexer = new DrlxLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        return new DrlxToRuleAstVisitor(tokens).visitDrlxCompilationUnit(ctx);
    }

    private void persistBuildCache(String drlxSource, CompilationUnitIR ast, Path outputDir) throws IOException {
        switch (DrlxBuildCacheStrategy.current()) {
            case NONE -> {
            }
            case RULE_AST -> DrlxRuleAstParseResult.save(drlxSource, ast, outputDir);
        }
    }

    private KieBase buildFromCache(String drlxSource, DrlxLambdaMetadata metadata, Path cacheDir) {
        if (cacheDir == null) {
            return null;
        }

        try {
            return switch (DrlxBuildCacheStrategy.current()) {
                case NONE -> null;
                case RULE_AST -> {
                    CompilationUnitIR parseResult =
                            DrlxRuleAstParseResult.load(drlxSource, DrlxRuleAstParseResult.parseResultFilePath(cacheDir));
                    if (parseResult == null) {
                        yield null;
                    }
                    DrlxLambdaCompiler lambdaCompiler = new DrlxLambdaCompiler();
                    lambdaCompiler.setPreBuildMetadata(metadata);
                    DrlxRuleAstRuntimeBuilder builder = new DrlxRuleAstRuntimeBuilder(lambdaCompiler);
                    yield createKieBase(builder.build(parseResult));
                }
            };
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DRLX build cache from " + cacheDir, e);
        }
    }
}
