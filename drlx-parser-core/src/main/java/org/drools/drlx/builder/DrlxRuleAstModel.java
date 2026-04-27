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

    public record RuleIR(String name,
                         List<RuleAnnotationIR> annotations,
                         List<LhsItemIR> lhs,
                         ConsequenceIR rhs) {
    }

    public record RuleAnnotationIR(Kind kind, String rawValue) {
        public enum Kind { SALIENCE, DESCRIPTION }
    }

    /** LHS tree node — pattern leaf, nested group element, or eval-style guard. */
    public sealed interface LhsItemIR permits PatternIR, GroupElementIR, EvalIR {
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
}
