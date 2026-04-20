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

    public record CompilationUnitIR(String packageName, List<String> imports, List<RuleIR> rules) {
    }

    public record RuleIR(String name,
                         List<RuleAnnotationIR> annotations,
                         List<RuleItemIR> items) {
    }

    public record RuleAnnotationIR(Kind kind, String rawValue) {
        public enum Kind { SALIENCE, DESCRIPTION }
    }

    public sealed interface RuleItemIR permits PatternIR, ConsequenceIR {
    }

    public record PatternIR(String typeName,
                            String bindName,
                            String entryPoint,
                            List<String> conditions,
                            String castTypeName,
                            List<String> positionalArgs) implements RuleItemIR {
    }

    public record ConsequenceIR(String block) implements RuleItemIR {
    }
}
