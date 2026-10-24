# Optimization Objectives and Scoring Profiles

**Status:** Authoritative statement of intent (2026-10-24, from Nathan Bunker). Read this before changing scoring weights, the default weight matrix, or anything that decides what the optimizer is steered toward.

**Why this document exists:** someone who knows IIS patient matching, looking at the default scoring matrix, will reasonably conclude it is a mistake. It is not. It encodes a deliberate measurement purpose that isn't visible from the code. Mismo can also be optimized for other purposes, and the software must keep that option open.

---

## 1. Two legitimate ways to use Mismo

### 1.1 National measurement (the current program)

Mismo is currently used as a **measuring instrument** for the Patient Matching at Rest program, not as a production matcher.

- Participating IIS run Mismo over their own data and report back signatures and counts, not patient records (see `mismo-conceptual-overview.md` §12.3).
- The quantity being measured is the **total number of potential matches**: pairs Mismo classifies as either Match or Possible Match. The distinction between Match and Possible Match is **not** critical and is not reported as a meaningful result.
- This is deliberate. The line between a true match and a possible match is very hard to draw, and at the national level we are **not taking responsibility** for that determination. Treating the two as one signal intentionally "poisons" the distinction, so participating IIS cannot read Mismo's output as "these pairs are certainly duplicates" versus "these pairs might be."
- The instrument is **expected** to report a certain baseline level of potential matches, including pairs that are not true matches, such as twins. What matters is comparing that rate across IIS:
  - a low potential-match rate suggests the IIS's own matching is working nominally;
  - an elevated rate points to a possible matching problem worth investigating.
- So the instrument's value comes from being **consistent across IIS**, not from being correct about any individual pair.

#### Deliberate design choices of the measurement instrument

These look like weaknesses from a production-matching point of view. They are intentional. Do not "improve" them in a measurement configuration.

1. **Over-identify on purpose; open the window wide.** Whatever Mismo classifies as Not a Match is invisible to the program: non-matches are not logged (there are far too many), so a missed potential match can never be found and corrected later. What *is* flagged comes back as a signature, and signature patterns that over-count can be tuned back out afterward. Errors in the inclusive direction can be recovered from; errors in the exclusive direction can't. So the instrument leans toward flagging.
2. **Keep the field set narrow and universal.** The measurement looks at the core fields every IIS has and populates well. The shipped configuration (`mismo-matcher.yml`; `matching-engine.md` §10a) uses:
   - **First, middle, and last name, date of birth, phone, and address** in all four networks (address through the address and missing-data detectors). Middle name is very helpful.
   - **Mother's maiden name** in the Not a Match network only. It can help, but it isn't always populated.
   - **Gender** in the Not a Match and Missing networks only, disabled in Match and Twin. It's easy to get but of little use for finding potential matches. It only halves the matching problem, and a gender mismatch is not a reliable not-a-match signal (data-entry errors, changes). Don't promote it to a strong signal.

   Fields that would make Mismo more decisive are left out deliberately even though detectors exist for some of them: an explicit twin/multiple-birth flag, sequential medical record numbers, records reported at nearly the same time, and so on. A tool tuned to those signals would work well in states that capture them and differently elsewhere, for reasons the program can't see, and the results would stop being comparable.
3. **Be less decisive than possible.** Because of (1) and (2), no IIS is expected to reach 0% possible matches. The expectation is a **healthy range**; a rate above that range is the signal worth investigating.
4. **Twins land in Possible Match, and that is acceptable, even useful.** With only the core fields, twins (same last name, DOB, address, phone; different first name) are very hard to tell apart, so they will often be flagged as Possible. The setup doesn't try to avoid that. It also gives the program a clear message for participants: *your Possible Match list should contain many twins, so do not use Mismo's Possible Match signal as a reason to merge records.*
5. **Guard against the measurement becoming the merge decision.** If an IIS used the measurement tool's output to decide merges, its measurement score would improve while the program quietly took over the IIS's own responsibility for matching. A tool that is deliberately not decisive, and visibly includes twins, resists being used that way.

**Mechanical note:** field scope is set by the **seed configuration**, not by the scoring profile. In `mismo-match`, a detector whose min and max are both 0 becomes `enabled: false`, and nothing in mutation or randomization turns it back on. Mating only inherits a parent's flag. The optimizer can therefore narrow the fields a configuration uses but never widen them. Keeping a measurement configuration narrow means starting from a narrow seed.

### 1.2 Production calibration (a future or other use)

Mismo was originally built to support an IIS doing operational matching. In that use the Match / Possible / Not a Match distinction matters a great deal:

- **Match** may drive an automatic merge, so a false Match (merging two different children) is usually the worst error.
- **Possible Match** goes to human review. That is safe but costs staff time, so it should be minimized.
- **Not a Match** on a true match leaves a duplicate record: harmful, but recoverable.

