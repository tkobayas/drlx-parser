# Performance Analysis: DRLX vs Executable-Model

## All Benchmark Results (SingleShotTime, cold start, 100 rules)

### Before Batch Compilation Optimization

```
Benchmark                                                             (ruleCount)  Mode  Cnt     Score     Error  Units
KieBaseBuildNoPersistenceBenchmark.buildWithDrlxNoPersist                     100    ss    5  5994.439 ± 138.730  ms/op
KieBaseBuildNoPersistenceBenchmark.buildWithExecutableModel                   100    ss    5  1265.521 ± 102.720  ms/op
KieBaseBuildUsingPreBuildArtifactsBenchmark.buildWithDrlx                     100    ss    5  2437.612 ± 149.021  ms/op
KieBaseBuildUsingPreBuildArtifactsBenchmark.buildWithExecutableModel          100    ss    5   133.336 ±  18.898  ms/op
KieBasePreBuildPersistenceBenchmark.preBuildWithDrlx                          100    ss    5  4385.426 ± 407.721  ms/op
KieBasePreBuildPersistenceBenchmark.preBuildWithExecutableModel               100    ss    5  1164.814 ± 205.089  ms/op
```

### After Batch Compilation Optimization

```
Benchmark                                                             (ruleCount)  Mode  Cnt     Score     Error  Units
KieBaseBuildNoPersistenceBenchmark.buildWithDrlxNoPersist                     100    ss    5  2035.796 ± 127.861  ms/op
KieBaseBuildNoPersistenceBenchmark.buildWithExecutableModel                   100    ss    5  1246.619 ±  45.892  ms/op
KieBaseBuildUsingPreBuildArtifactsBenchmark.buildWithDrlx                     100    ss    5  2396.726 ± 106.791  ms/op
KieBaseBuildUsingPreBuildArtifactsBenchmark.buildWithExecutableModel          100    ss    5   147.074 ±  40.853  ms/op
KieBasePreBuildPersistenceBenchmark.preBuildWithDrlx                          100    ss    5  4359.349 ± 311.752  ms/op
KieBasePreBuildPersistenceBenchmark.preBuildWithExecutableModel               100    ss    5  1164.982 ± 199.239  ms/op
```

KieBase building is a one-time process, so SingleShotTime (`ss`) with no warmup is the appropriate benchmark mode. It measures true cold-start cost in a fresh JVM (5 forks, 1 measurement each).

## Summary of Ratios

### Before Batch Compilation

| Scenario | DRLX (ms) | Exec-Model (ms) | Ratio | What it measures |
|----------|-----------|-----------------|-------|------------------|
| **NoPersist** | 5994 | 1266 | **4.74x** | Full in-memory build (compile + KieBase creation) |
| **PreBuild** | 4385 | 1165 | **3.76x** | Compile + persist to disk (maven plugin phase) |
| **UsingPreBuild** | 2438 | 133 | **18.3x** | Load pre-built artifacts → KieBase (runtime) |

### After Batch Compilation

| Scenario | DRLX (ms) | Exec-Model (ms) | Ratio | What it measures |
|----------|-----------|-----------------|-------|------------------|
| **NoPersist** | 2036 | 1247 | **1.63x** | Full in-memory build (compile + KieBase creation) |
| **PreBuild** | 4359 | 1165 | **3.74x** | Compile + persist to disk (maven plugin phase) |
| **UsingPreBuild** | 2397 | 147 | **16.3x** | Load pre-built artifacts → KieBase (runtime) |

The 2-step build splits NoPersist into PreBuild (offline) + UsingPreBuild (runtime).

## Batch Compilation Optimization — Impact Analysis

The batch compilation optimization collects all 200 generated Java sources during the ANTLR tree walk and compiles them in a **single** `KieMemoryCompiler.compile()` call, eliminating 199 redundant Java compiler startup/initialization cycles.

### What changed

