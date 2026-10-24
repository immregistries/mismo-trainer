# Island Runner Implementation Plan

**Status:** Draft for review (2026-10-23)

**Implements:** [`centrally-managed-local-island-runner.md`](centrally-managed-local-island-runner.md) (the architecture). That document says *what* to build; this one records the implementation decisions, the module and schema layout, and the order of work.

**Starting assumptions (confirmed by Nathan):** no current users of the Island stack, so everything legacy can be removed rather than migrated; schema changes go in `unapplied_updates.sql` (now `mismo-trainer-web/src/db/`) and are applied after the daily backup; big structural changes to this repository are acceptable.

---

## 1. Decisions

### D1. REST/JSON stack: one hand-routed servlet + Gson

The API is about a dozen endpoints with one client. A framework (JAX-RS/Jersey, Spring) would be larger than the API itself and would sit awkwardly next to the Hibernate 3 / plain-servlet stack.

- **Server:** one `RunnerApiServlet` mapped to `/api/v1/*`. It splits `getPathInfo()` into segments and dispatches with a small `switch` to handler methods. Handlers take a Hibernate `Session`, the authenticated `Runner`, and a request DTO; they return a response DTO or throw an `ApiException(status, code, message)`. Keeping handlers as plain methods makes them testable without Tomcat.
- **JSON:** **Gson 2.11.0** — a single ~300 KB jar with no transitive dependencies, and it handles Java 17 records. DTOs are records in the shared module (D2), so client and server serialize the identical types.
- **Client HTTP:** the JDK's `java.net.http.HttpClient`. No dependency.
- **Errors:** every non-2xx response has body `{"error": "<CODE>", "message": "<human text>"}`. Codes are a fixed enum (`UNAUTHORIZED`, `NOT_FOUND`, `NO_ASSIGNMENT`, `HASH_MISMATCH`, `CLIENT_UNSUPPORTED`, `RUN_NOT_ACTIVE`, …) so the runner can print a clear message without parsing prose.
- **Auth:** `AuthenticationFilter` stops exempting `POST /CentralServlet` and instead lets `/api/v1/*` through; `RunnerApiServlet` does bearer-token auth itself (every route except `POST /api/v1/runner-pairings`).
- **Org scoping:** `OrgScope`'s read rules are expressed against a `User`. Add overloads that take an `Organization` (the runner's) so the same "owned or template" rule applies to configurations and Test Sets fetched by a runner.

Website pages for runners are ordinary session-authenticated servlets, not API consumers.

### D2. Modules: turn `mismo-trainer` into a three-module Maven build

`mismo-match` stays its own repository and library. The optimizer is trainer-specific, so it moves into a new module in *this* repository:

```text
mismo-trainer/                    parent pom (packaging: pom), shared versions/plugins
  mismo-optimizer/                jar  -- no servlet, no Hibernate
    org.immregistries.mismo.optimizer.engine     World, Creature, Scorer, TestCaseLoader
    org.immregistries.mismo.optimizer.protocol   API DTO records, Gson setup, hashing helper
  mismo-trainer-web/              war  -- today's src/ moves here (finalName stays "mismo")
  mismo-runner/                   jar  -- console app; shaded jar + jpackage image
```

- `mismo-optimizer` depends on `mismo-match` and Gson only. Both other modules depend on it.
- The webapp already uses `Scorer` (in `OrgScope`, `TestSetServlet`, `TestMatchingServlet`), so having it in the shared module is natural rather than a forced split.
- One shared module rather than separate `engine`/`protocol` modules: they are small, released together, and always consumed together.
- Consequence: the WAR moves from `target/mismo.war` to `mismo-trainer-web/target/mismo.war`. Update `production-deployment-plan.md`/checklist and any deploy script.

### D3. Test Set immutability: content-addressed snapshots

`match_set` already has versioning (`root_match_set_id` families, copies) and a lifecycle (`DRAFT`/`REVIEWED`/`APPROVED`), but `APPROVED` is reversible and cases are editable in non-approved sets, so neither can guarantee a run's inputs never change. Rather than locking Test Sets, the server **copies** what a run needs:

- New table `test_set_snapshot`: an immutable serialized copy of one `match_set`'s scoreable cases, stored as the exact JSON bytes served, plus its SHA-256.
- The snapshot is made when the runner calls `GET /runners/me/assignment` (i.e. at `start` time, so the run gets the Test Set as it is *now*). If the newest snapshot for that `match_set` has the same hash, it is reused instead of inserting a duplicate.
- Only cases with an expected status of Match / Possible Match / Not a Match are included (what `MatchItem.isExpectedStatusSet()` already filters on). `caseCount` is the count of those.
- Case JSON carries the stored strings as-is (`label`, `description`, `expectStatus`, `patientDataA`, `patientDataB`). The runner converts them with the existing `Island.toRuntimeMatchItem` logic, moved into `TestCaseLoader`. No new patient representation.
- Any lifecycle status may be assigned; the assignment form and run summary show the status, with a warning for `DRAFT`. (Easy to tighten to approved-only later.)
- Size is not a concern: ~1,700 cases of a few KB each is a few MB of `LONGTEXT`, and snapshots are only made when content changes.

