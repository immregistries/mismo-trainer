# UI/Functional Changes for the Proposed Functional Model

**Source:** `docs/proposed-functional-model-and-navigation.md`.
**Companion:** `docs/database-changes-for-functional-model.md` (Phases 10–13) — most of Track B below is gated on those landing.
**Status:** Living checklist, not a finished design — mirrors the role its database companion plays. Not everything here is equally ready to build; that's the point of splitting it into two tracks.

---

## 1. Two tracks, deliberately different in readiness

- **Track A — Navigation reorganization.** Ready to build now. No database dependency at all — it's a regrouping of pages that already exist.
- **Track B — Everything else.** A checklist of functional capabilities, each tagged with what it depends on and whether it's ready to design or still needs a real design pass.

This split matters on its own, separate from sequencing convenience: the navigation reorganization gives you (and anyone building against this) a stable mental map to slot future work into, independent of how much of Track B is built when — which is exactly what the functional doc means by "these top-level areas correspond to stable analyst concepts rather than individual implementation pages" (§3).

---

## 2. Track A — Navigation reorganization (do now)

### Current state (verified against `HomeServlet.java`/`web.xml`, not assumed)

The live top nav is five flat items: `Central | Configuration | Test Set | Review | Signature`. Several other pages exist and are reachable but aren't in the top nav at all (`GenerateWeightsServlet`, `RandomServlet`/`RandomScriptServlet`/`RandomForCDCServlet`, `ConvertDataServlet`, `AddressTestServlet`, `ExampleServlet`, `MatchNodeServlet`, `TestScriptExploreServlet`, `MatchPatientServlet`).

### Proposed mapping onto the five new top-level areas

| New area | Existing page(s) | Notes |
|---|---|---|
| **Test Sets** | `TestSetServlet`, `TestSetUploadServlet` | Direct fit — already "manage sets + review cases" today. |
| **Configurations** | `WeightSetServlet`, `CentralServlet`'s `doGet` dashboard | Direct fit. |
| **Evaluations** | `TestMatchingServlet`, `ReviewServlet` | Matches the functional doc directly: "the existing Mismo 'Review' functionality should conceptually move here" (§7.1). |
| **Signatures** | `SignatureServlet` | Direct fit. |
| **Optimization** | `CentralServlet`'s Island-facing parts, `IslandCredentialServlet` | Matches "Central Service... presented as infrastructure supporting Optimization" (§2.9). |

### Needs a placement decision

