# Weight-Set Optimization & the Island Architecture (v1 Snapshot)

**Status:** Verified by direct reading of `Island.java`, `IslandSync.java`, `model/Creature.java`, `model/Scorer.java`, `model/World.java`, `random/Transform.java`, `random/Transformer.java`, `random/Typest.java`, `CentralServlet.java`, `GenerateWeightsServlet.java`, `island.yml`, and `pom.xml`'s `exec-maven-plugin` config. This resolves conceptual doc §19–§21.

This is the evolutionary/genetic-algorithm engine that searches for better `Configuration` weight sets (see `matching-engine.md` §10) by scoring candidates against a labeled training set and breeding the best performers. It can run two ways: as a standalone CLI process (`Island`, the current recommended path) or embedded in the webapp (`GenerateWeightsServlet`, explicitly marked deprecated in its own javadoc).

## 1. Core vocabulary, precisely

| Term | What it actually is |
|---|---|
| **World** | One running optimization instance: a live population (array of `Creature`) plus the generation loop. `World extends Thread`. **Not persisted** — it lives only in JVM memory for the life of the process. If the process dies, all in-memory generation/population progress is lost except whatever was last synced to the DB. |
| **Creature** | One candidate weight set — the GA "individual." Wraps a `PatientCompare` (the actual `Configuration`/weight tree) plus a cached `score`. Serializes to **the exact same YAML format as `Configuration.yml`**. No explicit lineage/parentage field is stored — genealogy exists only implicitly, at breeding time, then is discarded. |
| **Scorer** | Computes one `Creature`'s fitness: a 3×3 confusion-matrix dot product against `scoringWeights` (§3). |
| **Island** | One OS process running one `World`. The CLI entry point (`Island.main`) launches exactly one `World` per JVM — there is no support for running multiple islands in a single process. |

## 2. `World` state and persistence details

Fields: `matchItemList` (the loaded training set — **from a local flat file, not the DB**, see §5), `generation`, `creatures[]` (the live, mutating population), `creaturesCopy[]` (a stable end-of-generation snapshot for safe external reads by the CLI dashboard or `GenerateWeightsServlet`), selection parameters `lowerCut`/`lowerCutStart`/`upperCut` (§4), `worldName`/`islandName` (stamped onto every `Creature`), an optional `seed` `Creature` (plantable via `plantSeed()` for cross-island seeding), and thread-control fields `keepRunning`/`lastMessage`/`scoreRate`.

**`rescore` is dead code** — has a getter/setter, is never read anywhere in the codebase.

**What actually gets persisted to the `configuration` DB table is individual `Creature` snapshots, uploaded by `IslandSync` (§6) — and only the single best creature, only periodically.** `CentralServlet`'s update handler looks up the *existing* latest row for a given `(worldName, islandName)` and overwrites it in place rather than inserting a new row per generation — **so the DB never retains generation-over-generation history for an island**, despite `generation` being a column that implies it should. This matches `data-model.md`'s finding that the production `configuration` table has just one row.

## 3. Scoring — exactly a normalized 3×3 confusion-matrix dot product

For each labeled `MatchItem` with a set expected status, `Creature.score()` runs it through the wrapped `PatientCompare` to get an actual result, then `Scorer.registerMatch` buckets `i` = expected (0=Match, 1=Possible Match, 2=Not-a-Match) and `j` = actual (same buckets), incrementing `countTable[i][j]`. After all test cases:

```
actualScore         = Σ(i,j) countTable[i][j] * weights[i][j]
totalScorePossible  = Σ(i,j) countTable[i][j] * weights[i][i]     // diagonal weight for row i, for every j
score               = actualScore / totalScorePossible            // 0 if totalScorePossible == 0
```

