# Performance review: secondary implementation handoff

Review date: 2026-11-16. Status: proposed experiments and follow-on work, not implemented or benchmarked. Start with the [first-line handoff](performance-review-first-line-implementation.md), which contains architecture, source roots, constraints, and immediate correctness findings.

## Constraints and prerequisites

- Read applicable `AGENTS.md` instructions and [optimization objectives and scoring profiles](optimization-objectives-and-scoring-profiles.md). All filesystem activity must stay within `C:\dev`, including tools, repositories, logs, and runner state.
- `C:\dev\mismo-match` is read-only. Use existing public APIs from Trainer; do not modify its implementation, dependencies, or formats.
- The user authorized these documentation files only. Obtain approval for implementation. Ask separately before expensive benchmarks, full training, database-backed tests, downloads, or installations.
- Preserve the measurement matrix, intentional potential-match interpretation, and narrow seed scope. Production calibration requires a separately named profile and seed.
- Complete cloning correctness and baseline instrumentation before comparing evolutionary policies. Original clone behavior can widen field scope and invalidate an otherwise meaningful algorithm comparison.
- None of the proposed performance or model-quality gains has been measured. Source references are from the reviewed checkout; installed/deployed binary equivalence is unverified.

## Current evolutionary behavior to preserve as the baseline

See optimizer `engine/World.java:194`, `:357`, `:374`, and `Creature.java:138` under `mismo-optimizer/src/main/java/org/immregistries/mismo/optimizer/`.

For population `N`, let `k = floor(sqrt(0.5 * N))`. The upper `N - k²` survive, up to `k²` lower candidates are replaced, and only ranks `0..k-1` can breed. At default `N=1000`, that is 516 survivors, up to 484 replacements, and 22 eligible parents. Duplicate rejection leaves the old candidate in place without retrying.

Initialization preserves the first seed candidate, alternates randomization and tweak for the rest, and does not widen enabled flags by those operations alone. Distinct-parent offspring use node-wise crossover. Same-parent offspring clone and tweak. `Creature.mutate()` is not invoked by the training loop; crossover itself has rare randomization. `World.run()` continues until external stop or failure. `plantSeed()` has no production caller found, so ongoing island migration is not currently implemented.

The fitness surface is discontinuous: hierarchical thresholds and final classes create plateaus. The existing evolutionary approach is plausible, but its superiority over simpler search has not been demonstrated.

## Prioritized secondary work

| Work | Priority | Expected benefit | Complexity | Behavioral risk | Dependency compatibility | Evidence |
|---|---|---|---|---|---|---|
| Run-scoped seeded Trainer operators | Medium | Reproducible search experiments | Medium–High | Changes search trajectory | Existing public node accessors | Current seed gap confirmed |
| Explicit selection policy and parent pool | Medium | Less rejection overhead; possible better diversity | Medium | Changes selection distribution | Trainer-only | Formula confirmed; quality hypothetical |
| Targeted perturbation and diversity tracking | Medium | More useful evaluations | Medium | Changes candidate distribution | Public setters; preserve disabled flags | Current operators confirmed; gain hypothetical |
| Budget and stagnation handling | Medium | Bound unproductive runs | Medium | May stop before later improvement | Trainer-only | No automatic stopping confirmed |
| Reduce child YAML parsing | Medium | Lower serial CPU and allocation | Medium–High | Aliasing/metadata risks | Keep engine export format | Repeated parsing confirmed; impact unknown |
| Exact raw-detector score cache | Medium | Potentially large fitness speedup | High | High until equivalence proven | Trainer replay with engine fallback | Repeated detector work confirmed |
| Scoring thread tuning/task batching | Medium | Better CPU efficiency | Low–Medium | Low with score equivalence | Existing APIs | Parallelism exists; optimal setting unknown |
| Bounded saved evaluations | Medium | Less heap and persistence overhead | Medium | Transaction/lifecycle changes | Web-only | Full materialization confirmed |
| Request-local Hibernate sessions | Medium | Reliable concurrent browser requests | Medium | Request lifecycle changes | No matcher impact | HTTP-session sharing confirmed |
| Bounded cross-generation fitness cache | Low initially | Avoid reevaluating returning genotypes | Medium | Stale-key risk | Store counts, not matcher state | Recurrence rate unmeasured |
| Legacy cleanup | Low | Less maintenance burden | Low–Medium | Operational/external-call uncertainty | No matcher changes | Usage evidence varies |

