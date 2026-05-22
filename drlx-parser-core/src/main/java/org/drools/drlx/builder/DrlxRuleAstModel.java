package org.drools.drlx.builder;

import java.util.List;

/**
 * In-memory intermediate representation produced by {@link DrlxToRuleAstVisitor}
 * and consumed by {@link DrlxRuleAstRuntimeBuilder}. Also the payload that
 * {@link DrlxRuleAstParseResult} serialises to protobuf for build caching.
 */
public final class DrlxRuleAstModel {

    private DrlxRuleAstModel() {
    }

    public record CompilationUnitIR(String packageName,
                                    String unitName,
                                    List<String> imports,
                                    List<RuleIR> rules) {
    }

    public record RuleParameterIR(String typeName, String paramName) { }

    public record RuleIR(String name,
                         List<RuleAnnotationIR> annotations,
                         List<RuleParameterIR> parameters,
                         List<LhsItemIR> lhs,
                         ConsequenceIR rhs) {
    }

    public record RuleAnnotationIR(Kind kind, String rawValue) {
        public enum Kind { SALIENCE, DESCRIPTION }
    }

    /** LHS tree node — pattern leaf, nested group element, eval-style guard, or accumulate. */
    public sealed interface LhsItemIR permits PatternIR, GroupElementIR, EvalIR, AccumulatePatternIR, CustomAccumulateIR {
    }

    public record PatternIR(String typeName,
                            String bindName,
                            String entryPoint,
                            List<String> conditions,
                            String castTypeName,
                            List<String> positionalArgs,
                            boolean passive,
                            List<String> watchedProperties) implements LhsItemIR {
    }

    public record GroupElementIR(Kind kind, List<LhsItemIR> children) implements LhsItemIR {
        public enum Kind {
            NOT,
            EXISTS,
            AND,
            OR
        }
    }

    public record ConsequenceIR(String block) {
    }

    public record EvalIR(String expression, List<String> referencedBindings) implements LhsItemIR {
        public EvalIR {
            referencedBindings = List.copyOf(referencedBindings);
        }
    }

    public record AccumulatePatternIR(LhsItemIR source,
                                      List<AccumulatorIR> accumulators) implements LhsItemIR {
        public AccumulatePatternIR {
            accumulators = List.copyOf(accumulators);
        }
    }

    public record AccumulatorIR(String resultTypeName,
                                String resultBindName,
                                String functionName,
                                List<String> argExpressions,
                                List<String> referencedBindings) {
        public AccumulatorIR {
            argExpressions     = List.copyOf(argExpressions);
            referencedBindings = List.copyOf(referencedBindings);
        }
    }

    public record CustomAccumulateIR(
        LhsItemIR source,
        List<InitVarIR> initVars,
        String actionBlock,
        String reverseBlock,
        String resultTypeName,
        String resultBindName,
        String resultExpression,
        List<String> referencedBindings
    ) implements LhsItemIR {
        public CustomAccumulateIR {
            initVars = List.copyOf(initVars);
            referencedBindings = List.copyOf(referencedBindings);
        }
    }

    public record InitVarIR(
        String typeName,
        String name,
        String initializer
    ) {
    }
}
