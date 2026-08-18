# Known Issues — Running List

A lightweight, ongoing log of bugs worth fixing, found either during the v1 code review or while doing v2 implementation work. This is a tracking list, not a design document — for the reasoning/detail behind an already-cataloged v1 finding, follow the link rather than duplicating it here.

Update this file whenever a phase turns up something that's real but out of scope for that phase, per `v2-roadmap.md`.

| Found | Area | Issue | Status |
|---|---|---|---|
| v2 §3a (2026-08-17) | `CentralServlet.doPost`, `ACTION_UPDATE` | Building a brand-new `Configuration` row for a never-before-seen `(worldName, islandName)` pair never sets `generatedDate` before `save()` — fails against the `generated_date NOT NULL` constraint. Never fired in practice because every world/island combo in existing seed data already has a row. Confirmed pre-existing (predates Phase 2) via `git show`. Fix: default to current date/time when not otherwise known. | Scheduled (Phase 5, `v2-roadmap.md` §6) |
| v1 review | `World.java` constructor | `lowerCutStart = size - (lowerCut ^ 2)` uses bitwise XOR, not squaring — cripples the evolutionary algorithm's per-generation replacement rate to ~2% of intended. See `optimization-and-islands.md` §4. | Open |
| v1 review | `Creature`'s clone constructor | Clones the `Missing` tree from the parent's `Twin` tree instead of its own `Missing` tree. See `optimization-and-islands.md` §4. | Open |
| v1 review | `Scorer.java` / `Configuration.yml` / `island.yml` | Four different places define the default 3×3 scoring-weight matrix, and they disagree with each other. See `optimization-and-islands.md` §3. | Open |
| v1 review | `Scorer.registerMatch` | Test cases with expected status `Research`/`Not Sure` fall through into the "Should Not Match" scoring bucket instead of being excluded. See `optimization-and-islands.md` §2. | Open |
| v1 review | `RandomScriptServlet` | The "different"-pair column's pass/fail indicator appears to reuse the "same"-pair's result. See `trainer-pages.md`. | Open |
| v1 review | `DownloadHl7Servlet` | HL7 message-ID counter declared but never incremented; also queries the empty `patient` table. Low priority — table is being retired, not this servlet's logic. See `data-model.md` §5. | Deferred (table being retired) |

**Status values:** Open (not fixed, not scheduled) · Scheduled (tied to a specific roadmap phase) · Deferred (acknowledged, intentionally not fixing yet) · Fixed (note which commit/phase).
