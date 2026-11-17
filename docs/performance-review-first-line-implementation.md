# Performance review: first-line implementation handoff

Review date: 2026-11-16. Status: recommendations only; no application changes or runtime measurements were made during the review. This document and its [secondary implementation companion](performance-review-secondary-implementation.md) were subsequently authorized as documentation deliverables.

## Instructions for the next agent

### Phase 1 implementation update (2026-11-17)

The user approved the clone-correctness phase only. `Creature` now uses Trainer-owned
`MatchTreeCopy` to validate all four corresponding network structures and detector definitions
before copying each node's own enabled/min/max values. The matcher dependency is unchanged.
`CreatureCopyTest` covers corresponding values, stale parent scripts, scoring/classification
and export/reload equivalence, parent independence, incompatible definitions/topology, and
disabled scope through variation and small generations. Validation used Java 17 and Maven
under `C:\dev\tools`, with all dependencies in `C:\dev\.m2\repository` and temporary/user-home
paths redirected beneath Trainer's ignored `target/phase1-build` directory. The user explicitly
approved dependency downloads and building/installing the existing matcher source for validation;
no matcher source was changed. Its 8 tests passed, as did all 21 optimizer and 13 runner tests.
The focused copy regression was also run against the legacy clone calls and failed as intended
(a child minimum of 0.13 was overwritten with the root's 0.1); corrected source was restored.
No database tests, full training, or expensive benchmarks were run. Production binary equivalence
remains unverified; these results apply to the locally built current matcher source.

Runner client versions carry a `+copy1` suffix (for example, `2.1+copy1` or `dev+copy1`)
so new runs identify corrected copying using the existing client-version field. This suffix
does not change the protocol or configuration format and preserves the numeric release prefix.
Existing runs and artifacts are not rewritten. Corrected offspring can follow different
optimization trajectories; no performance gain is claimed.

Instrumentation, reporting/cancellation, hash/index caches, candidate-summary caching,
eligibility accounting, and prepared web inputs remain unimplemented and require separate
phase approval. The original findings below describe the reviewed pre-fix baseline.

1. Read `C:\dev\AGENTS.md`, the repository `AGENTS.md`, and any more specific instructions before working. All file access, including subprocess access, must remain within `C:\dev`. Stop and obtain explicit approval for anything outside it.
2. Treat `C:\dev\mismo-match` as strictly read-only. Implement workarounds and improvements in Trainer using existing public APIs. Do not install, rebuild, modify, or upgrade that dependency as part of this plan without a new discussion of scope and filesystem implications.
3. Read [optimization objectives and scoring profiles](optimization-objectives-and-scoring-profiles.md), [known issues](known-issues.md), and [Island Runner implementation plan](island-runner-implementation-plan.md). The measurement matrix, inclusive matching purpose, narrow seed scope, and twins in Possible Match are intentional. Do not change them.
4. The user authorized writing these two documents, not implementing the recommendations. Obtain implementation approval before changing application code. Expensive benchmarks, full training, database-backed tests, downloads, and installations require approval.
5. Preserve organization scoping through `OrgScope`, existing configuration and protocol formats, and existing matcher semantics. Keep the optimizer free of Hibernate and servlet dependencies.
6. Recheck source references before implementation: line numbers below identify the reviewed checkout and can drift. No builds, tests, database inspection, profiling, or deployed-binary inspection were performed. Code-confirmed work is not a measured bottleneck.

Do not silently use a default Maven repository, runner home, profiler directory, or log location outside `C:\dev`. Set an explicitly approved in-boundary location where applicable; do not copy or inspect an external repository to work around the boundary. Availability of dependencies inside the boundary is currently unknown.

## Architecture and workflow established by the review

The root Maven project contains `mismo-optimizer` (JAR), `mismo-trainer-web` (WAR), and `mismo-runner` (shaded console JAR). The root POM pins `mismo-match:1.1`; the checked-out sibling sources were inspected, but agreement with installed/deployed binaries was not verified.

- Web administrators select seed configuration, Test Set, population, and versioned Scoring Profile.
- `runner/TestSetSnapshots.java:28` builds an ordered snapshot of scoreable cases. Snapshot JSON is stored and SHA-256 identified.
- Runner `RunnerMain.java:321` downloads/verifies inputs, converts cases, stamps profile weights into the seed, creates the server run, and starts `RunSession`.
- `RunSession.java:64` creates a `World`; the world owns population evolution and parallel fitness evaluation.
- `World.java:44` parses and prepares patient pairs once. `Creature.java:180` uses `setPatients()` and accumulates the confusion matrix with `Scorer`.
- Unchanged survivors retain scores. New candidates are scored using a persistent executor, normally processor count minus one.
- `RunSession` samples progress, checkpoints changed best candidates, and reports progress every minute and changed candidates at most every five minutes. Checkpoints recover the best candidate, not population or random state.
- `RunnerApi.java:251` stores uploaded candidates and checks embedded scoring weights; it trusts the uploaded fitness rather than independently scoring at ingestion.
- `CandidateScoring.java:41` independently scores a selected candidate against frozen inputs when the run page displays it.
- `OrgScope.java:449` runs saved evaluations synchronously in the request transaction, storing classification and signature per case.

Source roots for unqualified file references:

| Area | Root beneath repository |
|---|---|
| Optimizer | `mismo-optimizer/src/main/java/org/immregistries/mismo/optimizer/engine/` |
| Runner | `mismo-runner/src/main/java/org/immregistries/mismo/runner/` |
| Web | `mismo-trainer-web/src/main/java/org/immregistries/mismo/trainer/` |
| Dependency | `../mismo-match/src/main/java/org/immregistries/mismo/match/` |

The dependency classifies a pair by four networks: absent Match signal means Not a Match; otherwise any Not Match, Missing, or Twin signal means Possible Match; otherwise Match. Aggregates sum enabled weighted children, clamp above 1, zero below 0.5, then apply their own min/max transformation. Preserve evaluation order and thresholds.

Fitness is `sum(count[i][j] * weight[i][j]) / sum(count[i][j] * weight[i][i])`. Only Match, Possible Match, and Not a Match expectations are scoreable. The denominator-zero behavior currently returns zero.

## Recommended order

| Order | Work item | Priority | Complexity | Main benefit | Behavioral risk | Evidence |
|---|---|---|---|---|---|---|
| 1 | Correct clone copying and test scope invariants | High | Medium | Correct search space and offspring | Changes evolved results intentionally | Source-confirmed defect |
| 2 | Add phase timing and evaluation counters | High | Low | Evidence for subsequent optimization | Low | Measurement gap |
| 3 | Publish immutable progress and reliable stop state | High | Medium | Consistent reporting and lifecycle | Low intended | Source-confirmed publication gaps |
| 4 | Cache existing population hashes | High | Low–Medium | Less serial breeding work | Low with exact legacy hashes | Repeated work confirmed; gain unmeasured |
| 5 | Introduce collision-safe genotype indexing | High | Medium | Scalable, correct duplicate detection | Changes collision handling | Hash-only equality confirmed |
| 6 | Cache immutable candidate result summaries | High | Low–Medium | Faster run-details requests | Low with complete keys and authorization | Re-evaluation confirmed; gain unmeasured |
| 7 | Align evaluation eligibility accounting | Medium | Low | Correct displayed totals | Changes affected summaries | Source-confirmed inconsistency |
| 8 | Reuse prepared web evaluation inputs | Medium | Low–Medium | Avoid reparsing | Low with equivalence tests | Repeated parsing confirmed |

All items preserve `mismo-match` APIs and leave its files untouched. No speedup percentages can currently be justified.

## 1. Correct cloning before algorithm tuning

### Finding

`Creature.java:163` constructs a clone through a configuration script and then invokes `clone()` on each root network. Dependency `matchers/AggregateMatchNode.java:171` passes the source aggregate itself to every descendant's clone operation, rather than pairing corresponding children. Leaf `MatchNode.clone()` copies min, max, and enabled from that aggregate.

For enabled roots, disabled descendants can become enabled. The subsequent tweak changes their values. This can widen measurement field scope, contrary to the documented invariant. This is a separate issue from the already-fixed Missing-from-Twin bug listed in `known-issues.md`.

Dependency aggregate `isSame()` at line 183 has a similar corresponding-child problem. Do not use it as the new deep equality check.

### Proposed implementation

- Add Trainer-owned corresponding-node copying through public getters/setters.
- Validate compatible topology and detector definitions before copying.
- Copy each node's own enabled/min/max values, including aggregate values, rather than copying roots into descendants.
- Keep the matcher dependency unchanged and retain engine configuration serialization.
- Preserve seed-disabled fields throughout initialization, cloning, crossover, and mutation. Do not reinterpret the known discrepancy concerning gender and mother's maiden name; it remains a maintainer decision.
- Clearly identify corrected optimizer behavior in run/version provenance. Preserve historical artifacts.

### Acceptance criteria

- A clone before variation has identical corresponding-node values and identical classifications, confusion matrix, and fitness.
- At least one test uses enabled roots with disabled intermediate groups and leaves, plus distinct child weights, to expose the actual defect.
- Seed-disabled detectors remain disabled after clone-and-tweak and subsequent generations.
- Export/reload reproduces the in-memory candidate's fitness and enabled flags.
- Add a focused regression to `mismo-optimizer/src/test/java/org/immregistries/mismo/optimizer/engine/WorldTest.java` or a dedicated copy test.

Risk: optimization trajectories will change. This restores intended copying; it does not promise equality with historically corrupted offspring.

## 2. Establish performance instrumentation

Instrument `World` initialization, `makeNewGeneration()`, `scoreAndSort()`, and candidate serialization/reporting separately. Use monotonic elapsed timing. Keep instrumentation outside the innermost detector calls unless a profiler shows it is needed.

Record per generation:

- Attempted, accepted, and duplicate-rejected offspring.
- New candidates actually evaluated and total case comparisons.
- Breeding, duplicate checking, scoring, sorting, and snapshot publication time.
- Best score, population fitness spread, and last improvement.
- Scoring thread count, population, case count, run/input/profile identifiers.

Existing `scoreRate` measures the scoring phase only; it is not end-to-end candidate production throughput. Do not optimize against that metric alone. Avoid noisy per-case logging or unbounded metric storage.

Acceptance: timing totals explain generation wall time, counters reconcile with accepted/evaluated offspring, and instrumentation does not change fixed-candidate scores.

## 3. Publish stable progress and stop requests

### Finding

`World.java:417` exposes a shallow array snapshot without consistent publication to unsynchronized readers. `keepRunning` at line 421 is not volatile. Generation, rate, and messages also cross threads without consistent visibility.

`RunSession.best()` at line 130 calls `Creature.makeScript()`, which updates the configuration script. This can overlap world construction reading a parent's configuration script. Synchronized world methods do not protect these reporter reads.

### Proposed implementation

- Publish an immutable generation summary through a volatile reference or equivalent established synchronization.
- Capture a consistent generation, candidate identity/birth generation, score, script, and metrics at a completed-generation boundary.
- Avoid repeatedly serializing an unchanged best candidate; avoid exposing mutable trees to reporters.
- Make stop visibility explicit and check cancellation within long case loops. Never mark partially evaluated candidates scored or publish partial-generation results as complete.
- Keep failure reporting and executor cleanup intact. Avoid a broad lock that stalls optimization during network/disk reporting.

Acceptance: concurrent reporting cannot produce mismatched script/fitness; stopping a long pass has bounded latency; interrupted work cannot replace a previously valid best candidate; existing sequential/parallel equivalence remains true.

## 4–5. Improve duplicate detection in two reviewable steps

### Finding

`World.makeNewGeneration()` at line 389 computes each baby hash and scans every current candidate, recomputing each recursive hash. `Creature.hashCode()` at line 152 and dependency `AggregateMatchNode.hashCode()` at line 98 build strings while traversing trees.

Worst-case duplicate screening is approximately `O(N² * D)` per generation for population `N` and tree size `D`. Fitness is approximately `O(U * T * D)` for `U` new evaluated candidates and `T` cases, so the dominant phase depends on workload.

Current duplicate equality is only a 32-bit hash comparison. A rejected child is not retried; the old candidate remains.

### Step 4: preserve behavior while eliminating repeated work

Cache exact existing hashes for unchanged candidates and update them on replacement. Preserve scan membership and duplicate behavior initially. Invalidate cached hashes whenever genotype changes; do not treat a mutable tree as immutable by convention alone.

### Step 5: complete genotype keys

Introduce Trainer-owned immutable genotype keys and an indexed population membership count. Keys must cover corresponding min/max/enabled values and any varying topology/detector definition. Exclude date, score, birth generation, and reporting metadata. Do not key by serialized scripts that contain those metadata fields.

Use full equality within hash buckets. Preserve multiplicities and correctly remove replaced entries. Keep rejected-child policy unchanged until an explicit algorithm experiment changes it. Do not substitute the dependency's flawed aggregate `isSame()`.

Acceptance: forced hash collisions do not incorrectly reject distinct genotypes in step 5; membership survives replacements and duplicates; step 4 preserves existing decisions on fixed inputs; phase timings demonstrate actual benefit.

## 6. Cache frozen candidate summaries

`servlet/OptimizationRunServlet.java:175` invokes `runner/CandidateScoring.score()` on page display. That method reparses JSON and patients and evaluates the full frozen snapshot.

Cache immutable confusion-matrix/fitness summaries by candidate content hash, Test Set snapshot hash, scoring-weight identity, and engine implementation identity. Authorize through existing scopes before returning cached data. Bound memory and protect cached arrays from mutation. Do not cache Hibernate entities, mutable `PatientCompare` instances, or arbitrary live Test Sets.

Acceptance: cache hit and miss return identical values; changed inputs miss the cache; unauthorized readers cannot obtain private results; cache growth is bounded; repeated page views avoid repeated evaluation. Cross-process persistent caching is unnecessary initially.

## 7. Align evaluation eligibility

`Scorer.registerMatch()` ignores all non-scoreable expectations. `servlet/OrgScope.java:474` excludes only Not Sure from evaluation scorable/agree/disagree accounting. Research or blank labels can therefore affect displayed totals without affecting fitness. Current database prevalence was not inspected.

Use the same scoreable-status predicate for fitness and displayed scorable/agree/disagree counts. Preserve result rows for every case, including unscoreable cases. Decide explicitly how existing Not Sure counters should represent Research/blank cases; do not silently relabel them or alter historical persisted evaluations.

Acceptance: Match/Possible/Not Match, Not Sure, Research, blank, and supported nullable conversions have consistent accounting and unchanged classification outputs.

## 8. Avoid redundant patient parsing in web paths

`TestCaseLoader.java:104` creates runtime patients; `PatientCompare.setMatchItem()` in the dependency at line 209 parses strings again. `World.setMatchItemList()` also constructs prepared pairs, although that cost is already outside the training hot loop.

Reuse explicitly prepared case inputs in repeated evaluation paths, retaining current null/default conversion behavior. Do not mutate shared patients. Use `prepareForSharing()` where cross-thread sharing applies. Avoid expanding the web-specific `RuntimeMatchItems` adapter into optimizer persistence coupling.

Acceptance: compare complete confusion matrices and classifications against the original parse-per-case path. Preserve signature output in saved evaluations.

## Validation and handoff checklist

Existing `WorldTest` covers repeated scoring, independent world weights, sequential/parallel agreement, shared-versus-fresh patients, and replacement slots. Extend those meaningful tests rather than replacing them with implementation-mirroring assertions.

- [ ] Confirm current checkout and installed engine agreement within permitted access, or document that it remains unverified.
- [ ] Obtain implementation scope approval.
- [ ] Add regression coverage before cloning changes.
- [ ] Keep measurement profile and field scope unchanged.
- [ ] Establish a corrected baseline before search-quality changes.
- [ ] Use fixed candidate scripts and frozen cases for runtime equivalence.
- [ ] Preserve configuration export/reload and protocol compatibility.
- [ ] Keep artifact and runner paths within `C:\dev`.
- [ ] Ask before expensive benchmarks, full runs, database tests, or downloads.
- [ ] Report measurements, uncertainty, and intentionally changed behavior separately.
- [ ] Update known issues and relevant architecture documentation when fixes are actually implemented.

Suggested first implementation batch: cloning regression and workaround, lightweight phase instrumentation, immutable reporting, and cached legacy hashes. Review separately before introducing complete genotype equality or changing evolutionary policies.