## 1. Reproducibility before algorithm experiments

`World.java:348` has a static random generator. Dependency `matchers/MatchNode.java:369` has another shared static generator used by randomization, crossover, tweak, and mutation. Seeding only the world will not reproduce full runs.

Implement optional run-scoped randomness in Trainer-owned initialization and variation, applying values through public node getters/setters. Avoid reflection into the dependency's static state. Define stable node traversal, tie ordering, and parent selection. Preserve topology and enabled scope.

Record seed, optimizer/operator version, seed configuration hash, Test Set snapshot hash, scoring profile version/weights, matcher artifact identity, and relevant parameters. If protocol or schema changes are needed, plan backward-compatible defaults explicitly. Schema SQL belongs in `mismo-trainer-web/src/db/unapplied_updates.sql`, and mappings must agree.

Validate identical generation traces for repeated seeded runs, including different scoring thread counts where evaluation order does not affect results. Do not promise exact crash continuation: existing checkpoints store only the best candidate. Population/RNG checkpointing is a separate, lower-priority feature.

## 2. Selection pressure and rejection overhead

`World.pickParentPos()` accepts absolute-Gaussian samples only below `k`, but Gaussian scale is `(N-k)*0.3`, approximately linear in population. Accepted interval grows only with the square root. At `N=1000`, scale is 293.4 and accepted ranks are 0–21.

Code-derived implications, not benchmark results:

- Rejection grows with population size.
- Conditional rank probabilities within the small accepted interval are nearly uniform, despite comments describing a strong preference for the top.
- Only a small fraction of retained candidates contributes to breeding.

Separate two changes:

1. Remove rejection while preserving the existing rank distribution, subject to distribution validation.
2. Introduce an explicitly versioned tournament or rank policy with a configurable parent pool.

Compare modest alternatives before adding complexity. Measure parent participation, distinct offspring, evaluations to useful fitness, and held-out quality. A broader pool may help diversity but can slow exploitation. Do not present a new selection policy as behavior-preserving.

## 3. Mutation, crossover, and initialization

Dependency `MatchNode.java:424` samples both tweaked bounds around the old minimum with standard deviation 0.20. This differs from a perturbation centered on each bound and from nearby approximate descriptions. `mate()` at line 454 inherits min/max/enabled independently and occasionally randomizes values. Its generation-dependent `mutate()` at line 410 is not used by actual training.

After first-line cloning fixes:

- Test small-subset perturbation of active genes rather than tweaking all nodes.
- Perturb each bound around itself, enforce `[0,1]` and min ≤ max, and preserve disabled detectors.
- Retain the unchanged seed as a baseline candidate.
- Compare initial mixtures of seed-near candidates and randomized candidates under equal evaluation budgets.
- Consider retaining min/max pairs together during crossover if independent inheritance proves disruptive; test rather than assume.
- Track both genotype diversity and classification diversity. Different weights can yield identical predictions on plateaus; equal fitness alone does not establish genetic equivalence.

Start with fixed operator rates and step sizes. Adaptive mutation should follow evidence: a conservative proposal is to increase perturbation or introduce a few scope-preserving immigrants after a sustained plateau, then restore ordinary settings after improvement. Do not adapt the measurement objective itself.

## 4. Elitism, stopping, and simpler search baselines

Roughly half the population survives, while only the best `k` breed. Test survivor fractions and parent-pool sizes independently. Maintain an explicit best-ever candidate, even when restart or diversity strategies replace much of the population.

Add optional maximum evaluations, elapsed time, and generations. A stagnation window should be defined in evaluation/time terms as well as generations because duplicate rejection changes actual work per generation. Initially expose plateau diagnostics rather than declaring global convergence.

Compare the corrected GA with random search and best-candidate local perturbation with occasional restarts. This bounded numerical problem has hierarchical threshold interactions; gradient-based optimization is not an obvious fit. More sophisticated methods require an observed weakness and an equal-budget win.

Do not stop simply at fitness 1.0 unless the selected profile's mathematical properties justify that target. Named custom profiles may have different normalization behavior.

## 5. Child construction and YAML parsing

Both `Creature` offspring constructors (`:138`, `:163`) build a `PatientCompare` from the parent's configuration script, invoking YAML parsing and tree construction, before overwriting genetic values. Dependency `model/Configuration.java:250` also serializes during reading. This serial work can matter especially when Test Sets are small.

