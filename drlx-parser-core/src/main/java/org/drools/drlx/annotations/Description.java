package org.drools.drlx.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rule-level annotation attaching human-readable description metadata.
 * Stored under the {@code "Description"} meta-attribute at build time.
 *
 * <pre>
 *     &#64;Description("Checks an adult person")
 *     rule CheckAge1 { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Description {
    String value();
}