i.e. **the fraction of the maximum achievable score** (the score if every case had landed on its own expected-row's diagonal weight) — typically displayed as a percentage (`Island` prints `(int)(score*100+0.5)`). The 9 `weights[i][j]` cells map directly to `Configuration.yml`'s `scoringWeights` block (`shouldMatch_Matches`→`weights[0][0]`, `shouldMatch_Possible`→`weights[0][1]`, … `shouldNoMatch_NoMatch`→`weights[2][2]`).

**⚠ Bug: `Scorer.weights` is a mutable `static` field, shared by every `Scorer` instance in the JVM.** Both `Island.main` and `GenerateWeightsServlet` mutate it **in place** through the live reference `Scorer.getWeights()` returns (no defensive copy). In the webapp this is process-global — one user editing weights on `GenerateWeightsServlet` silently changes scoring for every concurrent user and every other running optimization in that JVM.

**⚠ Finding: four different sources of truth for the same 9 default numbers, and they disagree:**

| Source | shouldMatch (Matches, Possible, NoMatch) | shouldPossible (M, P, N) | shouldNoMatch (M, P, N) |
|---|---|---|---|
| `Scorer.java` hardcoded default | 20, 0, −5 | −20, 10, 0 | −40, −10, 10 |
| `Scorer.java`'s own javadoc table | 20, −5, −20 | −5, 20, −5 | −10, −5, 10 |
| `Configuration.yml` default | 12, 10, −10 | 8, 10, −8 | −10, −8, 10 |
| `island.yml` | 12, 10, −10 | 10, 12, −8 | −4, −2, 10 |

None of these four agree with each other. A v2 rewrite needs to pick one canonical source (almost certainly the YAML config, not a hardcoded Java default) and delete the rest.

## 4. The evolutionary algorithm

**Population size:** `populationSize` from `island.yml` (production example: `1000`).

**Initialization** (in `World`'s constructor, per population slot `i`):
- `i == 0`: exact copy of the seed/starting configuration — the "pure baseline," unmodified.
- `i > 0`, even: `tweak()` — a small (~10%, per `Creature`'s own javadoc) perturbation of the seed.
- `i > 0`, odd: `randomize()` — full re-randomization (`MatchNode.makeRandom()`).

So the population is **seeded from an existing configuration when one is available** (from the central server, or the built-in default), half lightly perturbed, half fully random — not a from-scratch random population.

**Selection (`pickParentPos`)**: Gaussian rejection sampling — repeatedly draw `abs(random.nextGaussian()) * upperCut` until it lands below `lowerCut`, so parents always come from the top `lowerCut` creatures (index 0 = best) in the score-sorted array, weakly favoring the very best within that elite band. `lowerCut = (int)Math.sqrt(0.5 * size)` (≈22 for size 1000); `upperCut = (size - lowerCut) * 0.3`.

**⚠ Bug (high-impact): `lowerCutStart = size - (lowerCut ^ 2)` uses Java's bitwise XOR operator, not squaring.** The author almost certainly meant "lowerCut squared." For `size=1000`, `lowerCut=22`: intended `1000 - 484 = 516`; actual `22 ^ 2 = 20` → `lowerCutStart = 980`. Since `makeNewGeneration()` only replaces `creatures[lowerCutStart..size)`, **this bug means only ~20 of 1000 creatures are replaced per generation instead of the intended ~484** — convergence is drastically slower than the algorithm was designed for. This should be fixed, not silently carried into v2.

**Breeding (`World.makeNewGeneration`)**, for each slot `i` in `[lowerCutStart, size)`:
1. Increment the generation counter (once per call, up front).
2. Pick two parent indices `a`, `b` via `pickParentPos()`.
3. If `a != b`: true crossover — `new Creature(generation, parentA, parentB)` calls `MatchNode.mate()` on all four top-level trees (Match/NotMatch/Twin/Missing). The exact field-by-field crossover rule lives in the **compiled `mismo-match` jar**, not visible from this repo (see `matching-engine.md` §11).
4. If `a == b` (same elite picked twice): asexual — clone the one parent, then `tweak()` it.
5. **Dedup guard:** if the new creature's `hashCode()` collides with any existing creature's, the baby is discarded — and the old occupant of slot `i` is simply left in place, unreplaced (not retried). So in a given generation, some "replacement" slots may silently not get replaced.

**⚠ Bug: `Creature`'s clone constructor clones the `Missing` tree from the parent's `Twin` tree** (`patientCompare.getMissing().clone(clone.patientCompare.getTwin())` — should reference the parent's `getMissing()`). A genuine copy-paste bug corrupting asexual reproduction.

**Main loop**: `World.run()` (since `World extends Thread`) does `scoreAndSort()` once, then `while (keepRunning) { makeNewGeneration(); scoreAndSort(); }` forever. **There is no built-in stopping condition** (no target score, no generation cap) — only external `setKeepRunning(false)` (the `exit` CLI command, or the `stop` checkbox in `GenerateWeightsServlet`) ends a run. `scoreAndSort()` only rescopes unscored creatures, sorts descending by score, and publishes the `creaturesCopy` snapshot plus a `scoreRate` (creatures/sec) statistic.

**Randomness is not reproducible**: `World`, `Transformer`, and `Typest` each hold their own unseeded, default-entropy `java.util.Random` — no seed is configurable anywhere, so two runs of the same config never produce the same sequence.

### `Transform`/`Transformer`/`Typest` — a separate system, not GA mutation

These classes (`src/main/java/org/immregistries/mismo/trainer/random/`) are **not** part of the weight-value mutation machinery. They are the **synthetic patient/test-data generator**: `Transformer` builds fake `Patient` records (names, addresses, DOBs, vaccination history) by reading a small templating mini-language out of `src/main/resources/transform.txt` — plain `FIELD=[TOKEN]` lines, plus a `~NN%[A]:[B]` "sometimes" operator for probabilistic value choice (e.g. pick option `A` NN% of the time, else `B`) — and includes helpers to derive a "close match" or "twin" variant of an existing patient for negative/edge-case generation, informed by real-world twin-birth-rate statistics (`makeBirthCount()`). `Typest` then deliberately corrupts/degrades those records to model a specific "typist"/data-entry persona (data-quality tiers `IDEAL` down through `BAD` — i.e. how much of the true data actually got recorded), layering on named data-quality problems via a `Condition` enum (address typos, swapped DOB digits, missing guardian name, shared SSN/MRN between patients, hyphenated-name variants, keyboard-adjacency typos via a `makeTypo()` keyboard-neighbor map, etc.).

This is what actually powers `RandomServlet`/`RandomScriptServlet`/`RandomForCDCServlet` (see `trainer-pages.md`) and is almost certainly how the ~9,000-row production `match_item` corpus was originally generated — the `S-`/`D-` label prefixes observed in `data-model.md` §2.3 (Same-person / Different-person) plus a `Typest.Condition` code and a `Typest.Type` data-quality pair correspond 1:1 to this generator's output. The genuine per-node weight mutation (`makeRandom`/`tweak`/`mutate`/`mate`/`clone`) lives inside `MatchNode` in the compiled jar — not visible as source here. `Transformer`/`Typest` are a genuinely strong, reusable asset (realistic synthetic patients, named data-quality-problem injection, real-world twin-rate statistics) currently wired only into a handful of "Random*" debug servlets — worth promoting into a first-class, discoverable part of the v2 test-authoring workflow rather than a side tool.

## 5. Launching `Island` (the CLI process)

Wired via `pom.xml`'s `exec-maven-plugin` (`<mainClass>org.immregistries.mismo.trainer.Island</mainClass>`), run with `mvn exec:java`. A plain synchronous console process, not a servlet.

**Startup sequence:**
1. Read a YAML config file — `args[0]` if given, else `island.yml` in the working directory. This repo's `island.yml`: `centralURL: http://localhost:8080/mismo-trainer-1.0/CentralServlet`, `testCaseFileName: src/main/resources/AIRA-2026-B.txt`, `worldName: Jacob Lake`, `islandName: Twix Bar`, `populationSize: 1000`, plus its own (disagreeing, see §3) `scoringWeights` block.
2. Require non-empty `worldName`/`islandName` or exit immediately.
3. `IslandSync.requestStartScript(...)` — POST to the central server asking for the current best `Configuration` for this world/island (silently falls back to the built-in default if the central server is unreachable, only printing a stack trace).
4. Build the `World` (§4's initialization).
5. Resolve the effective scoring weights (`island.yml`'s block wins if present, else whatever was embedded in the seed script).
6. Load the training set from the **local flat file** named by `testCaseFileName` (custom `TEST:`/`EXPECT:`/`PATIENT A:`/`PATIENT B:`/`DESCRIPTION:` text format) — **not a DB query**, unlike `TestSetServlet`/`MatchPatientServlet`, which do read `match_set`/`match_item`.
7. Start `IslandSync` and `World` as background threads.
8. Enter an infinite interactive console loop: print a status dashboard (population/generation/last-message/score-rate/sync-status/top-10 creatures/confusion table), then block reading a stdin command: `exit`, `sync` (force an immediate sync), `weights` (reprint the scoring table), `script` (print the #1 creature's YAML), `diagnose <test-label>` (re-run one named case through a fresh `PatientCompare` built from the #1 creature and print `printOut()` diagnostic detail).

(`darq_cli.log`, present at the repo root, is 0 bytes — it could not be used as evidence of real console output, despite its name suggesting it should hold some.)

## 6. The Island ↔ central-server protocol

Plain HTTP `application/x-www-form-urlencoded` POSTs, hand-built with raw `URLConnection`/`DataOutputStream` (no HTTP client library, no HTTPS, URL taken straight from `island.yml`). All three actions hit `CentralServlet`'s single `doPost`, disambiguated by an `action` parameter:

| Action | Direction | What happens |
|---|---|---|
| `requestStartScript` | Island → server (once, at startup) | Server looks up the latest `Configuration` for the exact `(worldName, islandName)`; if none, falls back to the latest for the whole `worldName` regardless of island (**cross-island seeding** — a brand-new island inherits the best result from any sibling island in the same world); if nothing at all, returns a fresh default config. |
| `update` | Island → server (every 10 minutes, and once on shutdown) | Uploads **only the single best creature's YAML**, and only if `world.getGeneration()` has advanced since the last sync. Server overwrites the existing latest row for that `(world, island)` in place (§2's history-loss finding). Responds `"OK"`. |
| `query` | Island → server | **Defined in `IslandSync` but never actually called anywhere in `Island.java`** — dead code, possibly a leftover from an earlier design. Would return the raw YAML for the exact `(world, island)` pair, or `"Not Found"`. |

Polling interval is a fixed 10 minutes (`wait(10*60*1000)`), interruptible via the `sync` CLI command.

**⚠ Security finding: `CentralServlet.doPost` — the entire island-sync protocol — has zero authentication.** Contrast with `CentralServlet.doGet` (the human dashboard), which does require a logged-in session. Anyone who can reach the URL can POST arbitrary YAML into the `configuration` table for any `worldName`/`islandName` they invent, or read back any world's current best solution, with no credential of any kind.

## 7. `GenerateWeightsServlet` — the deprecated in-browser path

Its own javadoc: *"This is the original optimization servlet. The recommendation now is to run this using the command line. This gives more control and feedback during the optimization process."*

It runs the identical GA machinery (`new World(...)`, `Creature`, `Scorer`) **directly inside the webapp process**, entirely disconnected from `Island`/`IslandSync`/`CentralServlet` — it never talks to `CentralServlet`. The `World` it creates is stored in **`ServletContext` attribute `"world"`**: application-scoped, shared by every user of that deployment, running on its own background thread once started by an ordinary GET request. It reads its training set from a hardcoded classpath resource, `MIIS-E2.txt`, not the DB. Every page load is a poll of that shared `World`'s current top-100 creatures; the page also **doubles as a live editor for the process-global `Scorer.weights` array** (§3's bug). A `stop` checkbox tears the shared `World` down.

**It does not implement any "promote this creature to production" action.** The closest related functionality is `WeightSetServlet` (load a configuration script into your own session for testing) and `CentralServlet.doGet`'s "Select" links (load a specific stored `Configuration` by ID) — both are inspection/testing actions, not a deploy/activate workflow. **This confirms a real gap the conceptual doc's §23 lifecycle diagram assumes exists ("Adopt/version configuration") but the code does not implement** — worth treating as a genuine missing feature for v2, not something to search harder for in v1.

## 8. Consolidated bug/risk list for v2

**Correctness bugs (high confidence):**
- `World.lowerCutStart = size - (lowerCut ^ 2)` — bitwise XOR instead of squaring; cripples per-generation replacement rate (§4).
- `Creature`'s clone constructor clones `Missing` from the parent's `Twin` tree (§4).
- Four disagreeing default scoring-weight sources (§3).
- `IslandSync.sendUpdate`'s javadoc describes a `start`/`end` range parameter that doesn't exist on the real method — stale documentation.
- `World.rescore` and `IslandSync.sendQuery()` are both dead code.

**Concurrency/global-state hazards:**
- `Scorer.weights`: mutable `static`, shared JVM-wide, mutated in place by two different code paths with no synchronization.
- `World.creatures[]` is read via an unsynchronized `getCreatures()` while a background thread mutates it — readers can observe a partially-rewritten array mid-generation.
- Two-plus independent, unseeded `Random` instances — no reproducibility, no configurable seed.
- Design assumes exactly one `World` per JVM (`IslandSync`'s own javadoc states this explicitly) — "Island" really means "one OS process, one population," with no multi-island-per-process support.

**Security:**
- `CentralServlet.doPost` (the entire island-sync API) has no authentication — arbitrary write access to the `configuration` table for anyone who can reach the URL.
- `HomeServlet`'s login is a plaintext password comparison (see `data-model.md` §1).

**Data/history loss by design:**
- The central server overwrites each `(world, island)`'s latest `configuration` row in place instead of inserting a new row per generation — no generation-over-generation history is ever retained centrally.

**No promotion/activation workflow exists** — a weight set can be inspected, loaded into a session, or downloaded as YAML, but there is no modeled concept of "this configuration is now the one Mismo-Match uses in production" anywhere in the trainer (§7).

**Hardcoded values:** `island.yml`'s `centralURL`/`testCaseFileName` are checked-in local-dev values with no environment override; `GenerateWeightsServlet` hardcodes its resource filename and default population size.

**Weak error handling:** an unreachable central server is swallowed silently (stack trace to stderr, execution continues) both at Island startup and on every subsequent sync attempt — no alerting, no operator-visible failure state beyond reading server logs.