Mismo **can** be calibrated for this, and nothing in the software should prevent it. The current program simply does not take on that responsibility.

### 1.3 Other objectives

Research or experimental runs may want other trade-offs. Any objective is valid as long as it is **named, written down, and visible next to every score it produces** (§4).

---

## 2. How the objective is expressed in the optimizer

The optimizer has exactly two steering levers.

1. **Test Set expected labels.** Each test case's expected Match / Possible / Not a Match is what the optimizer tries to reproduce. Labels encode intent too: for example, whether a twin pair is labeled Possible Match or Not a Match.
2. **The scoring matrix** (the "steering weights"): nine point values, one per (expected, actual) combination. A candidate configuration's score is:

   ```text
   score = Σ count[expected][actual] × weight[expected][actual]
           ─────────────────────────────────────────────────────
           Σ count[expected][actual] × weight[expected][expected]
   ```

   That is, the fraction of the points available if every case landed on its own diagonal cell. The details are in `optimization-and-islands.md` §3.

The same configuration, Test Set, and engine give a **different "best" result under a different matrix**. Changing the matrix changes what the optimizer learns.

---

## 3. The national measurement matrix (current default)

The canonical default lives in `mismo-match`'s `mismo-matcher.yml` (`scoringWeights` block):

| Expected ↓ / Actual → | Match | Possible | Not a Match |
|---|---:|---:|---:|
| **Should Match** | 12 | 10 | −10 |
| **Should be Possible** | 8 | 10 | −8 |
| **Should Not Match** | −10 | −8 | 10 |

Read as a measurement objective, it is close to a **binary "potential match / not" score**:

- For a true match, answering Match (12) or Possible (10) earns nearly the same credit: either counts as "flagged."
- For a true possible, Match (8) and Possible (10) are again close.
- For a true non-match, flagging it as either Match (−10) or Possible (−8) costs about the same. Over-counting potential matches is penalized whether or not the result is labeled certain.
- Missing a real potential match (answering Not a Match, −10) costs slightly more than over-counting one (−8): a mild lean toward inclusion. The program's overall inclusive bias comes mostly from the narrow field scope and the engine's structure (§1.1), not from the matrix.

The small remaining preference for the diagonal (12 vs 10, 10 vs 8) is a mild tie-breaker, not a goal.

**Do not "fix" this matrix to punish false Matches or reward fewer Possible Matches** in order to make it look like a production-calibration matrix. That would silently change what the national measurement means and make new results incomparable with earlier ones. Create a separate profile instead (§4).

### Illustrative production-calibration matrix (not validated, not seeded)

For contrast only. The real values are a policy decision for whoever owns a production calibration:

| Expected ↓ / Actual → | Match | Possible | Not a Match |
|---|---:|---:|---:|
| **Should Match** | 10 | 3 | −5 |
| **Should be Possible** | −10 | 10 | −3 |
| **Should Not Match** | −30 | −2 | 10 |

Shape: false merge ≪ missed match < review cost < correct.

---

## 4. Rules for scores and weights

1. **A scoring matrix is a named, versioned Scoring Profile** with a stated purpose (`MEASUREMENT`, `PRODUCTION_CALIBRATION`, or `EXPERIMENTAL`) and a written description of intent. No anonymous numbers.
2. **Every score is shown with its profile name.** A score has no meaning on its own.
3. **Scores are only comparable within the same profile and the same Test Set snapshot.** "97% under measurement" and "91% under production calibration" are not better or worse than each other.
4. **Every configuration records the weights it was trained under.** Configuration scripts already embed a `scoringWeights` block, and the optimizer overwrites it with the run's profile weights.
5. **The default for new optimization assignments is the national measurement profile,** because that is the current program. Choosing any other profile is a visible, deliberate act.
6. **Editing a profile creates a new version**; existing runs keep pointing at the version they used.
7. **A measurement configuration keeps the narrow field scope** (§1.1). Enabling additional detectors (twin flags, MRNs, guardian, SSN, report timing, …) is a production-calibration or experimental change, never a measurement "improvement." Field scope is visible next to the profile wherever a run is shown.

How the Island Runner implements these rules is in `island-runner-implementation-plan.md` D6.

---

## 5. Notes

- **Twins (resolved 2026-10-24):** no single lever "makes" twins Possible Matches. They follow from the narrow field scope plus the inclusive lean (§1.1), and the engine's structure fits that: when the Twin network fires it can downgrade a Match to Possible, but never to Not a Match (`matching-engine.md` §7). Twins in Possible are an accepted, useful consequence, not a target.
- **The 2×2 view:** run and evaluation pages should show the 3×3 table collapsed to potential match vs not, because that is the quantity the measurement program uses (implemented per `island-runner-implementation-plan.md` D6).
