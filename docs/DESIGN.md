# DRLX Parser Design

## Overview

DRLX Parser builds Drools `KieBase` objects directly from `.drlx` rule files,
bypassing the intermediate Descriptor generation step used by traditional
Drools. It achieves fast startup through a 2-step build strategy: pre-compile
lambda expressions once (at build time), then reuse them on every subsequent
startup.

**Coordinates:** `org.drools:drlx-parser:1.0.0-SNAPSHOT` (parent)
**Java:** 17+
**Key dependencies:** MVEL3 3.0.0-SNAPSHOT, ANTLR4 4.13.1, Drools 10.1.0, Protobuf 3.25.5

## Module Structure

```
drlx-parser/                        (parent POM)
├── drlx-parser-core/               (core library)
│   ├── src/main: org.drools.drlx
│   │    +-- builder/    Rule building, constraints, serialization, pre-build
│   │    +-- parser/     ANTLR grammars + visitors (Descr, JavaParser)
│   │    +-- tools/      Public API (DrlxCompiler)
│   │    +-- util/       Helpers (DrlxHelper)
│   └── src/test: org.drools.drlx
│        +-- domain/     Test POJOs (Person, Address)
└── drlx-parser-benchmark/          (JMH benchmarks)
    └── src/main: org.drools.drlx
         +-- perf/       JMH benchmarks + PreBuildRunner
         +-- domain/     Domain POJOs (Person, Address) -- duplicated from core test
```

## DRLX Language

DRLX extends the MVEL3 grammar with rule-specific constructs. The ANTLR grammar
hierarchy is:

```
JavaLexer.g4 / JavaParser.g4       (Java expression support)
  <- Mvel3Lexer.g4 / Mvel3Parser.g4  (MVEL3 expression language)
    <- DrlxLexer.g4 / DrlxParser.g4    (rule declarations, patterns, OOPath)
```

A `.drlx` file contains:

```
package org.example;
import org.example.Person;
unit MyUnit;

rule "age check" {
    Person $p : /persons[ age > 18 ],
    do {
        System.out.println($p.getName());
    }
}
```

Key syntax elements:
- **Patterns:** `Type $var : /entryPoint[ conditions ]`
- **OOPath:** navigates object graphs with nested filtering
- **Alpha conditions:** single-fact constraints (`age > 18`)
- **Beta conditions:** cross-fact joins (`age < $other.age`)
- **Consequences:** Java statement blocks in `do { ... }`

## Architecture

### Data Flow: Normal Build

```
DRLX source
  |
  v
ANTLR Lexer/Parser
  |
  v
DrlxCompilationUnitContext (parse tree)
  |
  v
DrlxToRuleAstVisitor           (only ANTLR-aware step)
  |
  v
DrlxRuleAstModel IR records    (CompilationUnitIR, RuleIR, ...)
  |
  v
DrlxRuleAstRuntimeBuilder      (holds a DrlxLambdaCompiler)
  |-- buildRule()              -> RuleImpl
  |-- buildPattern()           -> Pattern + constraints
  |-- DrlxLambdaCompiler
  |     +-- createLambda*()    -> pending lambda sources
  |
  v
MVELBatchCompiler.compile()    (single javac call for all lambdas)
  |
  v
List<KiePackage>
  |
  v
RuleBaseFactory.newRuleBase() + addPackages()
  |
  v
KieBase
```

### Data Flow: 2-Step Build

**Step 1 -- Pre-build (at build time, once):**

```
DRLX source
  |
  v
DrlxRuleBuilder.preBuild(source, outputDir)
  |-- ANTLR parse -> DrlxToRuleAstVisitor -> DrlxRuleAstModel IR
  |-- persistBuildCache()               -> serialize IR to protobuf
  |-- DrlxRuleAstRuntimeBuilder(DrlxPreBuildLambdaCompiler)
  |     |-- build(IR), compiling lambdas through the pre-build compiler
  |     |-- record metadata (rule.counter -> className|physicalId|expression)
  |
  v
Output:
  drlx-lambda-metadata.properties    (lambda mapping)
  drlx-rule-ast.pb                   (compact rule AST cache)
  *.class files                      (compiled evaluators)
```

**Step 2 -- Runtime build (on every startup):**

```
DRLX source + pre-built artifacts
  |
  v
DrlxRuleBuilder.build(source, metadata, cacheDir)
  |-- Try buildFromCache():
  |     Load drlx-rule-ast.pb -> hash check -> rebuild from IR records
  |-- Fallback: normal parse (ANTLR -> DrlxToRuleAstVisitor -> IR)
  |-- For each constraint/consequence:
  |     Try loadPreCompiledEvaluator() (metadata match -> use cached .class)
  |     Fallback: compile from source
  |
  v
KieBase
```

## Key Classes

### Public API

| Class | Role |
|-------|------|
| `DrlxCompiler` | Facade. `preBuild(path)` and `build(path)` for the 2-step workflow. |

