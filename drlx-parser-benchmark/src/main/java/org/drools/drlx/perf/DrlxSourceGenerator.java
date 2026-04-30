package org.drools.drlx.perf;

/**
 * Utility class for generating DRL and DRLX rule source code for benchmarking.
 */
public final class DrlxSourceGenerator {

    private DrlxSourceGenerator() {
        // Utility class
    }

    public static String generateDrl(int count, String ruleType) {
        return switch (ruleType) {
            case "join" -> generateDrlJoin(count);
            case "multiJoin" -> generateDrlMultiJoin(count);
            case "multiAlpha" -> generateDrlMultiAlpha(count);
            default -> generateDrlAlpha(count);
        };
    }

    public static String generateDrlx(int count, String ruleType) {
        return switch (ruleType) {
            case "join" -> generateDrlxJoin(count);
            case "multiJoin" -> generateDrlxMultiJoin(count);
            case "multiAlpha" -> generateDrlxMultiAlpha(count);
            default -> generateDrlxAlpha(count);
        };
    }

    static String generateDrlAlpha(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule \"Rule_").append(i).append("\"\n");
            sb.append("when\n");
            sb.append("    $p : Person( age > ").append(i).append(" ) from entry-point \"persons\"\n");
            sb.append("then\n");
            sb.append("    System.out.println($p);\n");
            sb.append("end\n\n");
        }
        return sb.toString();
    }

    static String generateDrlxAlpha(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n");
        sb.append("import org.drools.drlx.ruleunit.MyUnit;\n\n");
        sb.append("unit MyUnit;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule Rule_").append(i).append(" {\n");
            sb.append("    Person p : /persons[ age > ").append(i).append(" ],\n");
            sb.append("    do { System.out.println(p); }\n");
            sb.append("}\n\n");
        }
        return sb.toString();
    }

    static String generateDrlJoin(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule \"Rule_").append(i).append("\"\n");
            sb.append("when\n");
            sb.append("    $p1 : Person( age > ").append(i).append(" ) from entry-point \"persons1\"\n");
            sb.append("    $p2 : Person( age < $p1.age ) from entry-point \"persons2\"\n");
            sb.append("then\n");
            sb.append("    System.out.println($p2);\n");
            sb.append("end\n\n");
        }
        return sb.toString();
    }

    static String generateDrlxJoin(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n");
        sb.append("import org.drools.drlx.ruleunit.MyUnit;\n\n");
        sb.append("unit MyUnit;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule Rule_").append(i).append(" {\n");
            sb.append("    Person p1 : /persons1[ age > ").append(i).append(" ],\n");
            sb.append("    Person p2 : /persons2[ age < p1.age ],\n");
            sb.append("    do { System.out.println(p2); }\n");
            sb.append("}\n\n");
        }
        return sb.toString();
    }

    static String generateDrlMultiJoin(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule \"Rule_").append(i).append("\"\n");
            sb.append("when\n");
            sb.append("    $p1 : Person( age > ").append(i).append(" ) from entry-point \"persons1\"\n");
            sb.append("    $p2 : Person( age < $p1.age ) from entry-point \"persons2\"\n");
            sb.append("    $p3 : Person( age > $p1.age - $p2.age ) from entry-point \"persons3\"\n");
            sb.append("then\n");
            sb.append("    System.out.println($p3);\n");
            sb.append("end\n\n");
        }
        return sb.toString();
    }

    static String generateDrlxMultiJoin(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n");
        sb.append("import org.drools.drlx.ruleunit.MyUnit;\n\n");
        sb.append("unit MyUnit;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule Rule_").append(i).append(" {\n");
            sb.append("    Person p1 : /persons1[ age > ").append(i).append(" ],\n");
            sb.append("    Person p2 : /persons2[ age < p1.age ],\n");
            sb.append("    Person p3 : /persons3[ age > p1.age - p2.age ],\n");
            sb.append("    do { System.out.println(p3); }\n");
            sb.append("}\n\n");
        }
        return sb.toString();
    }

    static String generateDrlMultiAlpha(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule \"Rule_").append(i).append("\"\n");
            sb.append("when\n");
            sb.append("    $p : Person( age == ").append(i).append(", value1 == \"A\", value2 == \"B\", value3 == \"C\" ) from entry-point \"persons\"\n");
            sb.append("then\n");
            sb.append("    System.out.println($p);\n");
            sb.append("end\n\n");
        }
        return sb.toString();
    }

    static String generateDrlxMultiAlpha(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n");
        sb.append("import org.drools.drlx.ruleunit.MyUnit;\n\n");
        sb.append("unit MyUnit;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule Rule_").append(i).append(" {\n");
            sb.append("    Person p : /persons[ age == ").append(i).append(", value1 == \"A\", value2 == \"B\", value3 == \"C\" ],\n");
            sb.append("    do { System.out.println(p); }\n");
            sb.append("}\n\n");
        }
        return sb.toString();
    }
}