Configurations need no snapshot table: with `CentralServlet` removed, nothing overwrites `configuration` rows in place any more, so rows become insert-only. Their content hash is SHA-256 of `configuration_script`, stored in a new `content_sha256` column (backfilled in SQL with `SHA2(configuration_script, 256)`).

**Hashing rule (both content types):** SHA-256 over the exact UTF-8 bytes served. The client hashes the response body before parsing. No canonical-JSON rules needed.

### D4. Remove the legacy Island stack outright

Deleted in the first slice, not ported:

- `Island.java`, `IslandSync.java`, `island.yml`, and the `exec-maven-plugin` block in `pom.xml`
- `CentralServlet` (its `doGet` "Central" page is replaced by the Runners pages), `IslandCredentialServlet`, `IslandCredentialSupport` (its token generation and SHA-256 code moves into `RunnerCredentialSupport`), `IslandCredential` + `IslandCredential.hbm.xml`
- `island_credential` table and `configuration.island_credential_id`
- The `AuthenticationFilter` exemption and nav links to the Central / Island credential pages

`configuration.world_name`/`island_name` stay (existing pages list and filter on them). New candidates fill them with the assignment name and the runner display name. `optimization-and-islands.md` §5–§6 get a "superseded" note pointing here.

### D5. Engine bugs

Re-checked against current code:

| Item | Status | Action |
|---|---|---|
| `lowerCutStart` XOR bug | **Already fixed** (`World.java:114` now `lowerCut * lowerCut`; known-issues.md, 2026-08-18) | Update `optimization-and-islands.md` §4, which still describes it as live |
| Clone copies `Missing` from `Twin` | **Already fixed** (`Creature` clone constructor now clones `getMissing()`) | Same doc update |
| `Scorer.weights` is a mutable `static` | **Open, and live in the webapp today**: `OrgScope.java:462` calls `new Scorer(weights)`, which overwrites the weights for every concurrent user and request | Make weights an instance field. `World` holds the run's weights, and `Creature.score()` passes them in. Remove `Scorer.getWeights()`. |
| `scoreRate` uses integer math | Open. `(end-start)/1000` truncates, so any scoring pass under 1 s reports 0, and the division is integer | Compute a `double` creatures/second from milliseconds |
| Dedup guard cost | Open. Each new creature's `hashCode()` (string-building across four trees) is compared against all 1,000 creatures: about 500k tree hashes per generation | Cache each creature's hash once its DNA is final and check against a `HashSet`. Measure before and after. |
| `Scorer.registerMatch` buckets Research/Not Sure as "Should Not Match" | Open (known-issues.md). Does **not** affect optimization, because `Creature.score()` already filters with `isExpectedStatusSet()`, but webapp callers are exposed | Have `registerMatch` ignore cases whose status isn't scoreable |
| A configuration script with no `scoringWeights` block throws an NPE in `mismo-match` `Configuration.setup()` | Open (`Configuration.java` ~line 268 dereferences the map unguarded) | Runner always writes the block (D6). Optionally guard it in `mismo-match` (separate repo). |
| Unseeded `static Random` in `World` and `MatchNode` (`mismo-match`) | Open. Runs are not reproducible | **Defer.** Making them seedable needs a `mismo-match` change. Drop "random seed" from the checkpoint contents for now. |

**Optional, after the main work:** parallel scoring. Scoring one creature is independent of the others, and a local workstation has idle cores, so this is likely the biggest throughput gain available. First confirm that `PatientCompare`/matchers keep no shared mutable state during scoring.

### D6. Scoring weights: named, versioned Scoring Profiles

**Read [`optimization-objectives-and-scoring-profiles.md`](optimization-objectives-and-scoring-profiles.md) first.** The default matrix is a deliberate *national measurement* objective: Match and Possible are treated as one "potential match" signal. It is not a production-calibration matrix, and the software must allow steering toward other objectives without hiding that it happened.

What the code does today:

- The defaults are already unified. `mismo-matcher.yml` in `mismo-match` is the canonical source (12, 10, −10 / 8, 10, −8 / −10, −8, 10), and `Scorer` reads its default from it. The one leftover is `mismo-match`'s `Configuration` field initializer, which still holds the old 20/0/−5 values. `setup()` always overwrites them, but they should be deleted to avoid confusion.
- Every configuration script **embeds** a `scoringWeights` block. The old Island used `island.yml`'s weights if present, otherwise the seed configuration's embedded weights. Nothing named or explained the choice.

Decision:

