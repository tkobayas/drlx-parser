package org.drools.drlx.builder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.drools.base.base.ValueResolver;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.consequence.Consequence;
import org.drools.core.rule.consequence.KnowledgeHelper;
import org.mvel3.Evaluator;
import org.mvel3.MVEL;
import org.mvel3.Type;

public class DrlxLambdaConsequence implements Consequence<KnowledgeHelper> {

    private static final String RETURN_NULL = "\n return null;";

    private String consequenceBlock;

    private Evaluator<Map<String, Object>, Void, String> evaluator;

    private Map<String, Type<?>> declarationTypes;

    public DrlxLambdaConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        this.consequenceBlock = consequenceBlock;
        this.declarationTypes = declarationTypes;

        initializeLambdaConsequence();
    }

    public Evaluator<Map<String, Object>, Void, String> getEvaluator() {
        return evaluator;
    }

    private void initializeLambdaConsequence() {
        // TODO: manage source code, hash, and implement cache
        MVEL mvel = new MVEL();
        // Add "return null;" with outType String, because Void doesn't match with void return. TODO: fix mvel3
        evaluator = mvel.compileMapBlock(consequenceBlock + RETURN_NULL, String.class, new HashSet<>(), declarationTypes);
    }

    @Override
    public String getName() {
        return RuleImpl.DEFAULT_CONSEQUENCE_NAME;
    }

    @Override
    public void evaluate(KnowledgeHelper knowledgeHelper, ValueResolver valueResolver) throws Exception {
        Map<String, Object> vars = new HashMap<>();
        List<String> declarationIds = knowledgeHelper.getMatch().getDeclarationIds();
        declarationIds.forEach(declarationId -> vars.put(declarationId, knowledgeHelper.getMatch().getDeclarationValue(declarationId)));

        evaluator.eval(vars);
    }
}
