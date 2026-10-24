# Production Deployment Checklist — Mismo-Trainer v2.0 (First Deploy)

Follows the same conventions as InteropHub. Full rationale in `docs/production-deployment-plan.md`
if anything here needs more context than a checklist gives.

**Stack:** Java 17, Tomcat 10.1.x, MySQL 8.0, `mysql-connector-java:8.0.30` / `com.mysql.cj.jdbc.Driver`.

**Provided out of band, not in the repo:** `mismo20260819.sql` (full dump of the current dev database
— this *is* the initial data, not an empty schema. It already contains the AIRA organization row
and its template Test Set/Configuration, and already has `app_setting.hub.external.url` and
`app_setting.mismo.external.url` set to their production values — nothing to edit in it before
importing).

---

## Engineer: server setup

- [ ] Create the `mismo` database (`utf8mb4` / `utf8mb4_0900_ai_ci`).
- [ ] Create the `mismo_web` MySQL account with a **freshly generated password**. Template:
  `mismo-trainer-web/src/db/create-user.sql` (`caching_sha2_password`, MySQL 8's default — confirm this server isn't
  configured to require something else). Grants only `ALL PRIVILEGES` on `mismo.*` — nothing broader,
  no `GRANT OPTION`.
- [ ] Import `mismo20260819.sql` into `mismo`.
- [ ] `mvn clean package` from the repo root (the parent pom builds all three modules) →
  `mismo-trainer-web/target/mismo.war` (`finalName` is already set in `mismo-trainer-web/pom.xml`;
  don't override it — the InteropHub app registration below depends on it deploying at `/mismo`).
- [ ] Before starting Tomcat, set in its environment (service-level, not just an interactive shell —
  see `docs/production-deployment-plan.md` §6 for why that distinction matters):
  - `MISMO_DB_USER=mismo_web`
  - `MISMO_DB_PASSWORD=<the password you generated>`
  - Only if MySQL isn't reachable at `localhost:3306` from this box: `MISMO_DB_URL=jdbc:mysql://<host>/mismo`
- [ ] Deploy `mismo.war`, confirm it comes up at `/mismo`.

## Engineer: InteropHub registration

- [ ] Register app code `mismo` in the production InteropHub instance
  (`https://informatics.immregistries.org/hub`), callback `https://informatics.immregistries.org/mismo/login`.

## Engineer: verify

- [ ] Have someone from `@immregistries.org` log in once via SSO. The AIRA organization
  (`domain = 'immregistries.org'`, `is_template_org = 1`) and its template Test Set/Configuration are
  already in the imported data — login just attaches this user to that existing organization
  (matched by email domain), nothing gets created or re-run.
- [ ] Confirm the AIRA templates are visible/usable from that login: one `match_set` (label
  `AIRA-D`) and one `configuration` (world `AIRA Template`), both `is_template = 1`.

## Engineer: the one open question — hand back to Nathan

- [ ] **Set up production backups of `mismo` the same way InteropHub's production database is
  already backed up**, and tell Nathan how to retrieve the latest one from this environment. This is
  the missing piece in `refresh_mismo_db.py` (see `docs/db-refresh-scripts.md` §5) — it currently
  only looks at a local folder, nothing fetches a new backup automatically yet, because this project
  didn't have a production backup source to fetch from until now.

---

## Nathan: after the engineer confirms it's working

- [ ] Verify the app end to end against production (log in, browse a Test Set, run an Evaluation).
- [x] Freeze the release, per `docs/production-deployment-plan.md` §5 — done: `mismo-trainer-web/src/db/upgrade-2.0.sql`
  now holds the full Phases 2-11 change history, and `unapplied_updates.sql` is reset to just its
  purpose comment, ready to accumulate whatever comes after v2.0. Still needs committing (see below).
- [x] Rename `matching_validation` → `mismo` and `mv_web` → `mismo_web` everywhere (repo, refresh
  scripts, local backups) — done, since production didn't exist yet this was the right time to do it
  cleanly rather than carry the legacy name forward. See `docs/production-deployment-plan.md` §6.
- [ ] Once the engineer reports back on backup retrieval: wire the actual transport into
  `refresh_mismo_db.py`'s marked `TODO`, test it once by hand, then decide when to schedule it (still
  deliberately not wired into any runner yet).

