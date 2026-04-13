package org.drools.drlx.builder;

import java.util.Locale;

public enum DrlxBuildCacheStrategy {
    NONE,
    RULE_AST;

    public static final String PROPERTY = "drlx.compiler.cacheStrategy";

    public static DrlxBuildCacheStrategy current() {
        String configured = System.getProperty(PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return fromProperty(configured);
        }
        return NONE;
    }

    public static DrlxBuildCacheStrategy fromProperty(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "none" -> NONE;
            case "ruleast", "rule_ast", "rule-ast", "ast" -> RULE_AST;
            default -> throw new IllegalArgumentException("Unknown DRLX build cache strategy: " + value);
        };
    }
}