1. **A Scoring Profile is a first-class record:** name, purpose (`MEASUREMENT` / `PRODUCTION_CALIBRATION` / `EXPERIMENTAL`), a required description of intent, and the 3×3 weights. Profiles are versioned like assignments: editing inserts a new version, and old runs keep theirs. Profiles follow the existing template rules (an AIRA-owned template profile is readable by every organization).
2. **Seed one template profile**, "National measurement (potential match)", purpose `MEASUREMENT`, with the canonical weights and a description taken from the objectives document. No production profile is seeded; the illustrative matrix in that document is not validated.
3. **The assignment selects a profile** (default: national measurement). There are no free-form weights on the assignment. Steering differently means creating or choosing a profile with a stated purpose, which is the visibility the objectives document requires.
4. The run snapshots the profile id, version and weights (`optimization_run.scoring_weights_json` stays as the frozen copy).
5. The runner overwrites the `scoringWeights` block of the seed and every candidate with the run's weights, so every uploaded candidate records the fitness function it was trained under.
6. **Visibility everywhere a score appears:** the runner's start summary, dashboard, and `weights` command show the profile name, purpose and matrix. The website shows the profile name next to every run and candidate score. Run lists only compare runs with the same profile version and Test Set snapshot.
7. **Seed mismatch warning:** if the chosen seed configuration's embedded weights differ from the selected profile, the assignment form and the runner summary say so ("seed was trained under different weights"). This is allowed, because re-steering an existing configuration is a legitimate use.
8. **Measurement view (Slice 3):** run and candidate pages also show the 3×3 table collapsed to 2×2 (potential match vs not), since that is the quantity the national program actually uses.
9. **Field scope is shown alongside the profile.** The seed configuration, not the profile, decides which patient fields the result can use: the optimizer can disable detectors but never re-enable them (objectives document, "Mechanical note"). Add `FieldScope.of(configuration)` to `mismo-optimizer`, which walks the enabled leaf detectors and returns their `fieldName`/`fieldNameOther` set. The assignment form, runner start summary, and run page list the seed's enabled fields. When the profile's purpose is `MEASUREMENT` and the seed enables any field outside the measurement core (the fields enabled in the shipped `mismo-matcher.yml`; `matching-engine.md` §10a), show a warning. Like the seed-weights warning, it is allowed but visible.

### D7. Distribution: shaded JAR first, `jpackage` app-image at the end

- Through development and testing, `mismo-runner` builds a shaded `mismo-runner.jar` (`java -jar`, Java 17+).
- For the analyst, the recommended release is **`jpackage --type app-image --win-console`**: a folder with `Mismo Runner.exe` and a trimmed (jlink) runtime, zipped. It needs no installer, no WiX (WiX is only required for `.msi`/`.exe` *installers*), and no admin rights. The zip is roughly 50–70 MB.
- **Why it's worth doing even though Java could be installed:** it removes the most likely support problem, which is the wrong Java on `PATH`. An older JRE fails with `UnsupportedClassVersionError`, which means nothing to an analyst. The cost is about a day, and `jpackage` must run on Windows. It's the last slice, so it's easy to drop if not wanted.
- **Hosting:** a GitHub Release asset on `immregistries/mismo-trainer`, linked from the website's pairing instructions with its SHA-256. Too large to put inside the WAR.

### D8. Database migration

All DDL goes in `mismo-trainer-web/src/db/unapplied_updates.sql`, applied after the daily backup. See §3.

---

## 2. Domain behavior details

These are decisions the architecture document leaves to implementation.

- **Pairing:** "Create Runner" inserts a `runner` row with a pairing code (format `XXXX-XXXX` from an unambiguous 31-character alphabet, valid for 15 minutes; only its hash is stored). `POST /runner-pairings` exchanges the code for a durable credential (`mrn_` + 32 random bytes, base64url) and clears the code. **Re-pair / replace credential** issues a new pairing code; the old credential stays valid until the new pairing succeeds, then is replaced. **Revoke** sets `credential_revoked_at`.
- **Pairing brute-force protection:** the pairing endpoint is unauthenticated. The code space (~8×10¹¹) plus the 15-minute expiry is adequate. Add a simple in-memory limit of 10 failed pairing attempts per IP per 15 minutes.
- **Assignment revisions:** `runner_assignment` rows are never updated. Saving the form inserts a new row with `revision = previous + 1`. The runner's current assignment is its highest revision. Saving is allowed while a run is active.
- **Run lifecycle:** `POST /runs` creates the run as `RUNNING` (the `STARTING` state collapses into the request itself). `stop` sets `STOPPED`, `fail` sets `FAILED`. A runner may have only one `RUNNING` run. Creating a new run while one is still marked `RUNNING` (e.g. after a crash) marks the old one `ABANDONED` with `stop_reason = 'superseded'`. Add `ABANDONED` to the status list. The website can also mark a run `ABANDONED`.
- **Reporting cadence:** progress every 60 s (small payload). The best candidate is uploaded when it changes, at most once every 5 minutes, plus immediately on `sync`, `stop`, and `exit`. The reporter keeps only the *latest* pending progress and candidate, so a long outage doesn't build up a queue. Retry uses exponential backoff capped at 5 minutes.
- **Candidate idempotency:** unique key `(optimization_run_id, content_sha256)` on `configuration`. A re-sent candidate returns the existing row's id with `200`.
- **Runner state on the website:** derived, not stored. *Running* = has a `RUNNING` run and was seen within 3 minutes. *Idle* = seen within 3 minutes with no running run. *Offline* = otherwise, showing "last seen".
- **Version check:** each request sends `X-Mismo-Client-Version` and `X-Mismo-Protocol: 1`. The server returns `CLIENT_UNSUPPORTED` (HTTP 426) below a configured minimum client version.

