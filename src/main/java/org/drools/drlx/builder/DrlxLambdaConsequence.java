package org.drools.drlx.builder;

import org.drools.base.base.ValueResolver;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.consequence.Consequence;
import org.drools.core.rule.consequence.KnowledgeHelper;

public class DrlxLambdaConsequence implements Consequence<KnowledgeHelper>  {

    private String consequenceBlock;

    public DrlxLambdaConsequence(String consequenceBlock) {
        this.consequenceBlock = consequenceBlock;
    }

    @Override
    public String getName() {
        return RuleImpl.DEFAULT_CONSEQUENCE_NAME;
    }

    @Override
    public void evaluate(KnowledgeHelper knowledgeHelper, ValueResolver valueResolver) throws Exception {
        // TBD
        System.out.println("Executing consequence: \n" + consequenceBlock);
    }
}
