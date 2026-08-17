# Mismo-Match Engine Internals (v1 Snapshot)

**Status:** Verified against compiled bytecode. The Mismo-Match engine (`org.immregistries:mismo-match:1.1`) is **not present as source in this repo** — it is a compiled Maven dependency (`~/.m2/repository/org/immregistries/mismo-match/1.1/mismo-match-1.1.jar`). Everything in this document was recovered by decompiling/disassembling that jar with `javap -p -c` (method signatures fully verified; several method *bodies* — the exact final decision arithmetic, the weight-rescale formula, the aggregate-summation rule, and the final Match/Possible/Not-a-Match branch — were fully traced instruction-by-instruction and are stated here as fact, not inference). This resolves nearly every "Verify in code" item in `mismo-conceptual-overview.md` §8–§13.

A second, older library, `org.immregistries:patientmatch:1.6.03` (package `org.immregistries.pm.*`), is also a live dependency and is exercised in parallel by the trainer's "Original" servlets for old-vs-new comparison — see `trainer-pages.md`. It was not separately decompiled; assume it has a similar but not necessarily identical architecture.

---

## 1. Package layout

```
org.immregistries.mismo.match
├── App                              (trivial CLI stub, unrelated to the servlet app)
├── PatientMatcher                   ← the public entry point
├── PatientCompare                   ← per-comparison working object / decision logic
├── PatientMatchResult               (determination + signature list)
├── PatientMatchDetermination        enum: MATCH | POSSIBLE_MATCH | NO_MATCH
├── MatchSignature / MatchSignatureType   (PRIMARY | SECONDARY | TERTIARY)
├── StringUtils                      (isEmpty/isNotEmpty helpers)
├── matchers/
│   ├── MatchNode                    ← abstract base: every detector/group extends this
│   ├── AggregateMatchNode           ← a group node (has children, sums their weightScore)
│   ├── ExactMatchNode, SimilarMatchNode, HyphenMatchNode, AtLeastOneExactMatchNode,
│   │   MissingMatchNode, SameInitialMatchNode, SimilarDateMatchNode,
│   │   DoNotConflictMatchNode, OneTypoMatchNode, HistoryMatchNode   ← leaf detectors
│   ├── AddressMatchNode, AddressStreetNameMatchNode, AddressStreetNumMatchNode
│   └── BirthOrderMatchNode, BirthNotIndicatedMatchNode, BirthHasTwinMatchNode, BirthMayBeTwinMatchNode
├── model/
│   ├── Patient, Address              (see data-model.md §4)
│   ├── MatchItem, MatchSet, User     (the Hibernate-mapped DB entities)
│   └── Configuration                 ← parses/holds the YAML weight tree
└── util/
    └── ReverseSignatureUtil          (decodes a signature string back into per-node scores)
```

## 2. Public API

```java
PatientMatcher matcher = new PatientMatcher();                 // or new PatientMatcher(InputStream configYaml)
PatientMatchResult result = matcher.match(patientA, patientB);
result.getDetermination();     // MATCH | POSSIBLE_MATCH | NO_MATCH
result.getMatchSignatureList();
String sig = matcher.generateSignature(patientA, patientB);    // signature without a full match() call
```

**Statefulness / thread-safety (resolves conceptual doc §11, §13):** `PatientMatcher` is **not stateless and not safely reusable across concurrent calls.** It holds a single internal `PatientCompare` instance; `match(A,B)` calls `patientCompare.setPatientA(A)`/`setPatientB(B)`, reads the result, and then calls `patientCompare.clear()` before returning. Two threads sharing one `PatientMatcher` instance and calling `match()` concurrently would race on that shared `PatientCompare`. **A v2 integration should create one `PatientMatcher` per thread/request, or add explicit synchronization — the class does none of this itself.**

Determinism and symmetry (conceptual doc §11) were **not independently proven** by decompilation (that would require exercising the code, not just reading it), but nothing in the traced logic depends on comparison order, wall-clock time, or global mutable state *within `PatientCompare`/`MatchNode` itself* — the algorithm as read is a pure function of `(PatientA, PatientB, Configuration)`. The one caveat: `MatchNode` declares a `protected static java.util.Random random` field, present on the base class — if any leaf detector's `score()` consults it (not confirmed either way from the signatures alone), determinism would not hold. Recommend an explicit test in v2 rather than assuming.

## 3. The four decision networks (corrects conceptual doc §10)