---

## 3. Schema (sketch for `unapplied_updates.sql`)

```sql
CREATE TABLE runner (
  runner_id               int NOT NULL AUTO_INCREMENT,
  organization_id         int NOT NULL,
  display_name            varchar(100) NOT NULL,
  pairing_code_hash       varchar(64) NULL,
  pairing_expires_at      datetime NULL,
  credential_hash         varchar(64) NULL,            -- NULL until paired
  credential_created_at   datetime NULL,
  credential_last_used_at datetime NULL,
  credential_revoked_at   datetime NULL,
  client_version          varchar(30) NULL,
  protocol_version        varchar(10) NULL,
  runtime_info            varchar(255) NULL,        -- Java/OS, for support
  last_seen_at            datetime NULL,
  created_at              datetime NOT NULL,
  created_by_user_id      int NOT NULL,
  PRIMARY KEY (runner_id),
  UNIQUE KEY uk_runner_credential (credential_hash),
  UNIQUE KEY uk_runner_pairing (pairing_code_hash)
  -- FKs to organization, user
);

CREATE TABLE runner_assignment (
  runner_assignment_id   int NOT NULL AUTO_INCREMENT,
  runner_id              int NOT NULL,
  revision               int NOT NULL,
  name                   varchar(150) NOT NULL,
  configuration_id       int NOT NULL,             -- seed configuration
  match_set_id           int NOT NULL,             -- live Test Set; snapshotted at start
  population_size        int NOT NULL,
  scoring_profile_id     int NOT NULL,             -- a specific profile version
  created_at             datetime NOT NULL,
  created_by_user_id     int NOT NULL,
  PRIMARY KEY (runner_assignment_id),
  UNIQUE KEY uk_runner_assignment_rev (runner_id, revision)
);

-- See optimization-objectives-and-scoring-profiles.md. Rows are never updated:
-- an edit inserts version + 1 with the same root id.
CREATE TABLE scoring_profile (
  scoring_profile_id     int NOT NULL AUTO_INCREMENT,
  root_scoring_profile_id int NOT NULL,            -- version family, like match_set
  version                int NOT NULL,
  name                   varchar(150) NOT NULL,
  purpose                varchar(30) NOT NULL,     -- MEASUREMENT / PRODUCTION_CALIBRATION / EXPERIMENTAL
  description            text NOT NULL,            -- required statement of intent
  scoring_weights_json   varchar(500) NOT NULL,
  organization_id        int NOT NULL,
  is_template            tinyint(1) NOT NULL DEFAULT 0,
  created_at             datetime NOT NULL,
  created_by_user_id     int NULL,                 -- NULL for the seeded row
  PRIMARY KEY (scoring_profile_id),
  UNIQUE KEY uk_scoring_profile_version (root_scoring_profile_id, version)
);
-- Seed: "National measurement (potential match)", MEASUREMENT, canonical weights,
-- owned by the template org, is_template = 1.

CREATE TABLE test_set_snapshot (
  test_set_snapshot_id   int NOT NULL AUTO_INCREMENT,
  match_set_id           int NOT NULL,
  content_sha256         varchar(64) NOT NULL,
  case_count             int NOT NULL,
  lifecycle_status       varchar(20) NOT NULL,     -- status at snapshot time
  content_json           longtext NOT NULL,
  created_at             datetime NOT NULL,
  PRIMARY KEY (test_set_snapshot_id),
  UNIQUE KEY uk_snapshot_content (match_set_id, content_sha256)
);

CREATE TABLE optimization_run (
  optimization_run_id    int NOT NULL AUTO_INCREMENT,
  runner_id              int NOT NULL,
  runner_assignment_id   int NOT NULL,             -- implies revision
  seed_configuration_id  int NOT NULL,
  seed_configuration_sha256 varchar(64) NOT NULL,
  test_set_snapshot_id   int NOT NULL,
  population_size        int NOT NULL,
  scoring_profile_id     int NOT NULL,
  scoring_weights_json   varchar(500) NOT NULL,  -- frozen copy of the profile's weights
  client_version         varchar(30) NOT NULL,
  protocol_version       varchar(10) NOT NULL,
  status                 varchar(20) NOT NULL,     -- RUNNING/STOPPED/FAILED/ABANDONED
  stop_reason            varchar(255) NULL,
  started_at             datetime NOT NULL,
  stopped_at             datetime NULL,
  last_reported_at       datetime NULL,
  last_generation        int NULL,
  candidate_rate         double NULL,
  best_score             double NULL,
  best_configuration_id  int NULL,
  PRIMARY KEY (optimization_run_id),
  KEY idx_run_runner_status (runner_id, status)
);

ALTER TABLE configuration
  ADD COLUMN content_sha256 varchar(64) NULL,
  ADD COLUMN optimization_run_id int NULL,
  ADD UNIQUE KEY uk_configuration_run_content (optimization_run_id, content_sha256);
UPDATE configuration SET content_sha256 = SHA2(configuration_script, 256);
-- then: drop fk_configuration_island_credential, drop column island_credential_id,
--       DROP TABLE island_credential
```