### Builder Package

| Class | Role |
|-------|------|
| `DrlxRuleBuilder` | Orchestrator. Coordinates parsing, cache, pre-build, and batch compilation. |
| `DrlxToRuleAstVisitor` | Walks the ANTLR parse tree and produces `DrlxRuleAstModel` IR records. The only ANTLR-aware step in the pipeline. |
| `DrlxRuleAstModel` | In-memory IR: `CompilationUnitIR`, `RuleIR`, `PatternIR`, `ConsequenceIR`. Shared by runtime build and proto serialization. |
| `DrlxRuleAstRuntimeBuilder` | Builds `KiePackages` from `DrlxRuleAstModel` IR. Uses `DrlxLambdaCompiler` via composition. |
| `DrlxLambdaCompiler` | Owns lambda compilation: constraints, beta constraints, consequences, batch mode, pre-built metadata reuse. |
| `DrlxPreBuildLambdaCompiler` | Extends `DrlxLambdaCompiler`. Records lambda metadata during pre-build. |
| `DrlxLambdaConstraint` | Alpha constraint. Wraps `Evaluator<Object, Void, Boolean>`. |
| `DrlxLambdaBetaConstraint` | Beta (join) constraint. Wraps `Evaluator<Map<String,Object>, Void, Boolean>`. Uses reflection-based property extraction. |
| `DrlxLambdaConsequence` | Consequence action. Wraps `Evaluator<Map<String,Object>, Void, String>`. |
| `DrlxLambdaMetadata` | Pipe-delimited properties file for lambda mapping (`rule.counter=fqn\|physicalId\|expression`). |
| `DrlxRuleAstParseResult` | Protobuf serialization of `DrlxRuleAstModel` IR (save/load). No ANTLR dependency. |
| `DrlxBuildCacheStrategy` | Enum: `NONE`, `RULE_AST`. Configured via `drlx.compiler.cacheStrategy`. |
| `DrlxMetadataMismatchMode` | Enum: `FAIL_FAST` (default), `FALLBACK`. Configured via `drlx.compiler.metadataMismatch`. Controls behavior when pre-built lambda metadata is stale or missing. |
| `EvaluatorSink` | Package-private interface implemented by the three lambda classes so `DrlxLambdaCompiler.compileBatch()` can bind compiled evaluators uniformly. |
| `DrlxRuleUnit` | Wraps unit declaration. |

### Parser Package

| Class | Role |
|-------|------|
| `DrlxToDescrVisitor` | Legacy: produces `drools-drl-ast` Descriptor objects. |
| `DrlxToJavaParserVisitor` | Converts ANTLR tree to JavaParser AST. Used by tooling (IDE support, formatting). |
| `TolerantDrlxParser` | Error-tolerant parsing entry point. |
| `TolerantDrlxToJavaParserVisitor` | Error-tolerant variant of `DrlxToJavaParserVisitor`. |

### Utility

| Class | Role |
|-------|------|
| `DrlxHelper` | Parsing helpers, type resolution, expression extraction. |

## Constraint Types

Alpha and beta constraints differ in how they receive fact data:

| | Alpha (`DrlxLambdaConstraint`) | Beta (`DrlxLambdaBetaConstraint`) |
|---|---|---|
| Evaluator signature | `Evaluator<Object, Void, Boolean>` | `Evaluator<Map<String,Object>, Void, Boolean>` |
| Input | Single fact object | Map of bound variable name -> fact |
| MVEL declarations | `Declaration<?>[]` from pattern type | Merged from all referenced bindings |
| Example | `age > 18` | `age < $p1.age` |
| Property access | Direct field access | `Method.invoke()` via cached `PropertyExtractor` |

## Build Cache Strategy: RuleAST

The `RULE_AST` cache strategy serializes the `DrlxRuleAstModel` IR to
protobuf (`drlx-rule-ast.pb`). At load time, `DrlxRuleAstRuntimeBuilder`
reconstructs `RuleImpl`/`KiePackage` directly from the IR records --
no ANTLR reparse needed.

| Aspect | Details |
|--------|---------|
| Proto file | `drlx_rule_ast.proto` |
| Output file | `drlx-rule-ast.pb` |
| What it saves | `DrlxRuleAstModel` IR (rules, patterns, conditions as strings) |
| Loads into | Java records (`CompilationUnitIR`, `RuleIR`, `PatternIR`, `ConsequenceIR`) |
| Runtime builder | `DrlxRuleAstRuntimeBuilder` |
| Reflection needed | No |
| Skips at load time | ANTLR parsing + tree walking |

Hash-based invalidation: the strategy stores a SHA-256 hash of the source. On
load, if the hash mismatches, the cache is discarded and normal parsing runs.

## Batch Compilation