---

## Island Runner release (Slices 0–4, 2026-10-24)

The centrally managed Island Runner (`docs/island-runner-implementation-plan.md`) goes to production
for **Shelby to test there**, instead of the planned clean-machine test. Mismo Trainer is not a
critical application. Shelby runs it, reports what needs changing, and changes are made as time
allows.

**Database** (after the daily backup, as usual):

- [ ] Check which blocks of `mismo-trainer-web/src/db/unapplied_updates.sql` production already
  has. The earlier blocks (Pilot 3 template flag, `user_configuration_recent`, corrected default
  configuration) predate the runner work. Then apply the rest. The runner blocks:
  - create `runner`, `scoring_profile`, `runner_assignment`, `test_set_snapshot`, `optimization_run`;
  - add `configuration.content_sha256` / `optimization_run_id` and backfill the hashes;
  - drop `configuration.island_credential_id` and the `island_credential` table;
  - seed the "National measurement (potential match)" profile;
  - add `app_setting` rows `runner.min.client.version` (2.0), `runner.download.url` and
    `runner.download.sha256` (empty until the zip is published).
- [ ] Afterwards, freeze the release per `docs/production-deployment-plan.md` §5: copy what was
  applied into a new `upgrade-2.1.sql` and reset `unapplied_updates.sql`, and consider bumping the
  pom version to 2.1. Runners report the pom version, and `runner.min.client.version` compares
  against it.

**Webapp:**

- [ ] On the build machine, pull `mismo-match` and run `mvn install` there first. This release uses
  methods added to mismo-match on 2026-10-24 (`Patient.prepareForSharing`,
  `PatientCompare.setPatients`) without a version bump (still 1.1). An older 1.1 jar in the local
  Maven repository fails to compile mismo-optimizer.
- [ ] `mvn clean package` from the repository root; deploy `mismo-trainer-web/target/mismo.war`.
- [ ] Optimization > Runners and Optimization > Scoring Profiles load, and the national
  measurement profile is listed.
- [ ] If Tomcat sits behind a reverse proxy, configure `RemoteIpValve` (or equivalent).
  Otherwise the pairing rate limit (10 failures per address per 15 minutes) sees one address for
  everybody, and one person's typos can lock everyone out of pairing for 15 minutes.

**Runner download** (Windows, JDK 17+ with `jpackage`):

- [ ] After `mvn clean package`, run
  `powershell -ExecutionPolicy Bypass -File mismo-runner\package-windows.ps1 -JdkHome <JDK>`.
  It writes `mismo-runner/target/release/Mismo-Runner-<version>-windows.zip` (about 28 MB, with
  Java 25 bundled) and its `.sha256`. Build from a clean, committed tree: `VERSION.txt` in the zip
  records the commit and says if there were uncommitted changes.
- [ ] Publish a GitHub Release on `immregistries/mismo-trainer`, tag `runner-v<version>`, with the
  zip attached and the SHA-256 in the notes.
- [ ] Set `app_setting.runner.download.url` to the asset URL (the seeded value assumes tag
  `runner-v2.0`) and `runner.download.sha256` to the zip's hash. The Runners page shows both.

**Shelby's first run** (the acceptance criteria of `docs/centrally-managed-local-island-runner.md`):

- [ ] Download and unzip, start `Mismo Runner.exe` with no Java installed, pair, assign, start,
  watch progress on the website, change the assignment mid-run, stop, start again. Population 100
  is enough to test the mechanics quickly; it's not enough to optimize well.
- [ ] Report anything confusing or broken. `%LOCALAPPDATA%\Mismo Runner\logs` is the most useful
  thing to send with a report.

