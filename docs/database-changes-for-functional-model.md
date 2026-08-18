# Database Changes for the Proposed Functional Model

**Source:** `docs/proposed-functional-model-and-navigation.md` (the functional/navigation model this proposal implements).
**Companion:** `docs/database-schema-migration-plan.md` — the living schema record this proposal is meant to be folded into once reviewed, continuing its phase numbering (Phase 8 "Template organization," Phase 9 "Expert review tracking" are already documented there but not yet built; this proposal picks up from Phase 10).
**Status:** Proposal for review — nothing here is built yet.

This document compares the functional model against the schema as it's *actually implemented right now* (verified directly against the current `.hbm.xml` mappings, not just prior planning docs — Phases 1–7 of the migration plan are live; Phases 8–9 are documented but not yet built) and proposes the changes needed to close the gap.

---

## 1. Summary: what's new, what extends existing work, what needs no schema at all

| Functional area | Verdict |
|---|---|
| Test Set lifecycle (Draft/Reviewed/Approved), versioning, deep-copy lineage | **New columns on `match_set`** |
| Test Case provenance categories, notes, review history | **New columns on `match_item` (some already planned in Phase 9) + a new `match_item_review` history table** |
| Configuration | **No change.** The functional doc explicitly defers configuration lifecycle/versioning; current schema already supports everything else asked of it. |
| Evaluation | **Entirely new — two new tables.** Nothing today persists an evaluation; results are computed fresh on every page view and discarded. |
| Signature Inspector (single signature) | **No schema needed.** Decode-on-demand against an existing configuration; nothing to persist. |
| Batch Signature Analysis | **New — two new tables.** |
| Signature-to-example generation | **No new table** — generated pairs are just ordinary `match_item` rows using the provenance columns proposed for Test Cases. |
| Optimization runs / Island status | **No new table.** The existing insert-only `configuration` history (Phase 6) plus `island_credential` already contains everything needed to derive "runs" and "recent activity" as queries — see §6. |
| Configuration promotion/"accept" | **Intentionally not proposed.** The functional doc itself defers this ("future lifecycle/version concepts for configurations... not defined by this document") — noted so it isn't accidentally designed in here. |
| Top/right-side navigation | **No schema implications** — presentation only. |

---

## 2. `match_set` — Test Set lifecycle, versioning, and copy lineage

### Current (verified against `MatchSet.hbm.xml`)

```text
match_set_id, organization_id, label, created_by_user_id, updated_by_user_id, created_at, updated_at
```

(Phase 8, already documented but not yet built, adds `is_template` — unaffected by this proposal, additive alongside it.)

### Proposed additions

```text
lifecycle_status         varchar(20) NOT NULL DEFAULT 'DRAFT'   -- DRAFT | REVIEWED | APPROVED
version                  varchar(50) NULL
copied_from_match_set_id int/bigint NULL FK -> match_set.match_set_id (self-referential)
root_match_set_id        int/bigint NOT NULL FK -> match_set.match_set_id (self-referential)
archived_at              datetime NULL
```

### Reasoning

