# DRLX Parser

DRLX Parser builds a Drools `KieBase` directly from `.drlx` rule files, skipping the intermediate Descr generation step. It supports a **2-step build** that pre-compiles lambda classes once and reuses them on subsequent builds for faster startup.

## Dependency

```xml
<dependency>
    <groupId>org.drools</groupId>
    <artifactId>drlx-parser</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Single-step build (simple)

Compiles everything from scratch each time:

```java
import org.drools.drlx.tools.DrlxCompiler;
import org.kie.api.KieBase;

DrlxCompiler compiler = new DrlxCompiler();
KieBase kieBase = compiler.build(Path.of("src/main/resources/rules.drlx"));

KieSession session = kieBase.newKieSession();
session.getEntryPoint("persons").insert(new Person("John", 25));
session.fireAllRules();
session.dispose();
```

If no pre-built metadata is found, `build()` automatically falls back to compiling from scratch.

### 2-step build (fast startup)

#### Step 1: Pre-build (one-time, at build time)

Compiles all lambda classes and saves them along with a metadata file:

```java
DrlxCompiler compiler = new DrlxCompiler();
compiler.preBuild(Path.of("src/main/resources/rules.drlx"));
```

This produces:
- `.class` files in `target/generated-classes/mvel/`
- `drlx-lambda-metadata.properties` in the same directory

#### Step 2: Build (repeated, at runtime)

```java
DrlxCompiler compiler = new DrlxCompiler();
KieBase kieBase = compiler.build(Path.of("src/main/resources/rules.drlx"));
```

`build()` automatically detects the metadata file and loads pre-compiled lambda classes instead of recompiling. If the metadata is missing or a rule expression has changed since pre-build, it falls back to normal compilation with a log warning.

## Input Sources

`DrlxCompiler` accepts multiple input types:

```java
DrlxCompiler compiler = new DrlxCompiler();

// From a file path
compiler.preBuild(Path.of("rules.drlx"));
KieBase kb1 = compiler.build(Path.of("rules.drlx"));

// From a String
String source = Files.readString(Path.of("rules.drlx"));
compiler.preBuild(source);
KieBase kb2 = compiler.build(source);

// From a classpath resource (InputStream)
try (InputStream is = getClass().getResourceAsStream("/rules.drlx")) {
    compiler.preBuild(is);
}
try (InputStream is = getClass().getResourceAsStream("/rules.drlx")) {
    KieBase kb3 = compiler.build(is);
}
```

## Custom Output Directory

By default, lambda classes and metadata are stored in `target/generated-classes/mvel/`. This can be changed via constructor or system property:

```java
// Via constructor
DrlxCompiler compiler = new DrlxCompiler(Path.of("my-custom-dir"));
```

```bash
# Via system property
java -Dmvel3.compiler.lambda.persistence.path=my-custom-dir ...
```

## DRLX Rule File Format

```
package org.example;

import org.example.domain.Person;
import org.example.domain.Address;

unit MyUnit;

rule CheckAge {
    Person p : /persons[ age > 18 ],
    do { System.out.println(p); }
}

rule CheckCity {
    Address a : /addresses[ city == "Tokyo" ],
        Person p : /persons[ age > 18 ],
    do { System.out.println(p + " lives in " + a); }
}
```

## How the 2-Step Build Works

```
Step 1 (Pre-build):
  .drlx file --> ANTLR4 parse --> DrlxPreBuildVisitor --> compiles lambdas
                                                       --> saves .class files to disk
                                                       --> writes metadata properties file

Step 2 (Runtime build):
  .drlx file --> ANTLR4 parse --> DrlxToRuleImplVisitor --> loads .class files from disk
                                                         --> skips compilation
                                                         --> builds KieBase
```

The metadata file (`drlx-lambda-metadata.properties`) maps each lambda to its pre-compiled class file. It also stores the expression text as a fingerprint, so if a rule changes between pre-build and runtime, the stale cache entry is detected and compilation falls back to normal.

## Building from Source

```bash
mvn clean install
```

### Running tests

```bash
# All tests
mvn test

# Compiler tests only
mvn test -Dtest="org.drools.drlx.tools.DrlxCompilerTest"
```

### Running JMH benchmarks

The project includes a JMH benchmark comparing KieBase build performance between
DRL executable-model and DRLX 2-step builds.

1. Build the fat jar:

```bash
mvn package -DskipTests
```

2. Run the benchmark:

```bash
java -jar target/drlx-benchmarks.jar \
  -jvmArgs "-Xms4g -Xmx4g -Dmvel3.compiler.lambda.resetOnTestStartup=true" \
  -foe true \
  org.drools.drlx.perf.KieBaseBuildBenchmark
```

3. Customize the rule count with `-p`:

```bash
java -jar target/drlx-benchmarks.jar \
  -jvmArgs "-Xms4g -Xmx4g -Dmvel3.compiler.lambda.resetOnTestStartup=true" \
  -foe true \
  -p ruleCount=10,50,100,200 \
  org.drools.drlx.perf.KieBaseBuildBenchmark
```

4. Quick smoke test (1 fork, no warmup, 1 iteration):

```bash
java -jar target/drlx-benchmarks.jar \
  -jvmArgs "-Xms4g -Xmx4g -Dmvel3.compiler.lambda.resetOnTestStartup=true" \
  -f 1 -wi 0 -i 1 -foe true \
  org.drools.drlx.perf.KieBaseBuildBenchmark
```