Profile before implementing a factory or reuse scheme. A Trainer-owned fixed-topology template plus corresponding-node parameter copying could reduce parsing. Keep engine serialization for exported configurations and preserve metadata, detector definitions, scoring weights, and topology.

Do not reuse candidate objects still referenced by scoring tasks, snapshots, best-candidate reporting, or other threads. A pool that corrupts a retained winner is worse than allocating a new tree. Prefer a straightforward factory over a broad pooling abstraction unless allocations demonstrably dominate.

Acceptance: exported/reloaded configurations match in-memory candidates, offspring never alias parents, discarded candidates cannot mutate published results, and measured allocation/wall-time improves.

## 6. Exact detector precomputation

Current fitness repeatedly evaluates identical patient pairs through detector definitions that often depend only on patient fields, while evolution changes weight ranges. Examples include Jaro-Winkler in dependency `matchers/SimilarMatchNode.java:29`, address comparisons in `AddressMatchNode.java:34`, and splitting in `AddressStreetNameMatchNode.java:42`.

Proposed experiment:

1. Audit each supported leaf implementation for dependencies on min/max, enabled state, mutable state, and detector parameters.
2. Use existing engine leaf `score(patientA, patientB)` methods to precompute exact raw doubles for fixed pair/detector definitions.
3. Replay aggregation in Trainer using the same child order, enable checks, clamping, thresholding, and min/max scaling.
4. Retain engine evaluation as the reference and fallback for unsupported definitions.

Trainer `servlet/SignatureClassifier.java:64` already replays aggregation for decoded signatures, but it is not an exact training cache. Dependency signatures quantize values in `PatientCompare.java:105`; never train from those quantized values as a supposedly equivalent optimization.

Cache keys must include full detector identity/settings and fixed input identity. Enabled scope can differ between candidates; a seed-only cache must not silently omit a detector that could legally be used. Correct clone scope first. Dense memory is approximately `8*T*L` bytes for `T` cases and `L` raw cached leaf scores, before overhead. Consider bounded/chunked storage only after measuring memory needs.

Acceptance requires classification and complete confusion-matrix agreement over fixed and randomized valid configurations, missing values, alternate fields/addresses, enabled/disabled groups, and values near thresholds. Preserve floating-point addition order; tolerance in intermediate values is insufficient if a final classification differs. Do not change engine quirks while optimizing them.

## 7. Parallelism and resource tuning

Candidate-level parallelism already exists in `World.scoreAndSort()` at line 283. Patient preparation is shared read-only, and each candidate has its own comparison state. Do not propose parallel scoring as a new feature.

Expose a recorded runner concurrency setting if useful and compare one thread through increasing counts. Measure CPU utilization, throughput, allocation, GC, and responsiveness. Processor count minus one is a default, not a measured optimum.

Only batch tasks if small scoring jobs show meaningful executor overhead. Avoid nested candidate/case parallelism and multiple pools competing for cores. Improve cancellation within long case passes before increasing parallel complexity.

## 8. Saved evaluation and web concurrency

`web/servlet/OrgScope.java:449` loads all cases, builds all result objects, computes classifications and signatures, and then saves results individually. `EvaluationServlet.java:68` performs this inside a synchronous request transaction. Signature generation traverses detectors again after classification.

For demonstrated large-evaluation problems, investigate bounded pages, projections, and controlled flush/clear behavior. Preserve all per-case outputs and atomicity, or explicitly add an evaluation lifecycle before splitting transactions. Native result ID generation (`src/main/resources/EvaluationResult.hbm.xml:6`) means JDBC batching effectiveness must be measured rather than assumed.

`HomeServlet.java:53` stores Hibernate sessions in the HTTP session and closes/replaces them during request setup; teardown closes and removes them. Concurrent requests for the same browser session can interfere. Move persistence sessions to request ownership while preserving `OrgScope` checks and transactions. Avoid sharing mutable session-selected `PatientCompare` state across concurrent comparison work.

Acceptance: concurrent requests cannot close each other's persistence sessions, failure rolls back the appropriate work, and evaluation contents/signatures remain unchanged. Database tests require approval even though existing test transactions roll back.

## 9. Optional bounded fitness memoization

Unchanged survivors already retain scores. Additional memoization only helps when an identical genotype returns after leaving the population or reaches the evaluator through another route.