Progress history is **not** stored per report. The run row keeps the latest values, and candidates (which carry generation and score) provide the history. A `run_progress` table can be added later if charts are wanted.

New Hibernate mappings (`.hbm.xml`, matching the existing style): `Runner`, `RunnerAssignment`, `ScoringProfile`, `TestSetSnapshot`, `OptimizationRun`.

The assignment API response carries the profile as `scoringProfile: {id, version, name, purpose, weights}` in place of the architecture doc's bare `scoringSettings`.

---

## 4. API surface

Routes as in the architecture doc §10. Below are only the implementation specifics:

| Route | Auth | Notes |
|---|---|---|
| `POST /api/v1/runner-pairings` | pairing code | Returns `runnerId`, `runnerName`, `organizationName`, `credential` |
| `GET /api/v1/runners/me` | bearer | Also updates `last_seen_at`/versions |
| `POST /api/v1/runners/me/heartbeats` | bearer | Sent while idle at the prompt (every 2 min, inside the 3-minute "seen" window of §2; originally 5 min, which would have shown idle runners as Offline); running reports already count as contact |
| `GET /api/v1/runners/me/assignment` | bearer | Creates or reuses the Test Set snapshot. Not refused while a run is marked `RUNNING`: the next `POST /runs` marks that run `ABANDONED` (see Slice 1 notes) |
| `GET /api/v1/configurations/{id}/content` | bearer | `text/yaml`; org/template scoped |
| `GET /api/v1/test-set-snapshots/{id}/content` | bearer | `application/json`; served by **snapshot id** (replaces the doc's `/test-sets/{id}/versions/{v}`) |
| `POST /api/v1/runs` | bearer | Body: assignment id + revision, both hashes. `409 ASSIGNMENT_CHANGED` if the revision is no longer current; the runner re-fetches and shows the summary again |
| `GET /api/v1/runs/{id}` | bearer | Own runs only |
| `POST /api/v1/runs/{id}/progress` | bearer | `RUN_NOT_ACTIVE` if the run is no longer `RUNNING` (e.g. abandoned on the website); the runner shows the message and keeps going locally |
| `POST /api/v1/runs/{id}/candidates` | bearer | Idempotent on content hash |
| `POST /api/v1/runs/{id}/stop`, `/fail` | bearer | Final best candidate is sent first as a normal candidate |

Request bodies are capped at 2 MB (candidates are the largest at roughly 30 KB).

---

## 5. Runner application structure (`mismo-runner`)

```text
RunnerMain          entry point; idle prompt loop and running prompt loop
ApiClient           HttpClient + Gson; bearer header; maps error JSON to ApiError
IdentityStore       %LOCALAPPDATA%\Mismo Runner\identity.json (server URL, runnerId, credential)
InputCache          cache\<sha256>.yaml / .json; verified on write and read
RunSession          builds World from cached inputs; owns the optimization thread
Reporter            background thread; latest-only pending progress/candidate; backoff
Checkpoint          checkpoints\run-<id>.json (best script, score, generation, hashes)
Dashboard           roll-and-scroll status rendering (ported from Island's loop)
```

- **Credential storage:** a plain file under `%LOCALAPPDATA%`, which Windows already restricts to the user. Windows DPAPI isn't reachable from pure Java without JNA, so it's out of scope for now. The credential never appears in console output or logs.
- **Server URL:** entered at `pair`. A plain `http://` URL is refused unless the host is `localhost`/`127.0.0.1`.
- **Logging:** `java.util.logging` to `logs\runner-YYYYMMDD.log`, kept for 14 days. The console shows one-line messages only.
- **Crash recovery:** at startup, if a checkpoint exists for a run that the server still reports as `RUNNING`, offer to upload its best candidate and mark the run `FAILED` (`stop_reason = 'runner restarted'`). Restoring the whole population is out of scope.
- **Ctrl+C:** a shutdown hook makes a best-effort final upload and stop (5 s limit). Otherwise the checkpoint covers it.

---

## 6. Website (`mismo-trainer-web`)

Added under the planned **Optimization** navigation area, using `aira-web-components` like the existing pages:

- `RunnersServlet`: list (Runner, State, Active/last run, Generation, Best score, Last seen) and **Create Runner**.
- `ScoringProfileServlet`: list profiles (name, purpose, version, matrix) and create or edit them (an edit makes a new version). The description is required, and the page links to `optimization-objectives-and-scoring-profiles.md`.
- `RunnerServlet`: identity and versions; pairing code (shown once) and pairing instructions with download link; replace/revoke credential; assignment form (name, seed configuration, Test Set, population size, scoring profile defaulting to national measurement, with a seed-mismatch warning per D6); a "changes will apply to the next run" banner when the current revision differs from the active run's; assignment history; run history.
- `OptimizationRunServlet`: the run's frozen inputs (configuration, Test Set snapshot with case count and lifecycle status, scoring profile name/purpose/weights, client version), progress, the candidate list, and the 3×3 table with its 2×2 potential-match collapse. Each candidate links to the existing configuration pages so it can be loaded or evaluated with the tools that already exist.
- **Abandon run** action (website-side only; doesn't stop a local process).

---

## 7. Delivery slices

The work is ordered to get end-to-end feedback early. Each slice ends in a working, committed state.

**Slice 0: Restructure (no new features).** Parent pom with three modules. Move the engine into `mismo-optimizer`. Fix the static `Scorer` weights, `scoreRate`, and `registerMatch`. Add Gson. Delete the legacy Island stack (D4) except the schema drop. Update deploy docs for the new WAR path. Point `Scorer`'s javadoc at `optimization-objectives-and-scoring-profiles.md`, and add a matching comment above the `scoringWeights` block in `mismo-match`'s `mismo-matcher.yml` (separate repo), so anyone who finds the matrix in code finds the reason.
*Done when:* the WAR builds and deploys, existing pages (including evaluation scoring) behave as before, and optimizer unit tests pass.

*Slice 0 notes (done 2026-10-24).* Corrections to the plan found while implementing it:

- **`CentralServlet.doGet` was also the Configurations area's "Browse All Configurations" page**, and the only place offering **Copy to My Organization** for template configurations. Both went with the servlet (the nav link is removed). `OrgScope.copyConfiguration` is kept, unused, so the action can be put back on `WeightSetServlet` or a Slice 1 page if it is wanted.
- **Moving `src/` also moved `src/db/`.** The schema scripts are now under `mismo-trainer-web/src/db/`. The local refresh scripts in `C:\dev\mismo\scripts` (outside git) hard-coded `src/db` and were updated.
- **`Island.toRuntimeMatchItem` took the Hibernate entity**, so it could not move to `mismo-optimizer` as-is. It is split: `TestCaseLoader.toRuntimeMatchItem(label, description, expectStatus, patientDataA, patientDataB)` does the string conversion and null defaulting (what the runner needs for D3's case JSON), and the webapp's `RuntimeMatchItems` adds the id, update date, match set and user.
- **`IslandCredentialSupport` is deleted, not moved** (D4 said its token and SHA-256 code would move into `RunnerCredentialSupport`, which does not exist until Slice 1). The code is short and in git history.
- **`registerMatch` exposure (D5):** every webapp caller already filtered out Research / Not Sure before calling it, so the fix changes no page's numbers. Evaluations re-run on the new build matched earlier ones exactly.
- `TestMatchingServlet` used `new Scorer()`, i.e. whatever the static weights last held. It now passes the loaded configuration's weights, like `TestSetServlet` and `OrgScope`.

**Slice 1: Walking skeleton.** Full schema (§3), including the seeded measurement profile. API: pairing, `me`, assignment, both content routes, runs create/progress/candidates/stop. Minimal website: create runner and show pairing code, a single assignment form (profile is a dropdown; profile editing waits for Slice 3), a run list showing the profile name with each score. Runner: `pair`, `start` (fetch, verify, summary, create run), dashboard, `sync`, `stop`, `exit`, using the shaded JAR.
*Done when:* acceptance criteria 1 and 3–10 of the architecture doc work against localhost Tomcat using the dev JAR, with an AIRA review Test Set loaded in the database (upload it through the existing Test Set upload page if needed).

*Slice 1 notes (done 2026-10-24).* Verified end to end against localhost Tomcat with `mismo-runner.jar`: create runner, pair, assign (AIRA-D, 3,397 scoreable cases), start, progress and candidates on the website, change the assignment mid-run (the run kept revision 1), stop, start again (got revision 2). The run's best candidate, evaluated on the existing Evaluation page, scored exactly what the runner reported. Corrections and findings:

- **`GET /assignment` no longer returns `409 RUN_ACTIVE`.** That conflicted with §2's rule that `POST /runs` supersedes a stale `RUNNING` run: after a crash, the runner could never fetch an assignment again, and the website abandon action doesn't exist until Slice 3. The runner only fetches an assignment while it has no run going, so a `RUNNING` run at that point always belongs to a dead process. `POST /runs` marks it `ABANDONED` (`stop_reason = 'superseded'`).
- **Hash columns are `varchar(64)`, not `char(64)`.** Hibernate 3's `hbm2ddl.auto=validate` rejects a `char` column mapped as a string.
- **The snapshot JSON is about 2 MB for AIRA-D** (3,397 cases, about 600 bytes each), consistent with D3's estimate.
- **Candidates must carry the run's weights.** The server rejects a candidate whose `scoringWeights` differ from the run's profile (`INVALID_CANDIDATE`), which enforces D6 item 5 rather than trusting it.
- **Throughput on this workstation is about 4–10 creatures/second** on AIRA-D, i.e. roughly 2–4 minutes per generation at population 1,000. That makes Slice 5 (parallel scoring) more valuable than the plan suggests.
- Pulled forward from Slice 2: the reporter already backs off (10 s doubling to 5 minutes) and shows "Disconnected" on the dashboard. Still in Slice 2: crash checkpoints, `fail`, heartbeats, the version check, logs, credential revoke, the pairing rate limit, and handler tests against the database.
- A failed authenticated request rolls back its `last_seen_at` update along with everything else. That's harmless now; Slice 3's derived Online/Offline state may want contact recorded in its own transaction.
- `maven-shade-plugin` 3.5.1 was not in the local Maven repository, so the first build needs network access to Maven Central.

**Slice 2: Hardening.** Retry/backoff and a "Disconnected" display; crash-recovery checkpoints; `fail` and heartbeats; version check; logs; all §15 startup refusals with clear messages; credential replace/revoke; pairing rate limit; org-isolation tests (runner A cannot read org B's configuration, Test Set, or runs) and idempotency tests.

*Slice 2 notes (done 2026-10-24).* All items are in. Verified against localhost Tomcat:
- **Crash recovery:** I killed the runner process mid-run. The server still had generation 3 at 72.9% (progress goes out every minute, candidates every five). The checkpoint had generation 52 at 82.5%. On relaunch the runner offered it, uploaded it, and marked the run FAILED ("runner restarted").
- **Version check:** old, missing and wrong-protocol clients are all refused with `426 CLIENT_UNSUPPORTED`, against `app_setting.runner.min.client.version`, which starts at 2.0.
- **Pairing rate limit:** the 11th failed pairing from one address gets `429 TOO_MANY_ATTEMPTS`.
- **Heartbeats** move `last_seen_at`.
- **Revoke and re-pair:** a revoked runner is refused with a clear message; a new code re-pairs it.
- **Logs** go to `logs
unner-YYYYMMDD.log` in local time, and the credential never appears in them.

`RunnerApiDatabaseTest` covers the plan §8 handler cases (12 tests, each rolled back). Notes:

- **The database tests are opt-in** (`MISMO_TEST_DB_USER`/`MISMO_TEST_DB_PASSWORD`), so `mvn package` never needs a database. On this workstation, the User-scope `MISMO_DB_PASSWORD` doesn't match the `mismo_web` account (Tomcat's service environment does), so keying them off the app's variables would have broken every build. The test factory pins `hibernate.default_catalog=mismo`, because a broad account like root otherwise trips the Hibernate 3.6 validator on other databases' tables (the same problem as the stray `aart` grant).
- **Two runner paths are implemented but not exercised end to end:**
  - A World thread dying with an exception: upload the best, mark FAILED, tell the user. Nothing in normal data makes the engine throw.
  - Ctrl+C: on Windows a forced kill skips shutdown hooks, and I couldn't send a real Ctrl+C from automation.

  The API side of `fail` is covered by the database tests, and the checkpoint covers both cases if the final upload doesn't happen.
- **Behind a reverse proxy,** the rate limiter sees the proxy's address unless Tomcat is configured with `RemoteIpValve`. Check this before production.

**Slice 3: Website completion.** Derived runner state, run detail page with the 2×2 measurement view, assignment history, "next run differs" banner, candidate links to configuration pages, Scoring Profile management page, seed-mismatch and field-scope warnings, abandon action.

*Slice 3 notes (done 2026-10-24).* Everything listed is in:
- **Derived Running / Idle / Offline state.** An Offline runner whose run is still marked running says so.
- **Run page** (`OptimizationRunServlet`): the frozen inputs, progress, the candidate list with Evaluate / Select links, and the abandon action. For the best candidate, or any chosen one, it shows the 3x3 table recomputed against the run's own snapshot and weights, plus the 2x2 potential-match collapse.
- **Assignment history.**
- **Runs grouped by profile version and snapshot,** so scores are only shown side by side when they are comparable (D6 item 6).
- **Scoring Profile management** (`ScoringProfileServlet`): list, view with version history, create, and edit as a new version. A description is required, the diagonal weights must be positive, and only the owner may edit.
- **`FieldScope`** in mismo-optimizer, with the measurement field-scope warning on the runner page, the run page and the runner's start summary. The seed-weights warning was already in place from Slice 1.

Verified against localhost Tomcat:
- The run page's recomputed score matched the runner's reported best (86.01%).
- A profile was created and then given a second version.
- An assignment using that profile showed the seed-weights warning on the website and in the runner.
- A live run was abandoned from the website. The runner's next report got `RUN_NOT_ACTIVE`, it kept optimizing locally, and on `exit` it said Mismo Trainer had already ended the run.

Findings:
- **The shipped configuration doesn't match the documented measurement field set.** `optimization-objectives-and-scoring-profiles.md` §1.1 and `matching-engine.md` §10a say mother's maiden name is used in the Not a Match network and gender in Not a Match and Missing. In `mismo-matcher.yml` (and in `legacy-test-data/Configuration.yml`), every gender detector is `enabled: false`, and the only enabled mother's maiden name detectors (`MM-not-match`, `MM-not-similar`) sit inside the disabled `Guardian` group. So neither field is used at all, and because the optimizer can't re-enable detectors, no run from these seeds will ever use them. Logged only, with no code change; Shelby will decide which is right (known-issues.md).
- **`FieldScope` lists detector fields** (`fieldName`, `fieldName2/3`, `fieldNameOther` of enabled detectors). The address detector also reads city and zip internally, which don't appear. The warning is unaffected, because the seed and the core are measured the same way.
- **The heartbeat interval is now 2 minutes** (see §4), to fit §2's 3-minute "seen" window.
- **AIRA users can add versions of the national measurement template profile.** That follows the plan's owner rule, and old versions are kept. Nathan confirmed this is fine for now (2026-10-24): runners aren't planned for use beyond AIRA.

**Slice 4: Distribution.** `jpackage` app-image zip, `README.txt`/`VERSION.txt`, SHA-256, GitHub Release, download link on the pairing page. Test on a clean Windows machine with no Java installed (acceptance criterion 2).

*Slice 4 notes (2026-10-24).*
- **`mismo-runner/package-windows.ps1`** builds the jpackage app-image (`Mismo Runner.exe`, console) with a jlink-trimmed runtime. It adds `README.txt` (analyst instructions) and `VERSION.txt` (version, build time, commit, runtime), zips the result with Windows' `tar.exe`, and writes the zip's SHA-256.
- **The zip is about 28 MB with Java 25 bundled,** about half of D7's estimate.
- **The module list** is `jdeps` output plus `java.logging`, `jdk.crypto.ec` and `jdk.charsets`, which `jdeps` can't see. A runtime with exactly that list made an HTTP/2 https request successfully.
- **Tested on the development machine:** the packaged exe ran a real optimization against localhost Tomcat (run 28: 22 generations, about 245 creatures/second, clean stop).
- **The Runners page links to the download.** The URL and SHA-256 come from `app_setting` (`runner.download.url`, `runner.download.sha256`), so a new release needs no redeploy.
- **The exe isn't code-signed,** so Windows SmartScreen will warn on first launch. The README says how to get past it. Signing is a possible later step.
- **Testing changed (Nathan, 2026-10-24):** instead of a clean-machine test before release, this goes to production and Shelby tests it there (acceptance criterion 2 included), feeding back changes as time allows. Mismo Trainer is not a critical application. The steps are in `production-deployment-checklist.md`, "Island Runner release".

**Slice 5 (optional): Throughput.** Cached creature hashes / `HashSet` dedup check, then parallel scoring if `PatientCompare` proves safe for it. Benchmark generations/minute before and after on the AIRA set.

*Parallel scoring and shared patients done early (2026-10-24), before Slice 2.* `PatientCompare` holds no shared mutable state during scoring: each creature has its own matcher tree (including its own `JaroWinkler` instances), and `MatchNode`'s static `Random` is used only for randomize, tweak, mate and mutate. `World` now parses each case's two patients once (`Patient.prepareForSharing()`, then `PatientCompare.setPatients`, both new in mismo-match), and `scoreAndSort` scores unscored creatures on a pool of one thread fewer than the processor count. Scores are identical to sequential scoring (unit tests compare both). One generation of AIRA-D at population 100 on a 20-processor workstation took 12.1 s before, 8.8 s with shared patients on one thread, and 1.0 s with shared patients on 19 threads (8.3 vs 101 creatures/second). The cached-hash dedup check is still open.

---

## 8. Testing

- **`mismo-optimizer` unit tests:** scoring with instance weights (two `Scorer`s with different weights don't interfere); a fixed creature scores identically on repeated runs; Test Set JSON round trip; hash helper; World generation replacement count (guards against regression of the XOR fix).
- **API handler tests** against the local MySQL dev database: pairing (expired code, reused code), auth (missing, revoked credential), org isolation, assignment snapshot reuse versus new snapshot after a case edit, `ASSIGNMENT_CHANGED`, idempotent candidate upload.
- **End-to-end smoke test** (manual, scripted in the doc): create runner, pair, assign, start, let it run 2 minutes, edit the assignment, confirm the run is unaffected, stop, start again, confirm the new revision is used.

---

## 9. Open items for Nathan

None currently.

**Resolved (2026-10-24):** the canonical matrix is intentional (national measurement objective). It is documented in `optimization-objectives-and-scoring-profiles.md` and implemented as Scoring Profiles (D6). Twins landing in Possible Match is an accepted consequence of the narrow field scope and inclusive lean, not a target; field scope is made visible (D6 item 9).

**Resolved (2026-10-24):**

- The `jpackage` app-image `.exe` is wanted (D7, Slice 4).
- GitHub Releases on `immregistries/mismo-trainer` is the download location.
- Runs may use `DRAFT` Test Sets, with a warning (D3).
