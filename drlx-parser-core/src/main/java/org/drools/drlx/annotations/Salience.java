package org.drools.drlx.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rule-level annotation controlling firing priority. Higher salience rules fire first.
 *
 * <pre>
 *     &#64;Salience(10)
 *     rule CheckAge1 {
 *         Person p : /persons[ age &gt; 18 ],
 *         do { System.out.println(p); }
 *     }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Salience {
    int value();
}