Measure recurrence before implementation. Use complete genotype, Test Set, scoring-weight, and engine identity. Cache confusion counts if later scoring requires them; do not cache mutable scorer or matcher objects. Bound memory and validate invalidation. Do not deduplicate by fitness or classification alone: those candidates can have different future breeding behavior.

## 10. Legacy and maintenance cleanup

| Candidate | Review evidence | Recommended treatment |
|---|---|---|
| `web/App.java` and `src/test/.../AppTest.java` | Hello World and unconditional assertion; no application integration found | Strong removal candidates, negligible runtime gain |
| `World.rescore` | Getter/setter state does not influence scoring | Remove/deprecate after checking optimizer consumers |
| `World.plantSeed()` | No production caller found | Keep only for an explicit migration plan; do not claim migration exists |
| `Creature.readScript()` | No caller found; does not invalidate prior fitness | Remove/deprecate or fix invalidation if retained |
| `Creature.mutate()` and demonstration `main()` | Mutation called by demo, not training | Clarify actual algorithm; do not enable silently |
| `Creature.isSame()` | Demo usage; dependency deep comparison is flawed | Do not use as genotype equality |
| `TestScriptExploreServlet` | Reachable classpath lookup for moved legacy scripts | Honor documented production-check gate before removal |
| `TemplateDataBootstrap` | Manual entry point reading legacy data | Absence of Java callers does not prove dead code |
| `ExampleServlet` | Servlet mapped and linked from navigation | Usage decision required; not unreachable |
| Hibernate/C3P0/Javassist dependencies | Runtime integrations can be indirectly loaded | No removal justified by source search alone |
| SnakeYAML | Directly imported by `WeightSetServlet` | Not an unused dependency |

Previously fixed XOR replacement, Missing-from-Twin copy, static scorer weights, and parsing/parallelism issues must not be reported as still unfixed. Consult `known-issues.md`. The gender/mother's maiden name documentation-versus-seed discrepancy remains explicitly deferred; do not resolve it as a performance change.

## Benchmark and regression strategy

### Runtime: faster generations

Use frozen snapshots and fixed scripts across small and representative larger datasets, several populations, and multiple scoring-thread counts. Separate startup, initial scoring, and steady-state generations. Warm the JVM and repeat measurements; report spread rather than a single favorable result.

Measure wall time by phase, accepted candidates/second, pair comparisons/second, allocations, GC time, heap, CPU, parser calls, detector calls, serialization/reporting overhead, page latency, and evaluation insert throughput. Use Java Flight Recorder if already available and approved; write artifacts only inside `C:\dev`. Do not add a benchmark dependency by default.

### Search quality: fewer evaluations to useful results

Run multiple seeds with both equal full-evaluation budgets and equal wall-clock budgets. Compare corrected GA, each isolated policy change, and simple baselines. Report best training fitness, held-out fitness, time/evaluations to a predefined useful score, full 3x3 confusion matrices, collapsed potential-match 2x2 matrices, diversity, duplicates, and plateau lengths.

Keep related patient pairs together across splits where possible to reduce leakage. Preserve measurement field scope and scoring profile. Separate search quality improvements from raw execution throughput; faster generations alone do not establish better optimization.

### Required compatibility gates

- Fixed candidate classifications and confusion matrices match the current engine reference.
- Export/reload matches in-memory scoring and embeds the selected weights.
- Shared-patient and sequential/parallel equivalence remain true.
- New operators preserve disabled fields, topology, bounds, and parent immutability.
- Repeated seeded runs reproduce traces under documented conditions.
- Cached paths miss on any relevant input or engine change.
- Saved evaluations preserve case output and signature formats.
- Existing authorization and ownership checks remain intact.
- Any intentionally changed search behavior has explicit version/provenance and a held-out comparison.

## Suggested delivery phases

1. Complete first-line correctness, instrumentation, and reporting work; establish baseline.
2. Add seeded Trainer-owned operators and optional budgets; compare simple baselines.
3. Experiment independently with selection, survivor fraction, initialization, and targeted perturbation. Introduce adaptation only if fixed policies show a measurable weakness.
4. Address child construction or exact raw-score caching according to measured phase costs, one at a time.
5. Address large saved evaluations and remaining legacy cleanup according to operational evidence.

At every phase, report what changed, which behavior is intentionally different, measured benefit with uncertainty, and remaining compatibility limits. Preserve a reference execution path until higher-risk evaluation optimizations have passed differential regression testing.