- **`lifecycle_status`** — a plain three-value state, reversible in either direction per the functional doc ("Draft ⇄ Reviewed ⇄ Approved"). Enforcing "Approved sets reject case-level edits" is application logic (in `OrgScope`, alongside its existing organization-boundary checks), not a database constraint — consistent with how `is_template`'s write restriction is already handled.
- **`version`** — a free-form label (`v1.2`, `v1.3`), separate from `label` (the Test Set's name). Nullable because not every Test Set needs one (a one-off scratch set has no reason to be versioned).
- **`copied_from_match_set_id`** — "Test Set v1.2 is Approved; v1.3 is Draft and contains the next round of changes" (§2.1) means each version is its **own row**, produced by a deep-copy operation, not a single mutable row with an incrementing version number. This self-referential FK captures direct lineage. It's informational only — per §2.3 ("Provenance is historical information. It should not create a dependency in which modifying the source also modifies the copied Test Case"), the application must never propagate edits through this link in either direction.
- **`root_match_set_id`** — **decided.** "Show me every version of this Test Set" is core, frequently-run functionality (§2.1/§4.2), not a rare lookup, so it gets a flat, indexed column rather than requiring a chain-walk (recursive CTE or app-side loop) every time it's needed. Always populated, never null: a brand-new (non-copied) set points at its own `match_set_id`; a copy inherits its source's `root_match_set_id` directly (not the source's own id), so every member of a version family — no matter how many generations deep — points at the same root. "All versions of this family" becomes `WHERE root_match_set_id = :rootId`, no recursion required. Set once at creation, never modified after, same as `copied_from_match_set_id`.
- **`archived_at`** — kept as an independent nullable timestamp rather than folded into `lifecycle_status`, so a set can be archived from any lifecycle state (an Approved set doesn't have to pass back through Draft to be archived) without losing its last real status. Same soft-delete pattern already used elsewhere (`island_credential.revoked_at`).

---

## 3. `match_item` — provenance, notes, and current-state review flags

### Current (verified against `MatchItem.hbm.xml`)

```text
match_item_id, match_set_id, label, description, patient_data_a, patient_data_b,
expect_status, data_source, created_by_user_id, updated_by_user_id, created_at, updated_at
```

(Phase 9, already documented but not yet built, adds `original_expect_status`, `is_reviewed`, `needs_review` — this proposal keeps those and adds the rest. One thing worth flagging: Phase 9's write-up never actually specified a dedicated notes column, even though it was discussed in the same conversation — closing that gap below.)

### Proposed additions (on top of Phase 9's three columns)

```text
provenance_type          varchar(30) NOT NULL DEFAULT 'MANUAL'   -- MANUAL | IMPORTED | GENERATED | SIGNATURE_GENERATED | COPIED
copied_from_match_item_id int/bigint NULL FK -> match_item.match_item_id (self-referential)
source_signature          varchar(500) NULL
review_notes               text NULL
```

### Reasoning

- **`provenance_type`** — the five categories in §2.3, as a controlled vocabulary rather than overloading the existing free-text `data_source` column (which already carries different information today — historically a person's name or a generator tag — and should keep that meaning rather than being repurposed). `data_source` becomes the free-text *detail* for a provenance type (which import file, which generator scenario); `provenance_type` is the structured category the UI filters on (§4.3's "Manually Created / Imported / Generated / Signature Generated" queue filters need this to be queryable, not parsed out of free text).
- **`copied_from_match_item_id`** — mirrors `match_set`'s lineage pointer, at the case level. Gets set automatically on every item produced by a Test Set deep-copy; same "never a live dependency" rule applies.
- **`source_signature`** — populated only when `provenance_type = SIGNATURE_GENERATED` (§9.4's generate-from-signature workflow). A plain string, not a foreign key to any signature table — signatures aren't first-class persisted entities in this proposal (see §5), they're values.
- **`review_notes`** — free-text, editable, distinct from `description` (which remains whatever scenario/source metadata came in at creation, per the existing model). This was in scope for Phase 9 but got dropped in the shuffle — restoring it here.

### Not proposed: denormalizing evaluation/signature info onto `match_item`

Whether a case currently agrees or disagrees with the last evaluation, or which signature it produced, is **evaluation-run-specific, not a property of the Test Case itself** — the same case can be evaluated against many configurations with different results each time. That information belongs in the new `evaluation_result` table (§4), not on `match_item`.

---

## 4. `match_item_review` — review history (new table)

Nothing today preserves more than the single current `expect_status`. §2.5 explicitly wants prior reviewer opinions retained, not overwritten:

```text
Reviewer A — Possible Match
Reviewer B — Not Sure
Reviewer A — Match
```

### Proposed table

```text
match_item_review
------------------
match_item_review_id   int/bigint PK AUTO_INCREMENT
match_item_id           int/bigint NOT NULL FK -> match_item.match_item_id
reviewer_user_id        int/bigint NOT NULL FK -> user.user_id
classification           varchar(20) NOT NULL   -- Match | Possible Match | Not a Match | Not Sure
notes                     text NULL
reviewed_at               datetime NOT NULL
```

Recommended index: `INDEX(match_item_id, reviewed_at)`.

### Reasoning

- **Append-only, never updated or deleted** — each row is one historical opinion. "The most recent accepted review becomes the current expectation" (§2.5, rule #7) means the application updates `match_item.expect_status` (and sets `is_reviewed = true`) every time a new row is inserted here, but the history rows themselves are immutable.
- **No `organization_id` on this table** — scoped the same way `match_item` itself is, through `match_item_id → match_set_id → organization_id`, consistent with how the existing schema already treats `match_item` as a child of `match_set` rather than independently organization-tagged.

### Decided: a copied Test Case starts with clean review history

When a Test Set is deep-copied, the new `match_item` rows get **no `match_item_review` rows carried over** — each starts with a completely empty history, even though its source may have been reviewed extensively.

A review event belongs to the row it was actually performed on; copying history forward would misattribute past decisions to a row nobody has actually looked at yet. The original's full history stays fully intact on the source row and remains reachable via `copied_from_match_item_id` (§3) — the UI can surface "see how this case was reviewed before the copy" as an explicit link to the original's real history, not as duplicated rows pretending to be the copy's own.

This is a direct, non-optional consequence for the columns that already exist on `match_item`, not a separate decision:

- **`original_expect_status`** on the copy snapshots whatever `expect_status` the source had *at copy time* — the same "written once, at creation" rule as any other new row, where "creation" for a copy means the moment of copying. It is **not** copied from the source's own `original_expect_status`, which could already be a stale, superseded value.
- **`is_reviewed`** and **`needs_review`** both reset to `false` on the copy. Nobody has reviewed *this* row yet, however thoroughly its source was reviewed.
- **Relationship to Phase 9's `is_reviewed`/`original_expect_status`:** `is_reviewed` becomes logically equivalent to "at least one row exists here for this `match_item_id`," but it's worth keeping as a maintained, denormalized boolean on `match_item` rather than replacing it with an `EXISTS` subquery everywhere — it's exactly the kind of frequently-filtered flag (§4.3's "Unreviewed" queue) that's worth not re-deriving on every query. `original_expect_status` still earns its keep as a creation-time snapshot separate from this history — it answers "what did this case start as" even for cases with zero review-history rows (imported/generated cases that have never been touched by a human), which this table alone can't answer.

---

## 5. `evaluation` and `evaluation_result` — the biggest gap (new tables)

This is the largest actual gap. Today, "evaluation" is not a persisted concept at all — `TestMatchingServlet`/`TestSetServlet` compute pass/fail live against whichever configuration happens to be loaded in the viewer's session, and throw the result away after rendering the page (a finding already on record in `data-model.md` from the v1 review). The functional model's rule #12 — "Evaluation must identify the exact Test Set and Configuration used" — and the entire Evaluations navigation area (§7) require evaluations to be real, durable, queryable records.

### Proposed tables

```text
evaluation
----------
evaluation_id          int/bigint PK AUTO_INCREMENT
organization_id         int/bigint NOT NULL FK -> organization.organization_id
match_set_id             int/bigint NOT NULL FK -> match_set.match_set_id
configuration_id         int/bigint NOT NULL FK -> configuration.configuration_id
run_by_user_id            int/bigint NULL FK -> user.user_id
run_at                     datetime NOT NULL
total_cases                int NOT NULL
scorable_cases              int NOT NULL
not_sure_cases               int NOT NULL
agree_count                   int NOT NULL
disagree_count                  int NOT NULL
score                            double NULL
```

```text
evaluation_result
------------------
evaluation_result_id     int/bigint PK AUTO_INCREMENT
evaluation_id              int/bigint NOT NULL FK -> evaluation.evaluation_id
match_item_id                int/bigint NOT NULL FK -> match_item.match_item_id
expected_classification        varchar(20) NOT NULL
calculated_classification        varchar(20) NOT NULL
signature                          varchar(500) NULL
agrees                                boolean NOT NULL
```

Recommended indexes: `evaluation(organization_id)`, `evaluation(match_set_id)`, `evaluation(configuration_id)`, `evaluation_result(evaluation_id)`, `evaluation_result(evaluation_id, agrees)`, `evaluation_result(evaluation_id, signature)` (the last one directly supports §7.5's signature-group analysis).

### Reasoning

- **`organization_id` directly on `evaluation`**, rather than only derivable through `match_set`/`configuration` — matches the existing pattern of putting the tenant boundary directly on every top-level owned resource (`match_set`, `configuration`, `island_credential` all do this already) rather than requiring a join for every access check.
- **`expected_classification` is a snapshot, not a live reference.** `match_item.expect_status` can change after an evaluation runs (that's the whole point of the review workflow) — capturing what it *was at evaluation time* preserves an honest historical record instead of letting `evaluation_result` silently reinterpret itself if the case gets reclassified later. This is the same "don't let history quietly rewrite itself" principle behind `original_expect_status` and the review-history table above.
- **The confusion matrix is not stored directly.** It's fully derivable by aggregating `evaluation_result` rows (`GROUP BY expected_classification, calculated_classification`), so storing it redundantly on `evaluation` would just be another place for the same data to drift. `evaluation`'s own summary columns (`total_cases`, `scorable_cases`, `agree_count`, `disagree_count`, `score`) are kept as real columns because they're what every list/summary view needs immediately, without an aggregation query.
- **Configuration comparison (§8) needs no additional schema.** "Compare Configuration A vs. B against the same Test Set" is just two `evaluation` rows (same `match_set_id`, different `configuration_id`) joined on `match_item_id` through their `evaluation_result` rows — a query, not a new table.
- **Growth is a real, known consideration — retention is decided as: not decided yet, deliberately.** An evaluation against a several-thousand-case Test Set produces a few thousand `evaluation_result` rows every time it runs. That's the accepted cost of the functional model's own requirement to persist real evaluation history rather than recomputing on every page view. Whether re-running an evaluation should also prune the previous run, or whether history accumulates indefinitely, is explicitly left open until there's enough real usage to know which is actually wanted — this table's design doesn't need to answer that now. `evaluation_result` is a pure child of `evaluation` with nothing else pointing back at it and no cascade-delete configured, so "delete an old evaluation run and its results" stays a clean, self-contained, purely additive decision to make later, whichever way it goes.

---

## 6. Optimization / Islands — no new table

Re-checking §10.2's list against what already exists:

- **"Optimization runs" / "candidate configurations" / "most recent/best configurations"** — the `configuration` table is already insert-only as of Phase 6 specifically so generation-over-generation history is retained. Every generation an Island submits is already a permanent row, tagged with `world_name`, `island_name`, `island_credential_id`, `generation`, `generation_score`, `created_at`. A "runs" or "recent activity" view is a `GROUP BY (world_name, island_name, island_credential_id)` query over existing data, not a new entity.
- **"Islands" / "run status"** — `island_credential` already tracks `last_used_at`. Whether a given Island is *currently* actively running is inherently a live-process question (an external JVM, not something the database can authoritatively know) rather than a durable fact worth modeling — staleness of `last_used_at` (e.g. "no sync in the last N minutes") is a reasonable proxy for "probably not running," and adding real heartbeat/liveness tracking now would be solving a problem the functional doc doesn't actually ask for.
- **"Promotion of candidate configurations into normal Configuration analysis"** — every candidate is already an ordinary `configuration` row the moment it's synced; there's nothing to "promote" structurally. What the analyst does with it (evaluate it, compare it, keep using it) is already fully supported by the `evaluation` table above with no special status needed.

**Deliberately not proposed here:** a `configuration.is_promoted`/active-status flag. The functional doc explicitly says configuration lifecycle/versioning "may be useful, but are not defined by this document" (§6.2) — noting that here so it doesn't get designed in by accident. If a real need for it shows up once Evaluations exist, it's a small, additive follow-up.

---

## 7. Signature Inspector and Batch Signature Analysis

### Signature Inspector (§9.2) — no schema needed

Single-signature decode-against-a-configuration is a compute-on-demand operation using functionality that already exists (`ReverseSignatureUtil`, already used by `SignatureServlet`). Nothing to persist.

### Batch Signature Analysis (§9.3) — new tables

```text
signature_batch
----------------
signature_batch_id       int/bigint PK AUTO_INCREMENT
organization_id            int/bigint NOT NULL FK -> organization.organization_id
label                        varchar(250) NULL
uploaded_by_user_id           int/bigint NULL FK -> user.user_id
uploaded_at                     datetime NOT NULL
```

```text
signature_batch_entry
-----------------------
signature_batch_entry_id   int/bigint PK AUTO_INCREMENT
signature_batch_id            int/bigint NOT NULL FK -> signature_batch.signature_batch_id
signature                        varchar(500) NOT NULL
count                                int NOT NULL
```

Recommended index: `signature_batch_entry(signature_batch_id)`.

### Reasoning

- **Decoded results are intentionally not stored.** A batch holds only the raw uploaded `(signature, count)` pairs. Decoding — classification, detector scoring, configuration compatibility — is computed on demand against whichever configuration the analyst currently has selected, the same way the Signature Inspector already works. Persisting decoded results would tie a batch to one configuration at upload time and go stale the moment the analyst wants to check the same batch against a different one; keeping the batch raw means it can be re-analyzed against any configuration at any time.
- **No FK from `signature_batch` to `configuration`.** For the same reason — a batch isn't "for" one configuration, it's raw data that outlives any single analysis session.

### Signature-to-example generation (§9.4) — no new table

A generated Patient A/B pair is just a new `match_item` row (§3's `provenance_type = SIGNATURE_GENERATED`, `source_signature` set to the signature it was generated from) added to whatever Test Set the analyst chooses. No dedicated "generated example" entity is needed beyond what's already proposed for Test Cases.

---

## 8. Proposed phase sequence (continuing `database-schema-migration-plan.md`'s numbering)

Once this is reviewed and confirmed, the natural split — each additive, none touching existing data destructively:

- **Phase 10** — `match_set` lifecycle/version/copy-lineage columns (§2).
- **Phase 11** — `match_item` provenance/notes columns (§3) + `match_item_review` history table (§4). Natural to land together with Phase 9 (already documented, not yet built) as one combined pass over `TestSetServlet`, since they touch the same review workflow.
- **Phase 12** — `evaluation` / `evaluation_result` (§5) — the biggest single piece of new functionality, worth its own phase.
- **Phase 13** — `signature_batch` / `signature_batch_entry` (§7).

No phase here requires touching `configuration`, `organization`, `user`, or `island_credential` at all — this proposal is additive on top of the existing tenant/identity model, not a revision of it.

---

## 9. Decisions made on review

All three open questions from the initial proposal are resolved:

1. **`match_set` "family" grouping** — `root_match_set_id` added (§2), not chain-walking.
2. **`evaluation_result` retention** — explicitly deferred, not decided now. The design doesn't need to answer it: no cascade-delete, `evaluation_result` is a self-contained child of `evaluation`, so whether re-running an evaluation later also prunes the previous run is a purely additive decision to make once there's real usage to inform it.
3. **Review history on Test Set copy** — copies start with clean history (§4). `original_expect_status` snapshots the source's current value at copy time; `is_reviewed`/`needs_review` reset to `false`. The source's full history remains reachable via `copied_from_match_item_id`.
