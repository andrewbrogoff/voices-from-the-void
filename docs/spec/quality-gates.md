# Quality gates and definition of done

What every build ticket must pass before it merges. It covers tooling, test layers, CI, performance budgets and enforcement. It builds on the [architecture spec](architecture.md) (§ Enforcement) and the verified stack in the [dev-tooling research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/dev-tooling/docs/research/dev-tooling.md), whose baseline Gradle config is the starting point.

Resolved in [Quality gates and definition of done](https://github.com/andrewbrogoff/voices-from-the-void/issues/12).

## Static gates

These all run inside `./gradlew check` and fail the build.

| Gate | Configuration |
|---|---|
| **Formatting** | Spotless with google-java-format `1.36.1`, AOSP style (4-space indent, 100 columns). Pinned. A bump goes through its own PR that proves formatting and configuration-cache compatibility; "the fix shipped upstream" is not evidence. Datagen output is outside Spotless's target. |
| **Compiler** | `-Xlint:all -Werror`, so every warning is an error. |
| **Error Prone** | `2.50.0` with the warnings it finds as errors. Generated code is excluded (`disableWarningsInGeneratedCode`, `excludedPaths`). |
| **NullAway** | JSpecify mode, `OnlyNullMarked`. `RequireExplicitNullMarking` is an error on **both** the `main` and `api` source sets, so every production package needs a `package-info.java` with `@NullMarked`. Where Mojang's annotations are wrong, use a local `castToNonNull` with a one-line reason. |
| **ArchUnit** | The rules in [architecture § Enforcement](architecture.md#enforcement), run in the separate `archTest` suite. The freeze store starts **empty**. CI never creates or refreezes a baseline, and any change to the committed store is reviewed like code. |
| **Access transformers** | Every active line (not blank, not a comment) in `accesstransformer.cfg` carries a trailing `# why` comment. |
| **Interface injection** | No interface-injection data files exist. |
| **Datagen** | CI regenerates into a clean directory and fails on any added, changed **or deleted** file compared with `src/generated/resources`. |
| **Hand-written resources** | Every hand-written JSON file in `src/main/resources` is parsed and validated by a test. |

**Suppressions** are allowed only on the narrowest declaration Java supports (a local variable, method or field, never a class), must name the specific diagnostic, and must carry a one-line reason. A suppression that needs a paragraph means the code is wrong.

**Not used:** Checkstyle, PMD, SpotBugs (redundant or noisy; see the research, §§4–7), git hooks (CI is the gate), and pixel-golden screenshot diffs (too flaky on software rendering).

## Test layers

The map requires that every **system** gets all three layers. A single ticket only adds the tests its change calls for.

### Unit (`test` suite, JUnit 6 with FML)

- **The `model` tier:** behaviours, boundaries and invariants of each algorithm or rule. There's no "one test class per class" rule.
- **Codecs:** every persisted record and every network payload. A round trip (encode → decode → equal) is necessary but not enough. Also test defaults for missing fields, rejected invalid values, and every historical format we still promise to read. `StreamCodec`s get their own tests.
- **Allocation budgets:** for hot paths a spec declares (see "Performance").

### GameTest (`runGameTestServer`, dedicated server)

- Every block and item interaction that has effects in the world.
- Every recipe type crafts in-world.
- Every datapack registry and data map loads and validates, and broken entries are rejected with a clear error.
- **Persistence:** every attachment, block entity, `SavedData` and persistent data component survives a real save and reload. That means the lifecycle, including dirty marking, not just codec equality.
- **Hostile input:** every client-to-server payload gets a test that sends it bad input (wrong menu, out of range, not the owner, malformed values).
- **Discovery guard:** CI checks that the **expected set of `vftv` test ids** ran and passed. It reads structured results where available, and otherwise parses the ids from a fresh log. A count isn't enough, because vanilla's own tests run alongside ours.

### E2E (`mc-agent-harness`)

- At least one scenario per player-facing flow in each system: screens, HUD and rendering, client sync.
- **Each scenario asserts state**, not only that steps succeeded. A small per-scenario checker reads `protocol.jsonl` and checks the `dump` values the scenario cares about.
- **Sync coverage:** initial sync on join, later updates, logout and reconnect, and `/reload` where it matters.
- **Asset checks:** a resource-reference validator (every blockstate, item definition, model, texture, sound and lang key referenced resolves to a real file), plus a client resource-load check with no missing-texture or missing-sound errors from `vftv`. "Has a model" is checked against each block's declared render strategy, which allows for inherited models, invisible blocks and custom renderers.
- **Language:** every registered item, block and entity has an `en_us` name. Dynamic UI keys exist, and placeholder counts match across lang entries.
- **Screenshots** are kept and reviewed by an agent; they are never pixel-diffed.

### Harness work the build phase needs first

The assertions above can't create abilities the harness doesn't have. Before the first system is built, the harness must gain:
- a per-scenario assertion checker over `protocol.jsonl`;
- a persistent world that survives across launches, plus logout and reconnect within a run (today, every launch gets a fresh game directory);
- a client resource-load inspection op;
- hardware rendering, a backend choice (OpenGL or Vulkan) and a resolution setting. Today it hard-codes 1280×720 Xvfb and forces `LIBGL_ALWAYS_SOFTWARE=1`. Every run records which GPU and backend actually initialised.

## Coverage

JaCoCo `0.8.15` merges the unit and GameTest execution data, filtered to `com.andrewbrogoff.vftv.*`.

- **Gate:** aggregated over production `..model..` packages, at least **90% line and 80% branch** coverage. Classes that never ran count in the denominator.
- Everything else is reported, not gated.
- Verification is wired into `check` explicitly (Gradle doesn't do this by itself) and needs fresh data from both suites.

## Shipping artifact

- A test checks the distributable jar: it contains the API classes and every resource, and no test, harness or dev-only dependency.
- **The packaged jar** (not the dev classpath) boots on a dedicated server with no soft dependency installed.
- Integration tests for Curios, JEI and TerraBlender run when code in their `neoforge.compat.<mod>` package changes.
- **API** ([API spec](api.md)):
  - Javadoc for the `api` source set runs with `-Xdoclint:all -Werror` over the API, Minecraft and NeoForge classpath.
  - The `api` jar holds only `META-INF/MANIFEST.MF`, directory entries, `LICENSE-API` and the API classes, and the `api-sources` jar the same with sources. Both manifests carry `Vftv-Version` and `Vftv-NeoForge-Version`.
  - The example addon `testAddon` compiles against the built `api` jar file.
  - The frozen compatibility corpus `testCompat` compiles against it. It references only Stable declarations (`API_CORPUS_STABLE`), and every method carries its deprecation suppression from the day it is added. Its data replays in its own GameTest run, at startup and after `/reload`.
  - **The baseline** is the release with the highest semantic version below `mod_version` of the same MAJOR, excluding drafts and prereleases, identified by tag and asset and checked against its `SHA256SUMS`. If one exists, `apiCompat` (japicmp, failing on binary and source incompatibility, with the Experimental and Internal tiers excluded), `API_NO_DEMOTION`, `API_RECORDS_FROZEN`, the corpus freeze check and the binary replay of the baseline's corpus jar all run; if a download fails, the step fails. If none exists (before `1.0.0`, or in a new MAJOR), those comparisons are skipped and logged, and every other API check still runs.
  - `apiGateSelfTest` proves the gate's own verdicts on committed old/new pairs.
  - These gates check compatibility; they don't prove it. Data schemas are also reviewed at release ([API spec, Public data formats](api.md#public-data-formats)).

## Logging

No unexpected `ERROR` from the `vftv` logger in any test run, nor any failure to load a resource. Errors a test causes on purpose (rejected payloads, broken data) are expected and bounded by that test. Warnings aren't banned wholesale.

## CI

GitHub Actions on hosted `ubuntu-24.04`.

- **Toolchains:** Temurin 21 and 25 are both installed. 21 is for NeoForge's build tooling, and 25 builds the mod. This is not a test matrix.
- **Caching:** `gradle/actions/setup-gradle` with `cache-provider: basic` (MIT; no proprietary component).
- **Pinning:** every dependency and plugin version, and every action by full commit SHA. Upgrades come in reviewed PRs that pass every gate. No automatic upgrades of the formatter or the platform.
- **The harness** is checked out with `actions/checkout` (its `repository` and a full commit SHA kept in a committed version file) in **every job that configures Gradle**, `build` included, because `settings.gradle` loads its plugin. The job writes the ignored `local.properties` with an absolute `harness.dir`. No submodule.
- **Jobs:**
  1. `build`: the API baseline step (picks and downloads the baseline release's `api` and `compat` jars, fail-closed), `spotlessCheck`, `check` (compile with EP + NullAway, unit tests including the binary replay, `archTest` including the API rules, `apiCompat`, `apiGateSelfTest`, coverage gate, AT check), `runGameTestServer` with the discovery guard, `runCompatGameTestServer` with its own discovery guard, the datagen check, the shipping-jar checks, the Javadoc build, the `testAddon` and `testCompat` builds, and the corpus freeze check.
  2. `e2e` (needs `build`): every harness scenario under xvfb with software GL, plus the assertion checker.
  3. `bench` (nightly only, never gating): JMH ns/op and `gc.alloc.rate.norm` trends for the `model` tier.
- **Artifacts:** screenshots, `protocol.jsonl`, assertion results, GameTest logs and coverage reports are uploaded on **every** run, not only on failure, because the definition of done reviews them.
- **Releases** link the corresponding source for that version (MPL §3.2): a tag in the public repo.
- **Hygiene:** every job has a timeout, superseded PR runs are cancelled, and the full suite runs on every PR that touches code or resources. Selective testing is added only once measured cost justifies it.
- **Flaky tests:** never retried silently until green. Failed attempts are kept. A test may be quarantined only with a tracked issue, replacement coverage if needed, and an expiry date.

### Graphics backends

- **OpenGL is the gate.** Shaders follow the std140 and named-sampler conventions in the architecture spec.
- **Vulkan:** first qualify Mesa lavapipe on CI, recording the backend that actually initialised. Once that works, run a small non-blocking nightly rendering smoke test with `--graphicsBackend vulkan`. A red nightly is triaged within a week by whoever drives the build phase; it is never ignored indefinitely.
- **Real GPU:** an agent runs the rendering scenarios and client profiling locally on the reference machine for any change that affects rendering or performance, and at each release checkpoint. There is no persistent self-hosted runner. Adding one is a separate decision for the owner, and would need isolation from the workstation's credentials and execution limited to reviewed revisions.
- **Minimum graphics requirement:** no feature may raise the client's requirement above vanilla 26.2's. The build phase proves one known-working CI driver and backend, and checks any graphics feature we use that vanilla doesn't against vanilla's baseline. We set no numeric minimum of our own.

## Performance

### Reference

- **Reference machine:** the owner's workstation (Ryzen 9 7950X3D, 64 GB, RTX 3090, Fedora). The budgets are acceptance targets **on this machine**. No claim is made about how they scale to slower hardware.
- **Reference stress world:** one player plus the combined "heavy base" workloads that every system spec declares (see below), laid out and active together as one world. It is built as a harness scenario.

### Whole-mod envelope

| | Target | Also reported |
|---|---|---|
| **Server** | time attributable to the mod per tick, including the engine work it calls: **≤ 1 ms mean, ≤ 3 ms p99** | whole-server tick time (MSPT), so indirect costs show up |
| **Client** | added end-to-end frame time against a matched baseline without our content: **≤ 1 ms p50** | p95, p99 and spikes; CPU and GPU time separately |

- **Client conditions:** fixed camera and workload, 1920×1080 framebuffer, default video settings with render distance 12, VSync and frame caps off, backend and driver recorded.
- **Protocol:** 600 ticks of warm-up, then 6,000 measured ticks. Warm-up stability and noise are checked, repeated runs are all kept (never the best one picked), and steady state is reported apart from explicit load or rebuild scenarios. Rebuilds that happen in normal play stay in steady state.
- Mod p99 is never estimated by subtracting one p99 from another.

### What each system spec must declare

Every system spec has a **Performance** section with:
- its **heavy-base workload**: counts, layout and activity (e.g. how many nodes, tubes, jars and golems, doing what);
- its **share** of the envelope. Shares are a planning constraint and must sum to no more than the envelope, but the **combined** stress world is what's measured, because interactions and percentiles don't add up;
- its **declared hot paths**: what runs per tick or per frame, with a bound on the work done (e.g. per loaded chunk, per network) and an allocation budget.

### How budgets are checked

| Check | Where | Gating? |
|---|---|---|
| Allocation budget on each declared hot path (`ThreadMXBean.getThreadAllocatedBytes` after warm-up, setup outside the measured interval). Exact zero only where the path was designed to allocate nothing. | unit tests | **yes** |
| Bounded work on hot paths (e.g. operations per tick are capped, not proportional to world size) | unit tests and GameTests | **yes** |
| JMH ns/op and allocation trends for the `model` tier | nightly `bench` | no (trend) |
| System tick timing in GameTests | `build` | report-only until calibrated, then promoted. Ordinary hang timeouts always apply. |
| Whole-mod envelope in the reference stress world, server and client | reference machine, by an agent | **yes**, for changes that affect performance and at release checkpoints |

**The `GameRenderer.resourcePool` access transformer** comes in only if a profile attributes a missed client budget to resource pooling **and** a measurement shows the AT fixes it.

## Enforcement

**Publication** (the owner's decision): when the build phase starts, this repo goes public under the licences in [`NOTICE.md`](../../NOTICE.md), and `mc-agent-harness` is published as its own public MIT repo that CI checks out at a pinned commit. Until then the repo is private on the free plan, and GitHub refuses branch protection.

**Target**, enforced from publication:
- `build` and `e2e` are required status checks on `main`;
- changes reach `main` only through PRs, with linear history;
- nobody bypasses the rules, agent credentials included.

**Interim rule,** and the process on top of the target:
- Agents never push to `main` directly.
- Only the session orchestrating the build merges a PR, and only when every required job has **succeeded** (not skipped, cancelled or neutral) on the PR's head SHA, tested against current `main`. The head and base SHAs (and the merge revision, if CI tests one) are recorded.
- The merge uses an expected-head check, so a concurrent push can't slip through.
- **Independent review** means a second agent session runs `/code-review` and posts its findings as a PR comment that cites the reviewed SHA and **explicitly concludes that no blocking findings remain**. Any later push invalidates that conclusion. Both agents share one GitHub identity, so this is a procedural review, not a GitHub approval, and the spec says so.
- Review blocks unresolved **correctness or spec defects**, not style suggestions.

## Definition of done

A build ticket is done when everything below that applies to its change holds, with evidence tied to the merged commit:

1. Every static gate, test layer and the coverage gate are green in CI on the head SHA.
2. **Each acceptance criterion** in the ticket links to the test or artifact that proves it.
3. Datagen output is committed and up to date.
4. `en_us` lang is complete for everything added.
5. Assets follow the art style guide, and each one has a row in [`docs/assets-provenance.md`](../assets-provenance.md).
6. New files fall under the right rule in [`NOTICE.md`](../../NOTICE.md), and any third-party material carries its own licence and notices.
7. **Public API changes:**
   - every new API declaration has an effective, documented tier: Stable by default, or `@Experimental` or `@Internal` ([API spec, Stability tiers](api.md#stability-tiers));
   - it carries Javadoc that passes `-Xdoclint:all -Werror` with the content the API spec requires;
   - the evidence is a green `apiCompat` against the baseline, or the logged no-baseline case;
   - `testAddon` covers it, and any new public data format has `testCompat` entries.
8. No new mixin or AT, unless the architecture's order of preference and its one-line justification rule are met.
9. The harness scenario screenshots for the change have been reviewed by an agent, and the review is linked.
10. For changes that affect performance or rendering: the reference-machine measurement is attached, and the system stays within its declared share and the whole-mod envelope.
11. An independent `/code-review` on the head SHA concludes that no blocking findings remain.

A **system** is done when it also has all three test layers, its declared workload is part of the reference stress world, and its spec's acceptance criteria are all met.
