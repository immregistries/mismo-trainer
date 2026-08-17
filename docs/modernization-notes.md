# Modernization Notes — Conceptual Map for v2

**Status:** Synthesis document. Everything referenced here is grounded in `matching-engine.md`, `data-model.md`, `trainer-pages.md`, and `optimization-and-islands.md` — read those first for the evidence; this document is the "so what" layer: what v1 actually implements conceptually, where it diverges from the aspirational model in `mismo-conceptual-overview.md`, and what that suggests for v2 design decisions. Where a recommendation is genuinely a judgment call rather than a fact, it's flagged as such.

---

## 1. The conceptual model needs one addition: a fourth decision network

`mismo-conceptual-overview.md` §10 describes three decision networks: Match, Not-a-Match, Twin. **Code has four** — a `Missing` network sits alongside them, contributes to the final classification exactly as powerfully as the other two "downgrade" networks (`matching-engine.md` §7), and is fully present in `Configuration.yml` as a first-class top-level block. This isn't a v1 implementation detail to fix — it's a real conceptual piece that the original design doc simply didn't capture. **Recommendation: adopt "four decision networks" (Match / Not-Match / Twin / Missing) as the corrected mental model going forward**, and decide deliberately whether v2 keeps "Missing" as a peer network (as now) or reframes it as a distinct concept (e.g., a confidence/completeness modifier applied after the other three vote) — the current code treats it identically to Not-Match/Twin (any one of the three firing downgrades Match → Possible Match), which conflates "these look like different people" with "we don't have enough information to tell." Those are semantically different reasons for the same output category, and a v2 UI/API surfacing *why* something is a Possible Match would benefit from distinguishing them.

## 2. The scoring math is a specific, undocumented design — decide whether to keep it

Three rules, all verified by bytecode, none written down anywhere before this snapshot (`matching-engine.md` §4–§6):

1. `weightScore = rawScore * (max - min) + min` — linear rescale per node.
2. A group's raw score = sum of enabled children's `weightScore`, **capped at 1.0, floored to 0.0 below 0.5**.
3. A network "fires" at `weightScore >= 0.5`.

