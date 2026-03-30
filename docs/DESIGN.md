# DRLX Parser Design

## Overview

DRLX Parser builds Drools `KieBase` objects directly from `.drlx` rule files,
bypassing the intermediate Descriptor generation step used by traditional
Drools. It achieves fast startup through a 2-step build strategy: pre-compile
lambda expressions once (at build time), then reuse them on every subsequent
startup.

**Coordinates:** `org.drools:drlx-parser:1.0.0-SNAPSHOT`
**Java:** 17+
**Key dependencies:** MVEL3 3.0.0-SNAPSHOT, ANTLR4 4.13.1, Drools 10.1.0, Protobuf 3.25.5

## Package Structure

```
org.drools.drlx
 +-- builder/      Core: rule building, constraints, serialization, pre-build
 +-- parser/       ANTLR grammars + visitors (Descr, JavaParser)
 +-- tools/        Public API (DrlxCompiler)
 +-- perf/         JMH benchmarks
 +-- util/         Helpers (DrlxHelper)
 +-- domain/       Test POJOs (Person, Address) -- should move to src/test
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
DrlxToRuleImplVisitor
  |-- buildRule()       -> RuleImpl
  |-- buildPattern()    -> Pattern + constraints
  |-- createLambda*()   -> pending lambda sources
  |
  v
MVELBatchCompiler.compile()   (single javac call for all lambdas)
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
  |-- Parse ANTLR tree
  |-- persistBuildCache()  -> serialize parse tree or rule AST to protobuf
  |-- DrlxPreBuildVisitor
  |     |-- walk tree, compile lambdas
  |     |-- record metadata (rule.counter -> className|physicalId|expression)
  |
  v
Output:
  drlx-lambda-metadata.properties    (lambda mapping)
  drlx-parse-tree.pb  OR  drlx-rule-ast.pb   (optional cache)
  *.class files                       (compiled evaluators)
```

**Step 2 -- Runtime build (on every startup):**

```
DRLX source + pre-built artifacts
  |
  v
DrlxRuleBuilder.build(source, metadata, cacheDir)
  |-- Try buildFromCache():
  |     Load protobuf snapshot -> hash check -> rebuild from snapshot
  |-- Fallback: normal parse
  |-- For each constraint/consequence:
  |     Try loadPreCompiledEvaluator() (hash match -> use cached .class)
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

| Class | Lines | Role |
|-------|-------|------|
| `DrlxRuleBuilder` | 188 | Orchestrator. Coordinates parsing, cache, pre-build, and batch compilation. |
| `DrlxToRuleImplVisitor` | 486 | Core visitor. Walks ANTLR tree -> RuleImpl, Pattern, constraints, consequences. |
| `DrlxPreBuildVisitor` | 134 | Extends `DrlxToRuleImplVisitor`. Records lambda metadata during pre-build. |
| `DrlxRuleAstRuntimeBuilder` | 125 | Extends `DrlxToRuleImplVisitor`. Rebuilds KiePackages from RuleAST snapshot data records. |
| `DrlxLambdaConstraint` | 128 | Alpha constraint. Wraps `Evaluator<Object, Void, Boolean>`. |
| `DrlxLambdaBetaConstraint` | 259 | Beta (join) constraint. Wraps `Evaluator<Map<String,Object>, Void, Boolean>`. Uses reflection-based property extraction. |
| `DrlxLambdaConsequence` | 67 | Consequence action. Wraps `Evaluator<Map<String,Object>, Void, String>`. |
| `DrlxLambdaMetadata` | 85 | Pipe-delimited properties file for lambda mapping (`rule.counter=fqn\|physicalId\|expression`). |
| `DrlxParseTreeSnapshot` | 221 | Full ANTLR parse tree serialization via protobuf. Rehydrates via reflection. |
| `DrlxRuleAstSnapshot` | 204 | Compact domain-specific AST serialization via protobuf. No reflection needed. |
| `DrlxBuildCacheStrategy` | 32 | Enum: `NONE`, `PARSE_TREE`, `RULE_AST`. Configured via `drlx.compiler.cacheStrategy`. |
| `DrlxRuleUnit` | 24 | Wraps unit declaration. |

### Parser Package

| Class | Lines | Role |
|-------|-------|------|
| `DrlxToDescrVisitor` | 180 | Legacy: produces `drools-drl-ast` Descriptor objects. |
| `DrlxToJavaParserVisitor` | 1990 | Converts ANTLR tree to JavaParser AST. Used by tooling (IDE support, formatting). |
| `TolerantDrlxParser` | 26 | Error-tolerant parsing entry point. |
| `TolerantDrlxToJavaParserVisitor` | 302 | Error-tolerant variant of `DrlxToJavaParserVisitor`. |

### Utility

| Class | Lines | Role |
|-------|-------|------|
| `DrlxHelper` | 164 | Parsing helpers, type resolution, expression extraction. |

## Constraint Types

Alpha and beta constraints differ in how they receive fact data:

| | Alpha (`DrlxLambdaConstraint`) | Beta (`DrlxLambdaBetaConstraint`) |
|---|---|---|
| Evaluator signature | `Evaluator<Object, Void, Boolean>` | `Evaluator<Map<String,Object>, Void, Boolean>` |
| Input | Single fact object | Map of bound variable name -> fact |
| MVEL declarations | `Declaration<?>[]` from pattern type | Merged from all referenced bindings |
| Example | `age > 18` | `age < $p1.age` |
| Property access | Direct field access | `Method.invoke()` via cached `PropertyExtractor` |

## Build Cache Strategies

Two protobuf-based serialization strategies, unified under `DrlxBuildCacheStrategy`:

| Aspect | `PARSE_TREE` | `RULE_AST` |
|--------|-------------|-----------|
| Proto file | `drlx_parse_tree.proto` | `drlx_rule_ast.proto` |
| Output file | `drlx-parse-tree.pb` | `drlx-rule-ast.pb` |
| What it saves | Full ANTLR tree + tokens | Domain-specific AST (rules, patterns, conditions as strings) |
| Loads into | Rehydrated `DrlxCompilationUnitContext` | Java records (`CompilationUnitData`, `RuleData`, `PatternData`, `ConsequenceData`) |
| Runtime builder | Existing `DrlxToRuleImplVisitor` | `DrlxRuleAstRuntimeBuilder` |
| Reflection needed | Yes (ANTLR context class instantiation) | No |
| Skips at load time | ANTLR parsing | ANTLR parsing + tree walking |

`RULE_AST` is the preferred strategy -- smaller output, no reflection, skips
more work. `PARSE_TREE` preserves full fidelity for debugging.

Hash-based invalidation: both strategies store a hash of the source. On load,
if the hash mismatches, the cache is discarded and normal parsing runs.

## Batch Compilation

When `drlx.compiler.batch=true` (default), all lambda sources are collected
in `pendingLambdas` during tree walking. A single `MVELBatchCompiler.compile()`
call compiles them all via one javac invocation. This eliminates per-lambda
compiler startup overhead.

**Impact:** 2.95x faster for no-persist builds, 8.74x faster for pre-build
phase (see `PERF_ANALYSIS.md`).

## Lambda Deduplication

During pre-build, expressions are compared for deduplication. Multiple rules
with identical constraint expressions (e.g. `age > 18`) reuse the same compiled
evaluator class. `DrlxPreBuildVisitor` tracks this via `PendingPreBuildInfo`
records and the metadata properties file.

## Inheritance Hierarchy

```
DrlxParserBaseVisitor<List<KiePackage>>
  +-- DrlxToRuleImplVisitor          (normal build: tree -> RuleImpl)
        +-- DrlxPreBuildVisitor      (pre-build: records metadata)
        +-- DrlxRuleAstRuntimeBuilder (load from RuleAST snapshot)