All lambda sources are collected in `pendingLambdas` during tree walking. A
single `MVELBatchCompiler.compile()` call compiles them all via one javac
invocation. This eliminates per-lambda compiler startup overhead.

**Impact:** 2.95x faster for no-persist builds, 8.74x faster for pre-build
phase (see `PERF_ANALYSIS.md`).

## Lambda Deduplication

During pre-build, expressions are compared for deduplication. Multiple rules
with identical constraint expressions (e.g. `age > 18`) reuse the same compiled
evaluator class. `DrlxPreBuildLambdaCompiler` tracks this via
`PendingPreBuildInfo` records and the metadata properties file.

## Class Hierarchy

The build pipeline separates three concerns, each in its own hierarchy:

```
ANTLR walking:
  DrlxParserBaseVisitor<Object>
    +-- DrlxToRuleAstVisitor              (only ANTLR-aware class)

Lambda compilation:
  DrlxLambdaCompiler
    +-- DrlxPreBuildLambdaCompiler        (overrides onLambdaCreated hook
                                           to record metadata during pre-build)

RuleImpl building:
  DrlxRuleAstRuntimeBuilder               (composition: holds a DrlxLambdaCompiler)
```

`DrlxToRuleAstVisitor` is the single ANTLR-aware step; everything downstream
works against the `DrlxRuleAstModel` IR records. `DrlxPreBuildLambdaCompiler`
intercepts constraint/consequence creation at the lambda-compilation layer
(no ANTLR walking). `DrlxRuleAstRuntimeBuilder` drives the IR → RuleImpl
translation and delegates lambda creation to its injected `DrlxLambdaCompiler`.

## Configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `drlx.compiler.cacheStrategy` | `none` | Build cache: `none`, `ruleAst` |
| `drlx.compiler.metadataMismatch` | `failFast` | Behavior on missing/stale pre-built lambda metadata: `failFast` or `fallback` |
| `mvel3.compiler.lambda.persistence` | `true` | Enable disk I/O for compiled .class files |
| `mvel3.compiler.lambda.persistence.path` | `target/generated-classes/mvel` | Output directory for pre-built artifacts |
| `mvel3.compiler.lambda.resetOnTestStartup` | `false` | Clear persisted classes on JVM startup |

## Performance Characteristics

See `PERF_ANALYSIS.md` for detailed benchmarks. The RuleAST cache strategy
resolved the multiJoin slowdown that was previously observed (DRLX was 1.84x
slower than exec-model for multiJoin without caching).

Key findings:
- Batch compilation provides 2.95x speedup for cold builds
- RuleAST eliminates ANTLR parsing cost at load time
- Remaining dominant cost: `defineHiddenClass()` per lambda at class-loading time

## Known Limitations

1. **`findReferencedBindings()` uses regex** to detect bound variable references
   in expression strings. Could produce false positives/negatives for complex
   expressions.

2. **Beta constraint evaluation allocates a HashMap per call** in `buildEvalMap()`
   and `evaluate()`. This creates GC pressure under high-throughput rule matching.

3. **`Method.invoke()` in hot path** -- `DrlxLambdaBetaConstraint` uses
   reflection-based property extraction on every constraint evaluation.

4. **Metadata pipe delimiter** -- `DrlxLambdaMetadata` uses `|` as separator
   with no escaping. Expressions containing `|` would corrupt the format
   (unlikely in practice since MVEL uses `||`).

## Testing

All tests live in `drlx-parser-core`:

| Test Class | Focus |
|-----------|-------|
| `DrlxCompilerTest` | 2-step build, RuleAST serialization, lambda deduplication |
| `DrlxCompilerNoPersistTest` | In-memory build (no disk I/O) |
| `DrlxRuleBuilderTest` | Direct rule building, alpha/beta/multi-join constraints |
| `DrlxParserTest` | Low-level ANTLR parser verification |
| `DrlxToDescrVisitorTest` | Legacy Descriptor generation |
| `DrlxToJavaParserVisitorTest` | JavaParser AST conversion |
| `TolerantDrlxToJavaParserVisitorTest` | Error-tolerant parsing edge cases |

**Framework:** JUnit 5 + AssertJ
**Domain objects:** `Person` (age, name), `Address` (city)

## Benchmarks

JMH benchmarks live in `drlx-parser-benchmark` (separate module to keep JMH
dependencies out of the core artifact):

| Class | Purpose |
|-------|---------|
| `KieBaseBuildNoPersistenceBenchmark` | Cold-start build (no pre-built artifacts) |
| `KieBasePreBuildPersistenceBenchmark` | Pre-build phase timing |
| `KieBaseBuildUsingPreBuildArtifactsBenchmark` | Warm build using pre-built artifacts |
| `PreBuildRunner` | Separate-JVM pre-compilation for UsingPreBuild benchmark |

Build the benchmark fat jar: `mvn package -DskipTests -Pbenchmark -pl drlx-parser-benchmark -am`