This is a reasonable, explainable design (consistent with conceptual doc §25.4's "matching should remain explainable" principle), but it's also somewhat ad hoc — the `0.5` floor-and-fire threshold is a magic number repeated at every level of the tree, `minScore` is unused in the shipped config (always `0.0`), and the whole scheme was apparently never written down for anyone tuning `Configuration.yml` by hand. Two honest options for v2, not a recommendation either way without more input:

- **Preserve it as-is**, but document it explicitly (this document + `matching-engine.md` now do that) and make the `0.5` thresholds configurable rather than hardcoded, since `Configuration.yml` already externalizes `minScore`/`maxScore` per node — the fire/floor thresholds are the one piece of "weight" that isn't actually in the YAML.
- **Replace it** with a more standard technique (e.g. a properly calibrated logistic/probabilistic combination) — bigger change, breaks the "explainable, not a probability" design principle the conceptual doc explicitly wants preserved (§25.3), so this would be a deliberate philosophical shift, not a drop-in improvement.

## 3. "Training set" and "test set" are already unified — the conceptual doc's open question is answered

§18's question ("are these the same concept?") — yes, one table, `match_set`, no distinction. What's actually missing (per §24.3's stated goal) is everything *around* that flat concept: no status (draft/reviewed/locked), no versioning, no way to say "this set was frozen for evaluating configuration X." **If v2 wants training sets to be a real, trustworthy unit of measurement** (which the broader patient-matching measurement program, out of scope here, presumably needs), that lifecycle needs to be added — it doesn't exist to be discovered, it needs to be designed.

## 4. Weight sets have no promotion/activation concept — this is a genuine gap, not a missing page

Traced exhaustively across every servlet (`optimization-and-islands.md` §7, `trainer-pages.md`): a `configuration` row can be created (by an Island sync or `GenerateWeightsServlet`), loaded into a session for inspection/testing (`WeightSetServlet`, `CentralServlet`'s Select links), and downloaded as text — but **nothing in v1 marks a configuration as "the one Mismo-Match should actually use."** There's no `status` column, no "current production configuration" pointer, no audit trail of who promoted what when. Conceptual doc §23's lifecycle diagram assumes an "Adopt/version configuration" step exists; it doesn't. **This is probably the single most important missing piece for v2** if the goal (per §24.3) is for Mismo-Trainer to become the authoritative source of the configuration actually running in production, rather than a side tool whose output gets manually copied somewhere else.

Related: history is actively *destroyed* by the current sync protocol (each island's row is overwritten in place rather than versioned — `optimization-and-islands.md` §2, §6). A v2 schema should treat every `(worldName, islandName, generation)` as an immutable, insert-only row, with a separate explicit "active configuration" pointer/table — never mutate history to represent "current state."

## 5. Two coexisting matching libraries — a decision point, not just tech debt

`org.immregistries:mismo-match:1.1` (current) and `org.immregistries:patientmatch:1.6.03` (legacy) are both live dependencies, deliberately exercised side by side by the "Original"-suffixed servlets (`trainer-pages.md` §0, §3). This isn't leftover cruft to delete reflexively — it's an intentional old-vs-new regression comparison capability. **Before removing anything "Original," confirm with the maintainer whether that comparison capability is still needed** (e.g., is the legacy library still deployed anywhere in production that this comparison protects against regressing?). If it's no longer needed, retiring it deliberately (and dropping the `patientmatch` dependency) is a legitimate v2 simplification — just not one to do by accident while cleaning up "duplicate-looking" servlets.

## 6. Security posture needs a full pass before any production exposure

Everything below is independently verified, not inferred (see `data-model.md` §1, `trainer-pages.md` §4, `optimization-and-islands.md` §6):

- **A live database password is committed to source control** (`hibernate.cfg.xml`, `src/db/create-user.sql`) — rotate immediately, regardless of v2 timeline, since it's in git history and the account has `GRANT OPTION`.
- **User passwords are stored and compared in plaintext.**
- **The machine-to-machine Island-sync API (`CentralServlet.doPost`) has no authentication at all** — anyone who can reach it can write arbitrary weight-set YAML into the database or read back any world's current best result.
- **No CSRF protection, no HTML-escaping (persistent XSS is plausible essentially everywhere), inconsistent/UI-only auth gating** across the 21 servlets — most are reachable and fully functional by direct URL with zero login.

Conceptual doc §24.1 already targets InteropHub SSO as a modernization goal — that's the right direction, but note it only solves *authentication*. It does nothing by itself about the missing authorization model (no roles — everyone who can log in can do everything), the unauthenticated island-sync channel (a *service* credential problem, not a *user* SSO problem), or the XSS/CSRF gaps (framework/output-encoding problems). Treat SSO as one of at least four separate security workstreams, not a single fix.

## 7. Known correctness bugs to fix, not port forward

From `optimization-and-islands.md` §4, §8 — concrete, high-confidence, independent of any redesign decision:

- `World.lowerCutStart = size - (lowerCut ^ 2)` (bitwise XOR instead of squaring) — silently cripples the evolutionary algorithm's convergence speed to ~2% of its intended per-generation replacement rate.
- `Creature`'s clone constructor clones the `Missing` tree from the parent's `Twin` tree.
- Four different files disagree on the default 3×3 scoring-weights matrix (`Scorer.java`'s code, `Scorer.java`'s own javadoc, `Configuration.yml`, `island.yml`) — pick one canonical source.
- `DownloadHl7Servlet`'s HL7 message-ID counter is declared but never incremented (guaranteed ID collisions), and the servlet queries the empty `patient` table so it currently produces near-nothing regardless.
- `RandomScriptServlet`'s "different"-pair pass/fail column appears to reuse the "same"-pair's comparison result.

## 8. Architectural pattern to leave behind, not carry forward

The raw-servlet-plus-string-concatenated-HTML architecture (no templating, no framework, hand-rolled Hibernate session management duplicated three different ways across files — `trainer-pages.md` §0, §4) was reasonable for its era but is the primary source of the class of bugs found here (leaked sessions, NPEs on missing session state, duplicated markup bugs, dead copy-pasted JavaScript). A v2 rewrite on a modern framework (matching the rest of the AIRA suite, per conceptual doc §24.2) would eliminate most of §7's "cross-cutting" findings as a side effect of the platform change, not through individual fixes.

## 9. Regularized terminology for v2 (resolves conceptual doc §24.4)

Grounded definitions, now that the code has been read:

| Term | v1 reality |
|---|---|
| **Detector** | A leaf `MatchNode` (e.g. `ExactMatchNode`, `SimilarMatchNode`) — compares one or two `Patient` fields and returns a raw `[0,1]` score. Fixed set of ~18 classes, not user-extensible without a code change. |
| **Signal / Signal group** | A `MatchNode` — this is a broader term than "detector": in code, *every* node (leaf or group) is a `MatchNode` and exposes the identical `weightScore`/`hasSignal` contract. An `AggregateMatchNode` is a signal group that sums its children. There's no separate "signal" class distinct from "detector" — the conceptual doc's two-tier detector→signal language maps onto one recursive class hierarchy in code. |
| **Decision network** | One of the four top-level `AggregateMatchNode` trees: Match, Not-Match, Twin, Missing (§1 above). |
| **Weight set / Configuration** | One YAML document (`worldName`/`islandName`/`generation`/`scoringWeights`/the four trees), persisted as one `configuration` DB row's `configuration_script` blob. No first-class "active/production" status (§4). |
| **Steering weights** | The `scoringWeights` 3×3 block — used only by `Scorer` to rank candidate weight sets during optimization; has no effect on `PatientMatcher.match()` itself. |
| **Test case** | One `match_item` row: two serialized patients + an expected status + provenance metadata. |
| **Training/test set** | One `match_set` row — a flat, unversioned, unstatused bag of `match_item`s (§3 above). |
| **Optimization run** | One `World` instance's lifetime — in-memory only, not itself a persisted/named entity; only its best `Creature` snapshots reach the DB, and only intermittently. |
| **Island** | One OS process (`Island.main`) running exactly one `World`, syncing its best `Creature` to `CentralServlet` every 10 minutes. |
| **Match signature** | A four-bit-plane-encoded string derived from every node's score across all four networks, prefixed by a hash of the configuration that produced it (`matching-engine.md` §9) — comparable only across signatures from the *same* configuration. |

## 10. Suggested next steps, roughly in dependency order

1. **Rotate the committed DB credential and stop storing user passwords in plaintext** — no reason to wait for a v2 rewrite.
2. **Decide the two open architectural questions** that block a clean v2 data model: (a) patient storage — normalize vs. keep serialized-string-in-`match_item` — and (b) whether weight-set history should be insert-only with an explicit "active configuration" pointer (recommended: yes).
3. **Fix the two concrete GA bugs** (`lowerCutStart`, the clone-constructor swap) if the evolutionary optimizer is being kept largely as-is rather than replaced.
4. **Decide the fate of the legacy `patientmatch` comparison path** (§5) before treating any "Original" servlet as dead code.
5. **If v2 needs to touch detector/signal/signature internals directly** (not just call the existing `mismo-match` API), get access to the `mismo-match` source repository — this snapshot's understanding of that library is thorough at the level of "what does it do and how is it structured" (verified via targeted bytecode tracing) but does not include full decompiled source for every method (`matching-engine.md` §11).