| Benchmark | Before | After | Change | Explanation |
|-----------|--------|-------|--------|-------------|
| **NoPersist** | 5994ms | **2036ms** | **2.95x faster** | 200 javac calls → 1 batch call. Compiler startup overhead (~3000-4000ms) eliminated. |
| **PreBuild** | 4385ms | 4359ms | ~0.6% (noise) | Batch mode is gated by `!LambdaRegistry.PERSISTENCE_ENABLED`. PreBuild uses persistence → old eager path unchanged. |
| **UsingPreBuild** | 2438ms | 2397ms | ~1.7% (noise) | Loads pre-compiled .class files from disk. No javac involved → batch optimization has zero effect. |

### Why PreBuild did not improve

Batch mode is only enabled when `LambdaRegistry.PERSISTENCE_ENABLED == false` (the no-persist path). The PreBuild benchmark runs with persistence enabled (the default), so it still uses the eager per-lambda compile + `LambdaRegistry` dedup/persistence flow. Applying batch compilation to the persistence path requires a more complex two-phase flow: transpile all sources → batch javac → then register/persist each output with `LambdaRegistry`.

### Why UsingPreBuild did not improve

This benchmark loads pre-compiled `.class` files from disk and calls `defineHiddenClass()` 200 times. There is no javac compilation on this path at all — the bottleneck is `defineHiddenClass()` (~62% of time) and `MethodByteCodeExtractor` (~29%). Batching javac has zero effect here.

### Implementation details

**Files changed:**

| File | Change |
|------|--------|
| `MVELCompiler.java` | Added `TranspiledSource` record, `transpileToSource()` (transpile without javac), `resolveEvaluator()` (instantiate from ClassManager) |
| `DrlxLambdaConstraint.java` | Added `setEvaluator()` for deferred resolution after batch compile |
| `DrlxLambdaConsequence.java` | Added `setEvaluator()` for deferred resolution after batch compile |
| `DrlxToRuleImplVisitor.java` | Added batch mode: `enableBatchMode()`, `compileBatch()`, pending lambda tracking with unique class names |
| `DrlxRuleBuilder.java` | `parse()` enables batch mode when `!LambdaRegistry.PERSISTENCE_ENABLED`, calls `compileBatch()` after tree walk |

**Flow (batch mode):**

```
DrlxRuleBuilder.parse()
├── visitor.enableBatchMode(sharedClassManager)
├── visitor.visitDrlxCompilationUnit()       // tree walk
│   └── for each lambda:
│       ├── MVELCompiler.transpileToSource() // MVEL → Java source (cheap, ~2-5ms)
│       ├── pendingSources.put(fqn, source)  // collect
│       └── return constraint/consequence with null evaluator
└── visitor.compileBatch()
    ├── KieMemoryCompiler.compile(sharedClassManager, allSources, classLoader)  // ONE javac call for all 200
    └── for each pending lambda:
        └── MVELCompiler.resolveEvaluator(sharedClassManager, fqn)  // instantiate from compiled class
```

## Key Findings

### 1. The 2-step build dramatically helps executable-model but barely helps DRLX

Executable-model runtime loading drops from **1266ms → 133ms** (9.5x improvement). It loads a pre-built kjar via standard ClassLoader — cheap and fast.

DRLX runtime loading drops from **5994ms → 2438ms** (2.5x improvement). The pre-build eliminates the javac compilation step, but replaces it with expensive per-class hidden class loading. The runtime is still dominated by 200 individual `defineHiddenClass()` calls.

### 2. The NoPersist vs PreBuild gap (~1600ms) is KieBase assembly, not disk I/O

- **NoPersist** = compile 200 lambdas + create KieBase
- **PreBuild** = compile 200 lambdas + write to disk (no KieBase)

The ~1600ms difference is the cost of assembling the KieBase (RuleImpl objects, Pattern objects, GroupElement trees, KnowledgePackageImpl, kBase.addPackages()). Disk I/O for writing 200 small .class files is negligible (<50ms).

### 3. DRLX UsingPreBuild (2438ms) — Why so slow?

The `loadPreCompiledEvaluator()` method (`DrlxToRuleImplVisitor.java:205-211`) does this **200 times**:

```java
private Object loadPreCompiledEvaluator(String fqn, String classFilePath) throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of(classFilePath));     // read .class file
    ClassManager classManager = new ClassManager();                 // NEW per lambda!
    classManager.define(Collections.singletonMap(fqn, bytes));     // defineHiddenClass + bytecode extraction
    Class<?> clazz = classManager.getClass(fqn);
    return clazz.getConstructor().newInstance();
}
```

Inside `ClassManager.define()`, for each class:
1. `ClassEntry` constructor calls `MethodByteCodeExtractor.extract("eval", bytes)` — ASM parses bytecode
2. `Murmur3F` hash is computed for deduplication
3. `lookup.defineHiddenClass(bytes, true)` — JVM hidden class definition (verify + link)

**Estimated time breakdown for UsingPreBuild (~2438ms):**

| Component | Per-lambda | x200 | % of total |
|-----------|-----------|------|-----------|
| File I/O (`readAllBytes`) | ~0.3ms | ~60ms | ~2% |
| `MethodByteCodeExtractor.extract()` + Murmur hash | ~2-5ms | ~400-1000ms | ~29% |
| **`defineHiddenClass()` (verify + link)** | **~5-10ms** | **~1000-2000ms** | **~62%** |
| ANTLR re-parse + KieBase assembly | — | ~200ms | ~7% |

The DRLX source is also **re-parsed** every time because the visitor must walk the parse tree to match lambdas with metadata entries.

### 4. Why executable-model UsingPreBuild is only 133ms

The executable-model path loads a single kjar using standard `URLClassLoader`/`KieModuleClassLoader`. Classes are loaded lazily via the JVM's optimized classpath class loading. No `defineHiddenClass()`, no `MethodByteCodeExtractor`, no per-class overhead.

## Root Cause: Per-Lambda Java Compilation (NoPersist/PreBuild)

Each of 100 rules generates **2 MVEL compilations** (constraint + consequence) = **200 separate compilation cycles**:

```
MVEL expression → Transpile to Java source → Java compile (javac/ECJ) → defineHiddenClass
```

### Time Breakdown (NoPersist, ~5994ms total)

| Step | Per-lambda | x200 lambdas | % of total |
|------|-----------|-------------|-----------|
| MVEL → Java transpilation | ~2-5ms | ~400-1000ms | ~12% |
| **Java source → bytecode compile** | **~15-25ms** | **~3000-5000ms** | **~67%** |
| defineHiddenClass + bytecode extraction | ~3-5ms | ~600-1000ms | ~13% |
| KieBase assembly | — | ~500ms | ~8% |

The **Java compilation** (`KieMemoryCompiler.compile()`) is the dominant cost. Each lambda spins up the compiler for a single small class.

### MVEL compilation pipeline

```
MVELCompiler.compile(CompilerParameters)
├── transpile(info)                         // MVEL → Java source
│   ├── MVELTranspiler.transpile()
│   └── CompilationUnitGenerator.createCompilationUnit()
│
└── compileEvaluator(unit, info)
    └── compileEvaluatorClass(classManager, sources, javaFQN)
        └── KieMemoryCompiler.compile()     // ← PRIMARY BOTTLENECK
            └── JavaCompiler.compile()      // javac or Eclipse compiler per lambda
```

## Consistency Check: Does PreBuild + UsingPreBuild = NoPersist?

No: 4385 + 2438 = **6823ms** > 5994ms (NoPersist). The ~828ms surplus exists because:

1. **DRLX source is parsed twice** — once in PreBuild, once in UsingPreBuild (~100-300ms)
2. **UsingPreBuild pays different costs** — `defineHiddenClass()` + bytecode extraction replaces javac compilation, but the per-class hidden class overhead is additive, not complementary to PreBuild

The 2-step model is not a clean decomposition — UsingPreBuild has its own substantial overhead that partially duplicates work.

## Optimization Paths (in order of impact)

### 1. Batch `defineHiddenClass()` or use standard ClassLoader (HIGH — fixes UsingPreBuild)

**Current**: 200 individual `new ClassManager()` + `defineHiddenClass()` calls.
**Fix**: Load all 200 .class files into a single `ClassManager.define()` call with a 200-entry map, or better yet, bundle pre-built classes into a JAR and load via `URLClassLoader` — matching what executable-model does. Expected to bring UsingPreBuild from ~2397ms to ~200-400ms.