The conceptual doc names three networks: Match, Not-a-Match, Twin. **Code shows four**: `PatientCompare` holds `AggregateMatchNode match`, `notMatch`, `twin`, **and `missing`**. `Configuration` mirrors this with a fourth top-level YAML block, `Missing:` (see `Configuration.yml`), covering things like "how much address/name data is absent between the two records." The "Missing" network is not cosmetic — it directly participates in the final decision (§5).

## 4. How a node's score becomes a "weighted" score

Every node (leaf detector or `AggregateMatchNode` group) extends `MatchNode`, which carries `minScore`/`maxScore`/`enabled`/`not` and this rescale, verified by disassembly:

```
weightScore(A, B) = score(A, B) * (maxScore - minScore) + minScore
```

i.e. a node's raw `[0.0, 1.0]` comparison score is **linearly rescaled into the node's configured `[minScore, maxScore]` window** before being handed up to its parent. This is the exact mechanism the conceptual doc's §9 asked to have verified: min/max aren't a clamp, they're a linear map — `maxScore` is effectively "how much this signal can contribute at most," and `minScore` is "the floor it contributes even at a raw score of 0" (in the shipped `Configuration.yml`, `minScore` is `0.0` on every single node, so in practice today `minScore` is unused/always-zero — worth flagging as either dead configuration surface or an intentionally-reserved future dial).

## 5. How an `AggregateMatchNode` (a signal group) combines its children

Disassembly of `AggregateMatchNode.score(A,B)`:

```
sum = Σ over enabled children of child.weightScore(A, B)
if (sum > 1.0) sum = 1.0
if (sum < 0.5) sum = 0.0        ← undocumented floor, not mentioned anywhere in Configuration.yml or the conceptual doc
return sum
```

That group's own `score()` then itself goes through the same `weightScore()` rescale (§4) against *its own* `minScore`/`maxScore` before being summed into *its* parent — the network is recursive and homogeneous (a leaf and a group obey the identical `weightScore` contract, which is why the tree in `Configuration.yml` can nest arbitrarily, e.g. `Match → Household → Last Name → L-match`).