- **`MatchPatientServlet`** (single-pair diagnostic, full four-network breakdown) doesn't map cleanly onto one area — it's simultaneously "look at one Test Case" and "look at signature/network detail for one pair." Recommend: **Test Sets** (a specific pair lives there conceptually), with a link out to **Signatures** for the network-detail view, rather than duplicating the page under both.
- **Dev/diagnostic utilities** (`AddressTestServlet`, `ExampleServlet`, `ConvertDataServlet`, `MatchNodeServlet`, `TestScriptExploreServlet`, the `Random*` generators) don't fit the five-area analyst model at all — they're tools, not analyst workflow, and several are already flagged in `modernization-notes.md` as candidates to de-emphasize or drop. Recommend a small "Tools" catch-all (or fold into whichever area is closest, e.g. the `Random*` generators under Test Sets' "Add Cases") rather than forcing five clean buckets to also hold everything else.
- **`GenerateWeightsServlet`** is already scheduled for deletion in the (not-yet-built) template-organization phase — exclude it from this mapping rather than giving it a new home.

### Sizing

Pure navigation/menu restructuring, reusing `AiraPage`'s existing nav-item builder (already wired in Phase 4). No new servlets, no database changes, no changes to what any individual page does. This can be its own small, self-contained piece of work, done before or independent of anything in Track B.

---

## 3. Also ready now — not blocked by any database phase

Two more things stood out while reviewing the functional doc that need no schema work at all, worth doing alongside Track A rather than waiting for Track B:

- **Visual side-by-side diff** (§5.2) — same/different/A-only/B-only/missing highlighting between Patient A and Patient B. Purely computed from `patient_data_a`/`patient_data_b` at render time; the review page currently shows raw values with no highlighting at all. Real, self-contained, valuable improvement on its own.
- **Signature Inspector polish** (§9.2) — show populated fields dynamically instead of a fixed subset, emphasize non-zero Match/Missing values. Improves the existing `SignatureServlet`; no schema dependency.

---

## 4. Track B — checklist of remaining functional work

Grouped by what unlocks it. "Ready to design" means the functional doc already describes it clearly enough to scope directly; "needs more definition" flags where a real design pass is still needed beyond just having the database columns available.

### Unlocked by Phase 10 (`match_set` lifecycle/versioning)

- [ ] Lifecycle state UI (Draft/Reviewed/Approved badges + transition controls) — ready to design.
- [ ] Deep-copy ("create new version") — ready to design, but note the copy operation itself (every `match_item` copied with fresh review state, per the decisions already made) is real server-side work, not just a button.
- [ ] Version-family navigation ("show all versions of this Test Set") — ready to design now that `root_match_set_id` exists.
- [ ] Archive/restore — ready to design.

### Unlocked by Phase 11 (`match_item` provenance/notes/history) — and Phase 9's `is_reviewed`/`needs_review`

- [ ] Provenance badges + filtering in the case list (Manual/Imported/Generated/Signature Generated/Copied) — ready to design.
- [ ] Notes field on the review page — ready to design, small.
- [ ] "Needs further review" flag toggle — ready to design, small.
- [ ] Review history panel (past reviewer opinions) — ready to design.
- [ ] **Filtered review queues** (Next unreviewed / Not Sure / flagged / by signature group / from an Evaluation), with Previous/Next operating *within* the selected queue — **needs more definition.** The functional doc names the filters (§5.1) but not how queue selection persists across navigation. This is exactly the "session-state-driven navigation" pattern the v1 review already flagged as fragile (`TestSetServlet`'s current `ATTRIBUTE_MATCH_ITEM_LIST`/sublist-by-session-attribute approach) — worth deciding deliberately (URL parameters vs. session state) rather than extending the existing fragile pattern.

### Unlocked by Phase 12 (`evaluation`/`evaluation_result`) — the big one

- [ ] Start an Evaluation (select Test Set + Configuration, run, persist) — ready to design at a basic level.
- [ ] Evaluation summary view — ready to design.
- [ ] Disagreement/failure review, with a path back into Test Case Review (§7.4) — ready to design.
- [ ] **Signature-group analysis** within an Evaluation (§7.5) — **needs more definition.** Grouping potentially thousands of results by signature and surfacing "conflicting expectations within a group" is more than a list view; worth its own design pass.
- [ ] **Configuration comparison** (§8) — **needs more definition.** One of the richer asks in the doc — case-level improved/regressed lists, not just score deltas. Worth scoping once basic Evaluation exists and there's something real to compare.

### Unlocked by Phase 13 (`signature_batch`)

- [ ] Batch upload UI — ready to design.
- [ ] **Batch analysis view** (sort/filter/group by frequency, decode against a selected configuration) — **needs more definition.** Depends on the database design's choice to decode on demand rather than store results, which means a UI decision about how/when the analyst picks which configuration to decode against, and whether that choice is sticky across a session.
- [ ] Export to delimited file — ready to design, small.

### No new database work, but real design/build effort

- [ ] **Optimization analyst dashboard** (runs, islands, candidate configurations, best/recent results) — the database companion doc confirms this is queries over already-existing data (the insert-only `configuration` history + `island_credential`), but the dashboard itself doesn't exist and isn't trivial — **needs more definition** for exactly what's shown and how "runs" get grouped/displayed.

### Explicitly deferred by the functional model itself, not just by us

- [ ] Generate synthetic examples from a selected signature (§9.4) — the source doc itself calls this "future functionality."
- [ ] Configuration lifecycle/promotion/versioning — the source doc itself defers this (§6.2), matching the database companion doc's decision not to build a promotion flag.

---

## 5. Suggested order

1. Track A (navigation) + the two no-database quick wins (§3) — no dependencies, immediate payoff, worth doing together as one phase.
2. Phase 10/11 database work (`database-changes-for-functional-model.md`) alongside the "ready to design" Track B items that depend on them — Test Set lifecycle/versioning and the review-workflow improvements are the most directly requested functionality (this conversation started from wanting exactly the review/reclassify workflow).
3. Phase 12 (Evaluation) — the biggest single piece, worth its own dedicated pass once the above is stable, given multiple "needs more definition" items sit inside it.
4. Phase 13 (Signature batch analysis) and the Optimization dashboard — lower urgency, can trail behind.