### 2. Skip `MethodByteCodeExtractor` on the load path (HIGH — fixes UsingPreBuild)

During loading of pre-compiled classes, `ClassEntry` still calls `MethodByteCodeExtractor.extract("eval", bytes)` for deduplication. When loading pre-built artifacts, deduplication is unnecessary — the pre-build already ensured correctness. Add a fast-path that skips bytecode extraction and hashing.

### 3. ~~Batch Compilation in MVEL3 (HIGH — fixes NoPersist)~~ ✅ DONE

~~Collect all 200 generated Java sources and compile in a **single** `KieMemoryCompiler.compile()` call. Compiler startup/initialization overhead is currently paid 200 times.~~

**Result**: NoPersist DRLX dropped from **5994ms → 2036ms (2.95x faster)**, ratio vs exec-model narrowed from **4.74x → 1.63x**. Currently only applied to the no-persist path. Extending to the persistence path (PreBuild) requires a two-phase flow that integrates with `LambdaRegistry` dedup/persistence.

### 4. Batch Compilation for Persistence Path (HIGH — fixes PreBuild)

Apply the same batch compilation to the persistence-enabled path. Requires a two-phase flow: transpile all sources → batch javac → register/persist each output with `LambdaRegistry`. The current `compileEvaluatorClassWithPersistence()` interleaves compilation with `LambdaRegistry` registration, making it non-trivial to batch. Expected to bring PreBuild from ~4359ms to ~2000-2500ms.

### 5. Cache ANTLR parse result (MEDIUM — fixes UsingPreBuild)

In the 2-step flow, DRLX source is parsed twice (PreBuild + UsingPreBuild). Serialize the parse tree or metadata to avoid re-parsing. Saves ~100-300ms on the runtime path.

### 6. Expression-Based Caching (LOW for this benchmark)

Cache compiled evaluators by expression hash. With 100 unique rules this benchmark has zero duplication, but real-world rule sets with repeated patterns would benefit.

## Benchmark Methodology Notes

- **5 forks**: Adequate for directional analysis. JMH uses 99.9% CI with t-distribution (df=4, t=8.61), which inflates error bars. 10 forks would halve confidence intervals for publication-quality results.
- **Relative error margins**: DRLX 2-9%, exec-model 8-18%. The smaller DRLX variance is expected given its longer runtime.
- **`@Setup(Level.Invocation)` in PreBuild benchmark**: Setup/teardown time is included in ss measurements. The overhead (temp dir creation, LambdaRegistry reset) is <5ms — negligible for 1000-6000ms measurements.
- **Setup warms some classes**: `@Setup(Level.Trial)` in UsingPreBuild loads ClassManager, ANTLR, etc. Both sides benefit roughly equally, making the numbers slightly optimistic vs truly cold production deployment.
- **GC**: 4GB heap (`-Xms4g -Xmx4g`) means minimal GC pressure. No specific GC algorithm pinned — minor reproducibility concern.

## Files Involved

| File | Role |
|------|------|
| `src/main/java/org/drools/drlx/builder/DrlxLambdaConstraint.java` | Compiles each constraint lambda via MVEL |
| `src/main/java/org/drools/drlx/builder/DrlxLambdaConsequence.java` | Compiles each consequence lambda via MVEL |
| `src/main/java/org/drools/drlx/builder/DrlxToRuleImplVisitor.java` | ANTLR visitor; `loadPreCompiledEvaluator()` loads pre-built classes |
| `src/main/java/org/drools/drlx/builder/DrlxRuleBuilder.java` | Core builder orchestrating parse + visit |
| `src/main/java/org/drools/drlx/tools/DrlxCompiler.java` | Public facade; routes to pre-build or compile path |
| (mvel) `org/mvel3/MVELCompiler.java` | Transpiles + compiles each lambda |
| (mvel) `org/mvel3/javacompiler/KieMemoryCompiler.java` | Java bytecode compilation — primary bottleneck |
| (mvel) `org/mvel3/ClassManager.java` | `defineHiddenClass()` + `MethodByteCodeExtractor` — secondary bottleneck |
