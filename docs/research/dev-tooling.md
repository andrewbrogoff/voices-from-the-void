# Research: dev tooling for JDK 25, Gradle 9.2.1 and ModDevGradle 2.0.147

Resolves [#7](https://github.com/andrewbrogoff/voices-from-the-void/issues/7). Researched on 2026-10-14 against NeoForge 26.2.0.88, Minecraft 26.2, MDG 2.0.147, Gradle 9.2.1 and OpenJDK 25.0.4.

## Recommended stack

| Concern | Tool | Pin |
| --- | --- | --- |
| Formatting | Spotless + **google-java-format** (AOSP style) | Spotless `8.10.4`, GJF `1.36.1` (not 1.37.0, see §1) |
| Compile-time bug checks | **Error Prone** via `net.ltgt.errorprone`, `-Xlint:all -Werror` | plugin `5.1.1`, core `2.50.0` |
| Null safety | **NullAway** in JSpecify mode, `@NullMarked` per package | NullAway `0.14.2`, JSpecify `1.0.0` (Minecraft's own) |
| Architecture and reflection ban | **ArchUnit**, in its own `archTest` suite | `1.5.1` |
| Unit tests | JUnit Jupiter with MDG `unitTest` (FML-bootstrapped) | JUnit BOM `6.1.3` |
| In-game tests | Vanilla/NeoForge **GameTests** via MDG `runGameTestServer` | n/a |
| E2E | `mc-agent-harness` `runHarnessScenario` under `xvfb-run` | harness `0.1.0` |
| Coverage | **JaCoCo** on `test` and `runGameTestServer`, merged report | `0.8.15` |
| Micro-benchmarks | **JMH** (`me.champeau.jmh`) for pure-Java core only | plugin `0.7.3`, JMH `1.37` |
| Allocation and tick budgets | `com.sun.management.ThreadMXBean` asserts in tests; `MinecraftServer#getTickTimesNanos` in GameTests; JFR | JDK |
| Live profiling (manual) | spark | `1.10.187-neoforge` |
| CI | GitHub Actions: `setup-java@v6` with Temurin 21 and 25, `gradle/actions/setup-gradle@v6` | n/a |

**Dropped as redundant:**
- **Checkstyle:** its only unique job here (an import ban) is done better by ArchUnit.
- **PMD:** overlaps Error Prone and adds Minecraft-specific false positives.
- **SpotBugs:** its signal on mod code is noise: `EI_EXPOSE_REP` on every `Level` holder, and false positives on Java 22 `_` patterns. Its null analysis disagrees with NullAway.

The reasoning is in §7.

## How this was verified

I built a scratch NeoForge mod in a worktree-local `.scratch/` directory (not committed) with the same Gradle wrapper, NeoForge version, MDG version and `gradle.properties` flags as the harness, including `org.gradle.configuration-cache=true`.

**The scratch mod contained:**
- An `api/core/neoforge` package split, each package `@NullMarked`.
- A data-driven GameTest: `DeferredRegister<Consumer<GameTestHelper>>` on `Registries.TEST_FUNCTION`, plus `test_instance`, `test_environment` and structure files.
- A JMH benchmark.
- ArchUnit rules.
- An FML-bootstrapped JUnit test.

Every tool in this document was applied at once. Deliberate probe classes, deleted afterwards, checked that each rule actually fires.

**What ran green on JDK 25 / Gradle 9.2.1 / MDG 2.0.147:**
- `spotlessCheck`, `compileJava` (Error Prone + NullAway + `-Xlint:all -Werror`), `checkstyleMain`, `pmdMain`, `spotbugsMain`, `test` (FML unit tests), `archTest`, `jmh`, `runGameTestServer` with the JaCoCo agent, and `jacocoGameTestReport`.
- A second `check` reported "Configuration cache entry reused".
- The harness's own build reports show `check` storing the configuration cache with 0 problems on Spotless 8.10.1, Error Prone 2.50.0, Checkstyle 14.1.0 and palantir 2.97.0.

**What I could not run:** the xvfb client path. The sandbox blocked `xvfb-run` for this agent. That path is covered by the harness README, by the harness's own prior runs, and by NeoForge's CI (§11).

## Compatibility matrix

"Verified" means I ran it in the scratch build. "Source" means it comes from a primary source but I did not run it.

| Tool | Latest (date) | Java 25 | Gradle 9 + config cache | Status |
| --- | --- | --- | --- | --- |
| Spotless Gradle plugin | 8.10.4 (2026-10-08) | yes | yes | Verified |
| palantir-java-format | 2.102.0 (2026-10-05) | **Fails on `_` unnamed patterns and `import module`** | n/a | Verified fail |
| google-java-format | 1.37.0 (2026-10-01) | 1.36.1 parses `_`; **1.37.0 is broken under Spotless** | n/a | Verified |
| Error Prone | 2.50.0 (2026-06-10) | yes (CI matrix JDK 21/25) | plugin 5.1.1 yes | Verified |
| NullAway | 0.14.2 (2026-09-25) | yes | n/a | Verified |
| Checkstyle | 14.3.0 (2026-09-27) | yes (Gradle default 10.24.0 is too old) | yes | Verified |
| PMD | 7.28.0 (2026-09-25) | since 7.16.0 (Gradle default 7.13.0 is too old) | yes | Verified |
| SpotBugs / plugin | 4.10.5 / 6.5.13 (2026-10-12) | yes (ASM 9.11) | yes (deprecation warning only) | Verified |
| ArchUnit | 1.5.1 (2026-09-25) | since 1.4.1 | n/a | Verified |
| JaCoCo | 0.8.15 (2026-06-05) | officially since 0.8.14 (Gradle default 0.8.13 is experimental) | yes | Verified |
| JMH plugin / JMH | 0.7.3 (2025-04-16) / 1.37 (2023-08) | yes (runs) | works; upstream Gradle 9 CI not yet released | Verified |
| JUnit | 6.1.3 (2026-08-07) | yes | yes | Verified |
| spark | 1.10.187-neoforge (2026-09-19), MC 26.1–26.3 | n/a | n/a | Source |

## 1. Formatting: Spotless with google-java-format

- **Spotless 8.10.4 works on Gradle 9.2.1 with the configuration cache.** You don't need manual `--add-exports`: Spotless has opened javac internals itself since 6.7.1 ([spotless#834](https://github.com/diffplug/spotless/issues/834)). There is an open intermittent `NoClassDefFoundError` with the configuration cache in multi-module `clean build` ([spotless#2850](https://github.com/diffplug/spotless/issues/2850)); watch for it.
- **palantir-java-format 2.102.0 cannot parse Java 22+ unnamed variables.** Verified: `case AspectAmount(String aspect, _)` fails with `expected token: '_'`. This is open as [palantir#1614](https://github.com/palantir/palantir-java-format/issues/1614), and the fix PR #1579 is unmerged. `import module` is also open ([palantir#1506](https://github.com/palantir/palantir-java-format/issues/1506)), as are compact source files ([palantir#1701](https://github.com/palantir/palantir-java-format/issues/1701)). A Java 25 codebase should not give up `_` because of its formatter.
- **google-java-format 1.36.1 parses `_` (verified).** Per the GJF releases, 1.30.0 added `import module` and 1.34.0 added instance main methods ([releases](https://github.com/google/google-java-format/releases)). GJF needs JDK 21+ to run since 1.29.0.
- **Do not use GJF 1.37.0 yet. Verified:** every file fails with an opaque `google-java-format(java.lang.reflect.InvocationTargetException)`. The cause is that GJF made `JavaFormatterOptions.Style` a record, which breaks how Spotless calls it ([spotless#3126](https://github.com/diffplug/spotless/issues/3126)). It is fixed upstream in GJF commit `a51e46a`, which is not yet released. Pin 1.36.1 and bump once a GJF release contains the fix.
- **Style:** AOSP (`.aosp()`) gives 4-space indent at 100 columns. Palantir's 120 columns is nicer for long Minecraft generics, but parsing correctness wins.
- **Minecraft pitfall:** Spotless only formats `src/*/java`. Keep datagen output (`src/generated/resources`) out of its `target`.

## 2. Error Prone

- **2.50.0 runs on JDK 21+** (2.42.0 was the last release for JDK 17) and is tested on JDK 25 in CI ([installation](https://errorprone.info/docs/installation), [ci.yml](https://github.com/google/error-prone/blob/master/.github/workflows/ci.yml)).
- **The `net.ltgt.errorprone` 5.1.1 plugin adds all the flags.** That covers `--add-exports`/`--add-opens`, `-XDcompilePolicy=simple`, `--should-stop=ifError=FLOW` and `-XDaddTypeAnnotationsToSymbol=true`. It forks javac only when the daemon lacks them ([plugin README](https://github.com/tbroyer/gradle-errorprone-plugin#jdk-16-support), [source](https://github.com/tbroyer/gradle-errorprone-plugin/blob/main/src/main/java/net/ltgt/gradle/errorprone/ErrorPronePlugin.java)). Verified on Gradle 9.2.1 with the configuration cache.
- **Use `-Xlint:all -Werror`.** Verified: Minecraft and NeoForge on the classpath produce no lint noise. Error Prone warnings then fail the build too; a probe tripped `ObjectToString` and failed `compileJava`.
- **Generated code:** set `disableWarningsInGeneratedCode = true` and `excludedPaths = '.*/build/generated/.*'` ([flags](https://errorprone.info/docs/flags)). With these, JMH's generated sources compiled cleanly.
- **Error Prone has no built-in reflection ban.** `RestrictedApi` and `DoNotCall` only cover APIs you annotate yourself. Use ArchUnit (§8).
- **Mixin pitfall (for the rare mixin):** `UnusedMethod` flags private `@Inject`/`@Redirect` handlers. Exempt them with `-XepOpt:UnusedMethod:ExemptingMethodAnnotations=<fqcns>` ([WellKnownKeep](https://github.com/google/error-prone/blob/master/core/src/main/java/com/google/errorprone/bugpatterns/WellKnownKeep.java)).

## 3. NullAway and JSpecify: Minecraft is already annotated

- **Minecraft 26.2 is JSpecify-annotated. Verified** in MDG's `minecraft-patched-26.2.0.88-sources.jar`:
  - 542 `package-info.java` files carry `@NullMarked` (510 under `net.minecraft`).
  - 1,774 files import `org.jspecify.annotations.Nullable`.
  - The compiled `minecraft-patched-26.2.0.88.jar` keeps all 542 `package-info.class` files.
  - Mojang ships `org.jspecify:jspecify:1.0.0` as a runtime library ([26.2 version JSON](https://piston-meta.mojang.com/v1/packages/91ef51b7d36ca092c5c02aba42f0f40c7cc1db84/26.2.json)).
  - NeoForge's own packages are `@NullMarked` too ([example](https://github.com/neoforged/NeoForge/blob/26.2.x/src/main/java/net/neoforged/neoforge/common/package-info.java)).
- **NullAway 0.14.2 enforces Mojang's annotations. Verified:** `level.getBlockEntity(pos).isRemoved()` fails compilation with `[NullAway] dereferenced expression 'level.getBlockEntity(BlockPos.ZERO)' is @Nullable`. This makes NullAway much more valuable here than in a typical mod, because vanilla nullability is checked rather than assumed optimistically.
- **Config:** `OnlyNullMarked=true` plus `JSpecifyMode=true`. JSpecify mode needs JDK 22+ javac or `-XDaddTypeAnnotationsToSymbol=true`; JDK 25 qualifies natively ([JSpecify wiki](https://github.com/uber/NullAway/wiki/JSpecify-Support)).
- **Caveats:**
  - JSpecify mode "may report false positive warnings" around generics (same wiki).
  - 0.14.0 removed `LegacyAnnotationLocations`, so `@Nullable` must sit in type-use position, e.g. `Outer.@Nullable Inner` ([0.14.0](https://github.com/uber/NullAway/releases/tag/v0.14.0)).
  - Expect occasional real errors where Mojang's annotations are imperfect. Handle them with a local `castToNonNull` helper and a one-line justification, not `@SuppressWarnings` on a whole class.
- **Use JSpecify 1.0.0, the version Mojang ships**, not 1.0.1. That avoids a version split; the annotations are identical.
- **Enforce `@NullMarked` everywhere:** enable `RequireExplicitNullMarking` as an error, so a package without `package-info.java` cannot slip through.

## 4. Checkstyle: dropped

- **Version:** 14.3.0 parses Java 25: `import module` since 12.2.0, flexible constructor bodies since 13.5.0 ([releases](https://github.com/checkstyle/checkstyle/releases)). Verified on a flexible-constructor-body plus `_` probe. Gradle 9.2.1's built-in default is 10.24.0, which is too old ([CheckstylePlugin@v9.2.1](https://github.com/gradle/gradle/blob/v9.2.1/platforms/jvm/code-quality/src/main/java/org/gradle/api/plugins/quality/CheckstylePlugin.java)).
- **It cannot enforce the reflection ban. Verified:** `IllegalImport` caught `import java.lang.invoke.MethodHandles` but missed a fully qualified `java.lang.reflect.Method` in the same file. It also cannot see `Class.forName(...)` or `getMethod(...)`, because `java.lang.Class` needs no import ([checkstyle#6486](https://github.com/checkstyle/checkstyle/issues/6486)).
- **Everything else it does is covered:** Spotless handles layout, `removeUnusedImports()` handles unused imports, and GJF never emits star imports. Naming conventions are the only gap, and Error Prone already covers some (`MemberName`, `TypeParameterNaming`). Drop it. If wanted later, a three-rule naming config costs about 1 s.

## 5. PMD: dropped

- **Works if you set the version yourself:** 7.28.0 runs fine under Gradle 9.2.1 (verified). Java 25 support arrived in 7.16.0 ([notes](https://github.com/pmd/pmd/releases/tag/pmd_releases%2F7.16.0)), but Gradle 9.2.1 defaults to 7.13.0 ([PmdPlugin@v9.2.1](https://github.com/gradle/gradle/blob/v9.2.1/platforms/jvm/code-quality/src/main/java/org/gradle/api/plugins/quality/PmdPlugin.java)), so `toolVersion` must be set.
- **Minecraft false positive (verified):** `InstantiableUtilityClass` fires on the `@Mod` entry class. FML instantiates it, but PMD sees only static members.
- **Noise:** it warns about `jrt-fs.jar` on the aux classpath on every run.
- **Overlap:** its error-prone and best-practice categories largely duplicate Error Prone, and Spotless covers its code-style rules. Type resolution has to index the whole Minecraft jar.
- **Verdict:** nothing unique justifies keeping it.

## 6. SpotBugs: dropped

- **It works:** 4.10.5 with plugin 6.5.13 reads class file 69 (verified). The plugin still calls `Configuration.setVisible`, which Gradle 9 deprecates (a warning only; [plugin#1773](https://github.com/spotbugs/spotbugs-gradle-plugin/issues/1773)).
- **Minecraft noise (verified):** a plain class holding a `Level` and a `BlockPos.MutableBlockPos` produced three `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` findings. Every block entity, menu and system holder would trip these, because Minecraft objects are mutable by design.
- **Java 22 false positive (verified):** `case AspectAmount(String aspect, _)` produces `DLS_DEAD_LOCAL_STORE` on the synthetic local for `_`.
- **Conflicts with NullAway:** its JSpecify support is partial ("scope-level semantics of `@NullMarked`… not yet supported", [CHANGELOG](https://github.com/spotbugs/spotbugs/blob/master/CHANGELOG.md)), so its null detectors disagree with NullAway.
- **Not needed:** find-sec-bugs adds nothing for a game mod.
- **Verdict:** drop it. Its one unique strength, concurrency patterns, matters little in code that runs on the server thread.

## 7. Redundancy analysis

None of the upstream projects documents how it overlaps with the others. This is reasoning backed by the probes above.

| Concern | Error Prone + NullAway | ArchUnit | Spotless | Checkstyle | PMD | SpotBugs |
| --- | --- | --- | --- | --- | --- | --- |
| Formatting and imports | | | **owns** | duplicate | duplicate | |
| Bug patterns | **owns** (javac-integrated, typed) | | | | mostly duplicate | partly duplicate, noisy |
| Nullness | **owns**, reads Mojang's JSpecify | | | | | conflicting |
| Reflection ban | none built in | **owns** (bytecode) | | leaky (imports only) | `AvoidAccessibilityAlteration` only | |
| Layering and loader seams | | **owns** | | | | |
| Naming | partial | possible | | yes | yes | |

Error Prone and NullAway run inside `javac`, so they cost no extra pass over the Minecraft classpath and fail at the earliest point. ArchUnit is the only tool that sees bytecode-level calls. With formatting given to Spotless, the other three add only noise or duplication.

## 8. ArchUnit: reflection ban and layering

**1.5.1 imports Java 25 bytecode** (class file 69 since 1.4.1, [release](https://github.com/TNG/ArchUnit/releases/tag/v1.4.1)).

**Pitfall 1: ArchUnit must not run in the FML-bootstrapped `test` task. Verified.** With MDG `unitTest { enable() }`, ArchUnit's engine discovery fails with this error:

> `IllegalAccessError: failed to access class net.neoforged.fml.loading.ClassLoadingGuardian from class dev.vftv.ArchitectureTest`

So put ArchUnit in its own JVM test suite, `archTest`, that depends on `project()`.

**Pitfall 2: Minecraft's strict dependency constraints clash with ArchUnit. Verified.** On the `test` classpath, Minecraft's `slf4j-api {strictly 2.0.17}` conflicts with ArchUnit's 2.0.19 and resolution fails. The separate suite never sees the Minecraft classpath, which fixes this too. This is a general pitfall: any test library on an MDG classpath must accept Minecraft's `strictly` pins.

**What the reflection ban caught (verified):**
- A plain `dependOnClassesThat().resideInAPackage("java.lang.reflect..")` rule **missed** `Class.forName("x").getMethod("y")` assigned to a local `Method`. The local variable's type is not a recorded dependency, and the call's owner is `java.lang.Class`.
- The rule below caught `Class.forName`, `Class.getMethod`, `MethodHandles.lookup()`, the method reference `String.class::getMethods` and a reference to FML's `ObfuscationReflectionHelper`.
- `accessTargetWhere` is needed for method references, and it needs `JavaAccess.Predicates.target`; `JavaCall.Predicates.target` does not compile there.
- It produced **no false positives** on lambdas, method references, string concatenation, records or pattern switches. ArchUnit only interprets `LambdaMetafactory` bootstraps and records their target, not `java.lang.invoke` itself; `StringConcatFactory`, `ObjectMethods` and `SwitchBootstraps` produce no dependency ([JavaClassProcessor](https://github.com/TNG/ArchUnit/blob/main/archunit/src/main/java/com/tngtech/archunit/core/importer/JavaClassProcessor.java)).

**What the layering rules caught (verified):** `core` calling a `neoforge` constructor tripped both `LAYERS` and `CORE_IS_LOADER_FREE`. A `core` method taking `IEventBus` tripped `CORE_IS_LOADER_FREE`.

**Known blind spot:** references to compile-time constants (`static final String MOD_ID`) are inlined by javac and invisible in bytecode. Keep shared constants in `api`.

```java
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;

@AnalyzeClasses(packages = "dev.vftv", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {
    private static final String REFLECTIVE_CLASS_METHODS =
            "forName|newInstance|get(Declared)?(Method|Field|Constructor|RecordComponent)s?|getEnclosingMethod"
                    + "|getEnclosingConstructor|getPermittedSubclasses|setAccessible";

    @ArchTest
    static final ArchRule NO_REFLECTION = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("java.lang.reflect..", "java.lang.invoke..", "sun.misc..", "jdk.internal..")
            .orShould().accessTargetWhere(target(owner(equivalentTo(Class.class)))
                    .and(target(nameMatching(REFLECTIVE_CLASS_METHODS))))   // accessTargetWhere also catches Class::forName refs
            .orShould().callMethodWhere(target(owner(resideInAnyPackage("java.lang.invoke.."))))
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName("net.neoforged.fml.util.ObfuscationReflectionHelper")
            .because("runtime reflection is banned");

    @ArchTest
    static final ArchRule LAYERS = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("dev.vftv..")
            .layer("Api").definedBy("dev.vftv.api..")
            .layer("Core").definedBy("dev.vftv.core..")
            .layer("Platform").definedBy("dev.vftv.neoforge..")
            .whereLayer("Platform").mayNotBeAccessedByAnyLayer()
            .whereLayer("Core").mayOnlyBeAccessedByLayers("Platform")
            .whereLayer("Api").mayOnlyBeAccessedByLayers("Core", "Platform");

    @ArchTest
    static final ArchRule CORE_IS_LOADER_FREE = noClasses()
            .that().resideInAnyPackage("dev.vftv.api..", "dev.vftv.core..")
            .should().dependOnClassesThat().resideInAnyPackage("net.neoforged..", "org.spongepowered..");
}
```

Notes:
- Don't ban every method on `Class`: `assert` compiles to `Class.desiredAssertionStatus()`, and `getSimpleName` is harmless.
- Banning `java.lang.invoke..` also bans `VarHandle`. Use `java.util.concurrent.atomic` instead.
- **Mixins:** mixin classes are reflection-adjacent by nature. Put them in one `dev.vftv.neoforge.mixin` package and add a rule that only that package may depend on `org.spongepowered..`, so the last-resort rule is enforced structurally.
- **Freezing:** if a rule ever has to be relaxed, prefer `FreezingArchRule.freeze(rule)` over disabling it ([Library API docs](https://github.com/TNG/ArchUnit/blob/main/docs/userguide/008_The_Library_API.adoc)).

## 9. Tests: JUnit with FML, and GameTests

- **FML unit tests (verified):** `neoForge { unitTest { enable(); testedMod = mods.vftv } }` with JUnit 6.1.3 boots FML inside `test`. A test asserting `BuiltInRegistries.ITEM.getKey(Items.DIRT)` passed ([MDG README](https://github.com/neoforged/ModDevGradle/blob/main/README.md)). For a live `MinecraftServer` parameter, add `net.neoforged:testframework` and `@ExtendWith(EphemeralTestServerProvider.class)`.
  - **Keep pure logic out of Minecraft types** so most unit tests do not need this. FML boot costs about 5 s per test JVM.
- **GameTests on 26.2 (verified):**
  - Register a `Consumer<GameTestHelper>` in `Registries.TEST_FUNCTION` with a `DeferredRegister`, plus `data/<ns>/test_instance/*.json`, `test_environment/*.json` and a structure.
  - Alternatively, register in code with `RegisterGameTestsEvent` ([source](https://github.com/neoforged/NeoForge/blob/26.2.x/src/main/java/net/neoforged/neoforge/event/RegisterGameTestsEvent.java)).
  - The harness's `HarnessGameTests.generateData` already emits these files, so reuse it rather than hand-writing JSON.
  - MDG run config: `runs { gameTestServer { type = 'gameTestServer' } }`, which creates `runGameTestServer`.
- **The run fails the build on test failure (verified).** A failing required test exits with the failed count (`System.exit(failedRequiredCount)` in `GameTestServer`), and `runGameTestServer` fails with "non-zero exit value 1". `RunGameTask` extends `JavaExec` ([source](https://github.com/neoforged/ModDevGradle/blob/main/src/main/java/net/neoforged/moddevgradle/internal/RunGameTask.java)).
  - When `CI=true` and `GITHUB_ACTIONS=true`, NeoForge's patch prints a `::error title=GameTest Failure::` annotation.
  - A green run took about 16 s end to end, and about 8 s of that was saving chunks on shutdown.
- **`neoforge.enabledGameTestNamespaces` is dead on 1.21.5+.** GameTests are enabled in dev and gametest-server runs, or with `-Dneoforge.enableGameTest=true` ([GameTestHooks 26.2.x](https://github.com/neoforged/NeoForge/blob/26.2.x/src/main/java/net/neoforged/neoforge/gametest/GameTestHooks.java)). This matches what I saw: vanilla's `minecraft:always_pass` ran alongside ours despite the property.
- **Inferred, not tested:** an exit code of exactly 256 failures wraps to 0, and a run with zero registered tests passes. CI should also grep the log for `All N required tests passed` with N ≥ expected.

## 10. Coverage: JaCoCo

- **Set JaCoCo 0.8.15 explicitly.** Java 25 is official since 0.8.14 ([release](https://github.com/jacoco/jacoco/releases/tag/v0.8.14)), but Gradle 9.2.1 defaults to 0.8.13, which supports Java 25 only experimentally ([JacocoPlugin@v9.2.1](https://github.com/gradle/gradle/blob/v9.2.1/platforms/jvm/jacoco/src/main/java/org/gradle/testing/jacoco/plugins/JacocoPlugin.java)).
- **GameTest coverage works (verified).** `jacoco.applyTo(...)` on `runGameTestServer` recorded coverage for classes loaded by FML's transforming class loader: the GameTest-only entry class came out at 96% line coverage. FML returns untransformed bytes for classes no transformer touches, so class IDs match `build/classes` ([ClassTransformer](https://github.com/neoforged/FancyModLoader/blob/main/loader/src/main/java/net/neoforged/fml/classloading/transformation/ClassTransformer.java)).
- **Gotcha (verified):** `jacoco.applyTo` requires a `Task` or `TaskCollection`, not a `TaskProvider`. `applyTo(tasks.named(...))` fails at configuration. Use `tasks.withType(JavaExec).matching { it.name == 'runGameTestServer' }`.
- **Limit instrumentation to our classes:** set `includes = ['dev.vftv.*']`, otherwise the agent instruments all of Minecraft.
- **Mixin caveat:** a class we mixin into, or one another mod transforms, reports "execution data mismatch".
- **Merged report:** `executionData(tasks.test, tasks.runGameTestServer)`. Run CI with `--continue` so a failing GameTest still produces a report. Production prior art: [IntegratedDynamics build.gradle](https://github.com/CyclopsMC/IntegratedDynamics/blob/master-26-lts/build.gradle) does the same on 26.1.
- **Harness client runs:** the client is a separate JVM launched by the harness CLI. Covering it would need the agent added to the `harnessClient` run, which is a harness change. Not worth it; GameTest plus unit coverage is the gate.

## 11. CI on GitHub Actions

- **Actions:** `actions/setup-java@v6` (6.0.1) with Temurin, and **both 21 and 25**, because NeoFormRuntime runs on a Java 21 toolchain ([NeoFormRuntimeTask](https://github.com/neoforged/ModDevGradle/blob/main/src/main/java/net/neoforged/nfrtgradle/NeoFormRuntimeTask.java)). The foojay resolver plugin in `settings.gradle` covers this locally. Use `gradle/actions/setup-gradle@v6` (6.4.0).
- **gradle/actions v6 changed caching:**
  - The default "Enhanced Caching" is a proprietary component (free for public repos).
  - `cache-provider: basic` gives the MIT `actions/cache`-based provider.
  - The configuration cache is no longer restored in CI.
  - Only the default branch writes the cache.
  - Wrapper validation is built in, so drop the separate step.

  Sources: [v6.0.0](https://github.com/gradle/actions/releases/tag/v6.0.0), [v6.1.0](https://github.com/gradle/actions/releases/tag/v6.1.0), [setup-gradle docs](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md).
- **Minecraft and NeoForm cache:** MDG keeps it in `~/.gradle/caches/neoformruntime` on purpose, so that Gradle cache actions pick it up. On this machine it is about 2.1 GiB.
- **Headless GameTests:** `./gradlew --continue check runGameTestServer jacocoReport`.
- **xvfb client:** NeoForge's own 26.2 CI does `sudo apt-get install xvfb libgl1-mesa-dri` then `xvfb-run ./gradlew ...` ([test-prs.yml](https://github.com/neoforged/NeoForge/blob/26.2.x/.github/workflows/test-prs.yml)). Ubuntu 24.04 runners already ship Temurin 21/25 and xvfb ([runner image](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)).
  - The harness already wraps its client launch in `xvfb-run` and requires `LIBGL_ALWAYS_SOFTWARE=1` (Mesa llvmpipe). The CI job only needs `xdotool` added for `hold_key`.
  - Upload `harness-results/` (screenshots, `protocol.jsonl`) as an artifact on failure.
- **Renderer:** 26.2 ships both OpenGL and Vulkan backends in `com.mojang.blaze3d` and falls back between them ([Minecraft.java.patch](https://github.com/neoforged/NeoForge/blob/26.2.x/patches/net/minecraft/client/Minecraft.java.patch)). NeoForge CI proves the GL path works under xvfb with llvmpipe. Minimum GL/Vulkan versions are not confirmed from Mojang sources.
- **Job layout:**
  1. `build`: `check`, `archTest`, `runGameTestServer`, coverage.
  2. `e2e`: needs `build`; runs `runHarnessScenario` for each scenario under xvfb.
  3. Optional `bench`: `jmh` on schedule only, never on PRs, because shared runners are too noisy for gating.

## 12. Profiling and benchmarks

- **JMH 1.37 with `me.champeau.jmh` 0.7.3 runs on Gradle 9.2.1 with the configuration cache (verified).** `-prof gc` reported `gc.alloc.rate.norm`, e.g. 216 B/op for a stream sum, which is exactly the allocation number we want to track. Upstream Gradle 9 CI for the plugin ([PR #288](https://github.com/melix/jmh-gradle-plugin/pull/288)) is merged but unreleased.
- **Verified pitfalls:**
  - `includeTests` defaults to true. That drags the test classpath in, causes the same `slf4j strictly` clash, and breaks configuration-cache serialization. Set `includeTests = false`.
  - `jmhJar` fails with "more than 65535 entries" because it fat-jars Minecraft. Set `zip64 = true`, or better, benchmark only Minecraft-free `core` code: there's no FML bootstrap or registries in JMH anyway ([MDG README](https://github.com/neoforged/ModDevGradle/blob/main/README.md)).
- **Allocation budgets in tests (verified):** `((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(tid)` before and after a call works inside FML unit tests. This is not reflection and passes the ArchUnit ban. Use it for "zero allocation per tick" assertions on hot paths, after a warm-up loop, with headroom.
- **Tick-time budgets in GameTests:**
  - `MinecraftServer#getTickTimesNanos()` and `getAverageTickTimeNanos()` are public in 26.2 (verified in source). NeoForge adds per-dimension `getTickTime(ResourceKey<Level>)` ([TPSCommand](https://github.com/neoforged/NeoForge/blob/26.2.x/src/main/java/net/neoforged/neoforge/server/command/TPSCommand.java)).
  - Whole-tick numbers include other tests, so a budget test should time its own system's call with `System.nanoTime()` on the server thread inside the test function, use loose thresholds on CI, and run in its own environment batch.
  - Prior art: IntegratedDynamics gates perf GameTests behind an env flag and feeds `github-action-benchmark` ([performance.yml](https://github.com/CyclopsMC/IntegratedDynamics/blob/master-26-lts/.github/workflows/performance.yml)).
- **JFR:** vanilla emits the `minecraft.ServerTickTime` and `minecraft.ClientFps` JFR events. The `/jfr`, `/perf`, `/tick` and `/debug` commands exist in 26.2 (`net.minecraft.server.commands`, verified in source). `jdk.jfr.consumer.RecordingStream` can consume these in a test, but sampling makes it a trend tool, not an exact assert.
- **spark:** `1.10.187-neoforge` supports MC 26.1–26.3 ([Modrinth API](https://api.modrinth.com/v2/project/spark/version?loaders=%5B%22neoforge%22%5D)). Use it for manual profiling in dev runs as a `runtimeOnly` dev dependency, not a CI gate. The Maven coordinates via Modrinth's maven are unverified.
- **async-profiler:** 4.5 for deep CPU and allocation flame graphs, attached ad hoc.

## Baseline config (verified)

This exact file ran green in the scratch build with `spotlessApply check runGameTestServer jacocoAllReport` and reused the configuration cache:
- 2 FML unit tests passed, including an allocation-budget assert.
- 3 ArchUnit rules passed.
- 2 GameTests passed.
- The merged XML and HTML coverage report was produced.

`settings.gradle` keeps the harness's `foojay-resolver-convention 1.0.0`. `gradle.properties` copies the harness: parallel, build cache, configuration cache.

```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.147'
    id 'com.diffplug.spotless' version '8.10.4'
    id 'net.ltgt.errorprone' version '5.1.1'
    id 'jacoco'
    id 'me.champeau.jmh' version '0.7.3'
}

repositories { mavenCentral() }

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }

neoForge {
    version = providers.gradleProperty('neo_version').get()
    mods { vftv { sourceSet sourceSets.main } }
    unitTest { enable(); testedMod = mods.vftv }
    runs { gameTestServer { type = 'gameTestServer' } }
}

dependencies {
    errorprone 'com.google.errorprone:error_prone_core:2.50.0'
    errorprone 'com.uber.nullaway:nullaway:0.14.2'
    compileOnly 'org.jspecify:jspecify:1.0.0'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.compilerArgs += ['-Xlint:all', '-Werror']
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = '.*/build/generated/.*'
        error('NullAway')
        if (name == 'compileJava') {
            error('RequireExplicitNullMarking') // main only; test/jmh packages need no package-info
        }
        option('NullAway:OnlyNullMarked', 'true')
        option('NullAway:JSpecifyMode', 'true')
    }
}

spotless {
    java {
        googleJavaFormat('1.36.1').aosp() // 1.37.0 breaks Spotless 8.10.4 (spotless#3126)
        formatAnnotations()
    }
}

testing.suites {
    // Gradle 9 fails a Test task that discovers no tests, so the default suite must opt into JUnit Platform.
    test { useJUnitJupiter('6.1.3') }
    // ArchUnit outside the FML-bootstrapped `test` task (ClassLoadingGuardian + slf4j `strictly` clash).
    archTest(JvmTestSuite) {
        useJUnitJupiter('6.1.3')
        dependencies {
            implementation project()
            implementation 'com.tngtech.archunit:archunit-junit5:1.5.1'
        }
    }
}
tasks.named('check') { dependsOn testing.suites.archTest }

jacoco { toolVersion = '0.8.15' }
jacoco.applyTo(tasks.withType(JavaExec).matching { it.name == 'runGameTestServer' })
tasks.named('runGameTestServer') { jacoco { includes = ['dev.vftv.*'] } }
tasks.named('test') { jacoco { includes = ['dev.vftv.*'] } }
tasks.register('jacocoAllReport', JacocoReport) {
    // Not executionData(tasks.named('test')): a TaskProvider resolves to all task outputs, including test binaries.
    executionData fileTree(layout.buildDirectory.dir('jacoco')).include('test.exec', 'runGameTestServer.exec')
    mustRunAfter 'test', 'runGameTestServer'
    sourceSets sourceSets.main
    reports { xml.required = true; html.required = true }
}

jmh {
    jmhVersion = '1.37'
    includeTests = false
    profilers = ['gc']
}
tasks.named('jmhJar') { zip64 = true }
```

**Gotchas found while verifying this config:**
- **`RequireExplicitNullMarking`:** on every compile task it also demands `package-info.java` in test and JMH packages. The config above scopes it to `compileJava`.
- **`executionData(tasks.named('test'))`:** fails with "Unable to read execution data file …/test-results/test/binary". The config above uses the file tree instead.
- **The default `test` suite:** without `useJUnitJupiter`, Gradle 9 fails `test` with "did not discover any tests".

## Open questions for the map

1. **Formatter differs from the harness.** The harness uses palantir, and its `.editorconfig` is synced from the consumer by `tools/sync-editorconfig.sh`. If this mod adopts GJF AOSP, either the harness follows (it has no `_` usages yet) or the two drift. The alternative is to stay on palantir and ban `_` until palantir#1614 lands, which this research advises against.
2. **Is `ServiceLoader` "reflection"?** The usual multiloader seam loads the platform implementation through `ServiceLoader`. The architecture ticket should decide between allowing it as the single exception (an ArchUnit rule allowing it only in `api`'s service lookup) and wiring platform services explicitly from the `@Mod` constructor. The latter is simpler and needs no exception.
3. **Performance budgets need numbers.** The tooling (JMH `gc.alloc.rate.norm`, `ThreadMXBean` asserts, GameTest tick timing) is ready, but the per-system budgets stay under "Not yet specified" on the map.
4. **The CI cache provider for a private repo:** gradle/actions v6 Enhanced Caching (proprietary, free preview for private repos) or `cache-provider: basic`.
5. **Minimum GL/Vulkan version for the 26.2 client** is unconfirmed from Mojang sources. It only matters if the harness ever hits a renderer failure on CI's llvmpipe.