```

`DrlxPreBuildVisitor` intercepts constraint/consequence creation to record
metadata. `DrlxRuleAstRuntimeBuilder` drives the same lambda-loading logic
from snapshot data records instead of ANTLR tree walking.

## Configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `drlx.compiler.batch` | `true` | Batch lambda compilation (single javac call) |
| `drlx.compiler.cacheStrategy` | `none` | Build cache: `none`, `parseTree`, `ruleAst` |
| `drlx.compiler.serializedParseTree` | `false` | Legacy boolean (backward compat for `PARSE_TREE`) |
| `mvel3.compiler.lambda.persistence` | `true` | Enable disk I/O for compiled .class files |
| `mvel3.compiler.lambda.persistence.path` | `target/generated-classes/mvel` | Output directory for pre-built artifacts |
| `mvel3.compiler.lambda.resetOnTestStartup` | `false` | Clear persisted classes on JVM startup |

## Performance Characteristics

See `PERF_ANALYSIS.md` for detailed benchmarks. Summary (100 rules, `multiJoin`):

| Scenario | DRLX | Exec-Model | Ratio |
|----------|------|-----------|-------|
| NoPersist (cold) | 2,036 ms | 1,247 ms | 1.63x |
| PreBuild phase | 499 ms | 164 ms | 3.04x |
| UsingPreBuild (warm) | 2,397 ms | 147 ms | 16.3x |

The `UsingPreBuild` gap is dominated by `defineHiddenClass()` cost per lambda
at class-loading time. ANTLR parsing cost is eliminated but class definition
overhead remains.

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

5. **`BATCH_ENABLED` flag** -- always true, dead code path for `false` remains.

## Testing

| Test Class | Focus |
|-----------|-------|
| `DrlxCompilerTest` | 2-step build, both serialization strategies, lambda deduplication |
| `DrlxCompilerNoPersistTest` | In-memory build (no disk I/O) |
| `DrlxRuleBuilderTest` | Direct rule building, alpha/beta/multi-join constraints |
| `DrlxParserTest` | Low-level ANTLR parser verification |
| `DrlxToDescrVisitorTest` | Legacy Descriptor generation |
| `DrlxToJavaParserVisitorTest` | JavaParser AST conversion |
| `TolerantDrlxToJavaParserVisitorTest` | Error-tolerant parsing edge cases |

**Framework:** JUnit 5 + AssertJ
**Domain objects:** `Person` (age, name), `Address` (city)
