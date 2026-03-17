package org.drools.drlx.perf;

import java.io.IOException;

import org.drools.drlx.domain.Person;
import org.drools.drlx.tools.DrlxCompiler;
import org.drools.model.codegen.ExecutableModelProject;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

/**
 * a convenient main class to run DRL/DRLX prepared for benchmark to confirm if the rule is valid
 */
public class QuickRun {

    public static void main(String[] args) throws IOException {
        System.setProperty("mvel3.compiler.lambda.persistence", "false");

        runDrl();

        runDrlx();
    }

    private static void runDrl() {
        // ---- DRL
        String drl = KieBaseBuildNoPersistenceBenchmark.generateDrl(10, "multiJoin");
        System.out.println(drl);
        KieBase kieBase = new KieHelper()
                .addContent(drl, ResourceType.DRL)
                .build(ExecutableModelProject.class);
        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons1").insert(new Person("John", 30));
        kieSession.getEntryPoint("persons2").insert(new Person("Paul", 27));
        kieSession.getEntryPoint("persons3").insert(new Person("George", 26));
        int fired = kieSession.fireAllRules();
        System.out.println("fired = " + fired);
    }

    private static void runDrlx() throws IOException {
        // ---- DRLX
        String drl = KieBaseBuildNoPersistenceBenchmark.generateDrlx(10, "multiJoin");
        System.out.println(drl);
        DrlxCompiler compiler = DrlxCompiler.noPersist();
        KieBase kieBase = compiler.build(drl);
        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons1").insert(new Person("John", 30));
        kieSession.getEntryPoint("persons2").insert(new Person("Paul", 27));
        kieSession.getEntryPoint("persons3").insert(new Person("George", 26));
        int fired = kieSession.fireAllRules();
        System.out.println("fired = " + fired);
    }
}