**The `< 0.5 → 0.0` floor is a real, previously-undocumented rule**: a signal group (e.g. "Last Name," "Household," or one of the four top-level networks) contributes **nothing at all** to its parent unless its own enabled children's summed weighted evidence reaches at least 0.5 — there is no partial credit below that line. This is a significant, code-only fact for anyone tuning `Configuration.yml`'s min/max values, and should be called out explicitly in any v2 rewrite of the scoring engine (whether it's kept as-is, made configurable, or replaced).

## 6. `hasSignal` — the threshold that actually drives the final decision

```
hasSignal(A, B) := weightScore(A, B) >= 0.5
```

Defined once on the base `MatchNode` class and inherited unchanged by `AggregateMatchNode` — so a top-level network (Match/NotMatch/Twin/Missing) "fires" exactly when its own final rescaled score is `>= 0.5`.

## 7. Final classification logic (fully resolves conceptual doc §10)

Disassembly of `PatientCompare.getResult()`, transcribed to pseudocode:

```java
boolean matchSignal    = match.hasSignal(A, B);
boolean notMatchSignal = notMatch.hasSignal(A, B);
boolean missingSignal  = missing.hasSignal(A, B);
boolean twinSignal     = twin.hasSignal(A, B);

if (matchSignal) {
    if (!notMatchSignal && !missingSignal && !twinSignal) {
        result = "Match";
    } else {
        result = "Possible Match";
    }
} else {
    result = "Not a Match";
}
```

In words: **the Match network must fire, AND none of Not-Match / Missing / Twin may also fire, for a clean `MATCH`.** If the Match network fires but *any* of the other three also fire, the result is downgraded to `POSSIBLE_MATCH` — never overridden to `NOT_A_MATCH`. If the Match network doesn't fire at all, the result is `NOT_A_MATCH` regardless of what the other three networks say (their outputs are simply never consulted in that branch).

This means the **Missing network, which the conceptual doc doesn't mention as decision-relevant at all, is exactly as powerful as the Not-a-Match or Twin networks at forcing a downgrade from Match to Possible Match** — e.g., if enough address/name fields are absent between the two records to cross the Missing network's own internal 0.5 threshold (§5, §6), a pair that would otherwise cleanly Match gets downgraded to Possible Match. This is a first-class, code-verified behavior that should be explicitly retained, renamed, or reconsidered in v2 — not silently dropped because it wasn't in the original conceptual model.

There is no tie-breaking/boundary-value special case beyond the plain `>=` comparisons above — `weightScore == 0.5` exactly counts as "has signal."

## 8. Detector catalog (leaf `MatchNode` subclasses)

All of these take a `fieldName` (and sometimes `fieldName2`/`fieldName3`/`fieldNameOther`/`splitParameter`) from `Configuration.yml` identifying which `Patient` field(s) to compare, plus a `not` flag. **`not` inverts the raw score before it's used**, verified via `MatchNode.ifTrueOrNot(boolean, double)`:

```
ifTrueOrNot(conditionMet, value) = conditionMet != node.not  ?  value  :  (1.0 - value)
```

i.e. when `not: true` in the YAML, the detector reports evidence *of difference* instead of evidence *of sameness* — this is how the same `ExactMatchNode`/`SimilarMatchNode` classes power both the "Match" tree (`not` absent/false) and the parallel "Not Match"/"Twin" trees (`not: true`) in `Configuration.yml` without any code duplication.

| Detector class | What it does (verified) |
|---|---|
| **ExactMatchNode** | `fieldName` values equal (case-sensitive `.equals`, per bytecode) → `1.0` via `ifTrue`, else `0.0`. If `fieldNameOther` is set, compares `patientA.fieldName` against `patientB.fieldNameOther` instead (cross-field match, e.g. `L-first`: A's last name vs. B's first name — catches swapped-field data-entry errors). |
| **SimilarMatchNode** *(extends ExactMatchNode)* | If either value is empty, or the two values are already equal (case-insensitive), returns `0.0` (defers entirely to `ExactMatchNode`'s exact-match detection — similarity only fires on genuinely *different but close* strings). Otherwise returns `com.wcohen.ss.JaroWinkler.score(A,B)` run through `ifTrue` — i.e. the raw continuous Jaro-Winkler string-similarity score, only for non-identical, non-empty pairs. |
| **HyphenMatchNode** *(extends ExactMatchNode)* | Compares `fieldName` against a hyphenated-name variant. |
| **AtLeastOneExactMatchNode** *(extends ExactMatchNode)* | Splits `fieldName`'s value by `splitParameter` (e.g. `"|"` for `mrns`) into arrays for A and B, and checks whether **any** element of A's array exactly matches **any** element of B's array — used for multi-valued fields like MRNs where a patient may have several historical medical-record numbers. |
| **MissingMatchNode** *(extends ExactMatchNode)* | Returns `1.0` if **either** patient's `fieldName` value is empty string, else `0.0` — a pure presence/absence check, no comparison between A and B's *values* at all. This is the building block of the entire "Missing" network (§3, §7). Has 3- and 5-arg constructors supporting `fieldName`/`fieldName2`/`fieldName3` for fields with alternate/legacy column names (e.g. address fields duplicated across `addressStreet1`/`address2Street1`/`address3Street1`). |
| **SameInitialMatchNode** *(extends ExactMatchNode)* | Fires when comparing a full name value against a single-character "initial" value — requires **at most one** side to have length > 1 (both sides being full multi-character names returns `0.0` outright, i.e. this detector specifically targets the "middle name" vs. "middle initial" mismatch case, not general name comparison), then compares the first character (uppercased) for equality. |
| **SimilarDateMatchNode** *(extends ExactMatchNode)* | Date-of-birth "close but not exact" detector — used alongside plain `ExactMatchNode` on `birthDate` (`DOB-similar` in `Configuration.yml`); presumably tolerant of transposed digits/off-by-a-few-days, consistent with catching typo'd DOBs, though the exact tolerance window was not traced to the instruction level. |
| **DoNotConflictMatchNode** *(extends ExactMatchNode)* | Returns `1.0` (treated as "no conflict") if **either** value is empty — i.e. missing data is *not* treated as a mismatch here, unlike `MissingMatchNode` which treats it as its own signal. Otherwise `1.0` if the values match (case-insensitive) via `ifTrueOrNot`, else presumably `0.0` for a genuine conflict. Used for fields like `nameSuffix` where the absence of a suffix shouldn't itself count against a match, but an actual clash (`Jr.` vs `Sr.`) should. |
| **OneTypoMatchNode** | Fires when two strings are identical except for exactly one character difference (`hasThreeOrMoreCharacters`/`sameButForOne` helper methods) — a single-edit-distance detector, cheaper/simpler than full Jaro-Winkler similarity. (Class name in the jar is `OneTypoMatchNode`; a related concept, "SameButOne," is referenced in this repo's own recent commit history — `8dce639 Added SameButOne` — suggesting active work on this family of detectors around the time of this snapshot.) |
| **HistoryMatchNode** *(extends ExactMatchNode)* | Used for `shotHistory` — exact/history-based comparison, not further disassembled. |
| **AddressMatchNode** | Builds a `List<Address>` per patient (supporting the 3-address model — see `data-model.md` §4) and computes a Jaro-Winkler-based address similarity; has dedicated logic to extract a "street number + apartment" composite for comparison. |
| **AddressStreetNumMatchNode** / **AddressStreetNameMatchNode** | Finer-grained address sub-detectors — number-only and street-name-only comparisons, each building their own A/B address lists. |
| **BirthOrderMatchNode**, **BirthNotIndicatedMatchNode**, **BirthHasTwinMatchNode**, **BirthMayBeTwinMatchNode** | The "Twin" network's specialized detectors, working off `birthOrder`/`birthStatus`/`birthType` fields to flag likely-multiple-birth scenarios (siblings born the same day, e.g. twins, who are often near-duplicates by every other field). |

### `AggregateMatchNode` (the group/container node)

Not a leaf — has a `List<MatchNode> matchNodeList` and its own `score()` = the sum-cap-floor rule in §5. Also exposes `populateMatchNodeListAndScoreMap`, `populateScoreList`, and `printOut` (a diagnostic dump used by `Island`'s `diagnose` CLI command and the trainer's inspection pages).

## 9. Signature generation (resolves conceptual doc §12)

Traced via disassembly of `PatientCompare.getSignature()` and `MatchNode.getSplitParameter`/`Configuration.getHashForSignature()`:

1. The signature string starts with `configuration.getHashForSignature()` — a short hash (via `generateShortHash`, SHA-based per the method signature) computed once per loaded `Configuration`, identifying *which weight-set version* produced this signature (so signatures from different configurations are visibly distinguishable and not comparable as if from the same "vocabulary").
2. `getScoreList()` walks **all four** top-level networks (`match`, `notMatch`, `twin`, `missing`) via `populateScoreList`, collecting **every individual node's raw score** (not just leaves — group nodes' scores are included too) into one flat ordered `List<Double>`, in tree-declaration order.
3. For each score in that list: `bucket = (int)(score * 15.0)` — i.e. each `[0.0,1.0]` score is quantized into a 4-bit value (`0–15`, printable as one hex digit).
4. Those 4 bits are **not** appended as a single hex digit into one string. Instead, the algorithm builds **four separate accumulator strings** by peeling off the bucket's bits one at a time (`bucket & 1`, then `bucket >>= 1`, repeated 4×) — bit 0 (least significant) goes into accumulator "6", bit 1 into accumulator "5", bit 2 into "4", bit 3 (most significant) into accumulator "3" — across **every node's score in the list**, before moving to the next score.
5. Each of the 4 accumulator strings is passed through a `collapse(String)` post-processing step (not traced to the instruction level, but named and positioned to perform run-length/redundancy reduction) and then colon-joined into the final signature, prefixed by the configuration hash.

**Net effect (confirms and sharpens the conceptual doc's "progressive generalization" description in §12):** the signature is not one flat hash — it is **four parallel bit-planes of the same underlying per-node score list**, from most-significant-bit (coarsest, "did this signal register roughly true/false at all") down to least-significant-bit (finest, small variations within a signal's strength). Two patient pairs with very different exact scores but the same coarse Match/NotMatch/Twin/Missing "shape" will collapse to the same or very similar leading signature segments even though their trailing segments differ — this is the mechanism behind "the left side is more general, the right side is more specific" described in the conceptual doc, now grounded in exact bit-plane semantics rather than described only by analogy.

`ReverseSignatureUtil.reverseSignatureIntoHexScores(String)` performs the inverse operation (`PatientCompare.setSignature(String)` calls it) — parsing a signature string back into a list of per-node scores, each hex digit `d` mapped back to a score via `d==15 ? 1.0 : (d==0 ? 0.0 : (d + 0.5) / 15.0)` (this exact mapping *was* fully traced from `setSignature`'s bytecode). This reverse path is what powers `SignatureServlet` (paste a signature, see the implied per-node scores) without needing an actual patient pair.

## 10. Configuration loading (`org.immregistries.mismo.match.model.Configuration`)

- Constructible from a file path `String`, an `InputStream`, or the parameterless default (built-in fallback configuration compiled into the jar — source/content not visible from this repo).
- Parses the YAML into the four `AggregateMatchNode` trees (`readAggregateMatchNode`/`readMatchNode`, recursive descent keyed on the presence of a `detector:` field to distinguish a leaf from a group) plus `scoringWeights` (parsed into both an `int[9]`-shaped `int[][]` and a `Map<String,Integer>` keyed by the 9 named constants `SHOULD_MATCH_MATCHES` etc.) and the identity fields (`worldName`, `islandName`, `generation`, `generationScore`, `generatedDate`).
- `createConfigurationScript()` / `toString()` — the serialize-back-to-YAML path, used everywhere a `Creature`/weight-set needs to be turned back into text for storage or transmission (see `optimization-and-islands.md`).
- `getPatientFieldSet()` — introspects the loaded tree and returns the set of `fieldName`s actually referenced by *enabled* nodes — i.e. Mismo can tell you which patient fields a given configuration actually looks at, useful for a v2 "what does this weight-set need as input" feature.

## 10a. Which `Patient` fields the shipped `Configuration.yml` actually uses

The `Patient` class defines 25+ fields (§ per `data-model.md` §4), but the configuration that ships with this repo only wires a subset of them into *enabled* detectors:

| Status | Fields |
|---|---|
| **Used by an enabled detector today** | `nameFirst`, `nameMiddle`, `nameLast`, `birthDate`, `phone` (all four networks); `gender` (Not Match/Missing only — disabled in Match/Twin); `motherMaidenName` (Not Match only); `addressStreet1/2`, `addressCity`, `addressState`, `addressZip` (via `MissingMatchNode`'s fallback chain and `AddressMatchNode`) |
| **Defined with a detector, but that detector/group is `enabled: false`** | `nameAlias`, `nameSuffix`, `guardianNameFirst`, `guardianNameLast`, `mrns`, `ssn`, `medicaid`, `birthStatus`, `birthType`, `birthOrder`, `shotHistory` |
| **Never referenced in `Configuration.yml` at all** | `nameLastHyph` (unused — `HyphenMatchNode` splits `nameLast` itself, not this field), `motherNameFirst`, `motherNameLast`, `motherNameMiddle`, `fatherNameFirst`, `fatherNameLast`, `race`, `ethnicity`, `vacName`, `vacDate`, `vacCode`, `vacMfr`, and the address2/address3 blocks beyond what `MissingMatchNode`'s fallback chain reaches |

This confirms conceptual §5.2's "current measurement use of Mismo uses only a subset of the available fields" claim concretely — roughly a third of the modeled patient concepts (guardian info beyond mother's maiden name, race/ethnicity, vaccination history, father's name) are present in the data model and even have detector classes ready, but are switched off in the configuration actually in use today. Also unwired into any current leaf: `OneTypoMatchNode`, `AddressStreetNumMatchNode`, `AddressStreetNameMatchNode` — detector classes that exist and compile but aren't referenced by any node in the shipped YAML.

## 11. What remains genuinely unverifiable without the mismo-match source

These are real gaps, not oversights — the relevant method *bodies* are compiled code this repo doesn't own:

- The exact Jaro-Winkler parameters/threshold tuning (library is `com.wcohen.ss.JaroWinkler`, a well-known third-party string-similarity implementation — its own algorithm is standard, but any wrapping logic beyond what's shown in §8 wasn't traced).
- `SimilarDateMatchNode`'s exact tolerance window for "close" dates.
- The `collapse()` string-reduction algorithm's exact rule (only its position/purpose in the signature pipeline was confirmed).
- `MatchNode.makeRandom()`, `.tweak()`, `.mutate(int)`, `.mate(MatchNode,MatchNode)`, `.clone(MatchNode)` — the actual per-node genetic-algorithm mutation/crossover mechanics that `Creature` (in the trainer, see `optimization-and-islands.md`) delegates to. Only their *existence and call sites* are confirmed; their internal math (mutation step size, distribution, crossover selection rule) is compiled code not present in this repo.

If v2 needs these details, the next step is either requesting the `mismo-match` source repository directly, or a full decompilation pass (e.g. with a Java decompiler like CFR/Procyon) rather than the signature-level `javap` disassembly used here — this snapshot went instruction-by-instruction only on the highest-value methods (the final decision, the weight math, the signature algorithm) rather than decompiling the entire jar to Java source.
