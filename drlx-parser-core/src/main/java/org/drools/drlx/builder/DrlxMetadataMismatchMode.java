package org.drools.drlx.builder;

import java.util.Locale;

/**
 * Behavior when pre-built lambda metadata is missing, stale, or the
 * referenced class cannot be loaded.
 *
 * <p>Default is {@link #FAIL_FAST} — a mismatch is a build error because it
 * typically means the pre-built artifacts are out of sync with the source.
 * Set {@link #PROPERTY} to {@code fallback} to fall back to runtime
 * compilation instead.
 */
public enum DrlxMetadataMismatchMode {
    FAIL_FAST,
    FALLBACK;

    public static final String PROPERTY = "drlx.compiler.metadataMismatch";

    public static DrlxMetadataMismatchMode current() {
        String configured = System.getProperty(PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return fromProperty(configured);
        }
        return FAIL_FAST;
    }

    public static DrlxMetadataMismatchMode fromProperty(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "failfast", "fail-fast", "fail_fast" -> FAIL_FAST;
            case "fallback", "warn" -> FALLBACK;
            default -> throw new IllegalArgumentException("Unknown DRLX metadata mismatch mode: " + value);
        };
    }
}
