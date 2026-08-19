# Production Deployment Plan

**Status:** §1's credential mechanism and the file cleanup in §2 are implemented and verified locally (see §6). The rest — the initial production seed and the ongoing refresh cycle — remains a plan, not yet executed, since production doesn't exist yet. Copies InteropHub's already-battle-tested credential and database-release pattern (`C:\dev\immregistries\InteropHub`, read directly for this doc — see `docs/database-release-practice.md` there for the full original) rather than inventing a new one, per explicit direction to match that project's convention.

**Companion:** `v2-roadmap.md` §13 already describes the shape of this cycle (pull a fresh copy of production, apply `unapplied_updates.sql`, release, cycle the file out) — this document makes it concrete and adds the pieces that were missing: credential handling, the local-refresh direction, and the initial seed.

---

## 1. Credentials: environment variables, never checked in

### What InteropHub does (copy this exactly, renamed)

`hibernate.cfg.xml` has **no username or password property at all** — only `driver_class` and a `connection.url` with a bare localhost default (safe to commit; it has no secret in it). A small `HibernateUtil` class loads the XML, then overrides four properties from environment variables before calling `buildSessionFactory()`:

| Hibernate property | Primary env var | Alias env var | Required? |
|---|---|---|---|
| `hibernate.connection.driver_class` | `INTEROPHUB_DB_DRIVER` | `INTEROPHUB_DRIVER` | No — falls back to the XML default |
| `hibernate.connection.url` | `INTEROPHUB_DB_URL` | *(none)* | No — falls back to the XML default |
| `hibernate.connection.username` | `INTEROPHUB_DB_USER` | `INTEROPHUB_USER` | **Yes — throws `IllegalStateException` at startup if unset** |
| `hibernate.connection.password` | `INTEROPHUB_DB_PASSWORD` | `INTEROPHUB_PASSWORD` | **Yes — same** |

Driver/URL are allowed a checked-in default because they aren't secret and a sensible localhost default makes local dev friction-free. Username/password are never allowed a default — missing either one fails the deployment loudly at startup instead of silently connecting with something wrong (or not connecting at all with a confusing error three layers down).

### Mismo-Trainer equivalent (to implement)

Same structure, `MISMO_` prefix instead of `INTEROPHUB_`:

| Hibernate property | Primary env var | Alias env var |
|---|---|---|
| `hibernate.connection.driver_class` | `MISMO_DB_DRIVER` | `MISMO_DRIVER` |
| `hibernate.connection.url` | `MISMO_DB_URL` | — |
| `hibernate.connection.username` | `MISMO_DB_USER` | `MISMO_USER` |
| `hibernate.connection.password` | `MISMO_DB_PASSWORD` | `MISMO_PASSWORD` |

Concretely:
1. Remove `hibernate.connection.username`/`connection.password` from `src/main/resources/hibernate.cfg.xml` entirely — they currently hold the real, committed `mv_web`/`cArn88r0w` credential (confirmed still there).
2. Add a `HibernateSessionFactorySupport` class (or similarly named, mirroring InteropHub's `HibernateUtil`) that loads the XML and applies the environment-variable overrides above, requiring username/password and failing fast if either is missing.
3. **Route every `SessionFactory` bootstrap through it** — today there are two independent call sites (`HomeServlet.getSessionFactory()` and `TemplateDataBootstrap.main()`, both calling `new AnnotationConfiguration().configure().buildSessionFactory()` directly); both need to go through the shared class instead of duplicating the override logic.
4. Local dev sets `MISMO_DB_USER`/`MISMO_DB_PASSWORD` in the developer's own shell/IDE environment (never in a checked-in file); production sets them however that server manages environment/service configuration.

### Also worth adopting from InteropHub while touching this

- **`hibernate.hbm2ddl.auto=validate`** — InteropHub sets this explicitly (it wasn't always; their doc notes it used to be `update`, which silently auto-created missing tables/columns and once masked a skipped release step until the app was already running against an incomplete schema). Mismo-Trainer doesn't set `hbm2ddl.auto` at all today, which defaults to doing nothing — meaning a mismatch between the Java entity mappings and the actual database schema currently isn't caught until something happens to query the mismatched table/column. Setting `validate` makes that fail loudly at startup instead. Recommended.

---

## 2. `create-user.sql`, `initial.sql`, `db-dump-2024-04-05.sql`

Already agreed these are stale:
- Delete `initial.sql` and `db-dump-2024-04-05.sql` outright — both describe a schema years out of date and aren't part of this workflow.
- Replace `create-user.sql` with a template containing **no real password** — a placeholder plus a one-line instruction to generate a fresh one per environment. The account itself (`mv_web`, `GRANT ALL ... ON matching_validation.*`) still needs creating by hand on any new server; `mysqldump`/restore never carries user accounts or grants along with a single database's data.

---

## 3. Files this repo should carry, mirroring InteropHub's `db/` layout

| File | Role | Hand-edited? |
|---|---|---|
| `src/db/unapplied_updates.sql` | Already exists — schema/data changes for the next release, not yet applied to production. Unchanged practice. | Yes |
| `src/db/upgrade-N.N_description.sql` | One frozen record per past release, copied from `unapplied_updates.sql` at release time, never edited after. Mismo-Trainer already has this precedent (`upgrade-1.1.sql`) — **keep that naming** rather than adopting InteropHub's `vX.Y_description.sql` verbatim; the concept is what's being copied, not the exact filename convention. | Yes, once |
| `src/db/schema.sql` (new) | Full-schema reference snapshot (`mysqldump --no-data --routines --events --triggers`), regenerated after every local refresh. **Auto-generated — never hand-edited.** Give it the same `DO NOT HAND-EDIT` banner InteropHub uses; it exists purely as a human/agent-readable "what does the schema look like right now" reference, not something the app or build consumes. | No — generated |
| `src/db/local_database_refresh.sql` (new) | Rewrites production-specific values to their local-dev equivalents after a production snapshot is restored locally. For Mismo-Trainer, concretely: `UPDATE app_setting SET setting_value = '<local Hub URL>' WHERE setting_key = 'hub.external.url';` — without this, a freshly-refreshed local dev database would have the InteropHub client pointed at *production's* Hub instead of the local one. | Yes |

---

## 4. Initial production seed (one-time, since production doesn't exist yet)

Per the decision to dump the current dev database rather than replay old scripts (the current schema has already dropped the plaintext-password `user` columns, so that risk doesn't apply to a current-state dump):

1. On the new server: create the MySQL 8.0 database and the `mv_web` user (`create-user.sql`'s template, with a freshly generated password — set as that server's `MISMO_DB_PASSWORD`).
2. From here: `mysqldump -u<user> -p --single-transaction --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 matching_validation > matching_validation.sql`.
3. Transfer that file out-of-band (it is **not** checked into the project, per your direction) and restore it on the new server.
4. Apply `local_database_refresh.sql`-equivalent logic in the *other* direction here — i.e. make sure `app_setting.hub.external.url` on production points at production's real InteropHub instance, not local dev's.
5. Register the app in production's InteropHub instance (app code, callback URL matching wherever this deploys) and confirm `pom.xml`'s `<finalName>mismo</finalName>` matches the intended context path.
6. Deploy the WAR; confirm `MISMO_DB_USER`/`MISMO_DB_PASSWORD` are set in that server's environment before Tomcat starts it.

No need to touch `legacy-test-data/`/`TemplateDataBootstrap` for this seed — the dump already carries the real, already-bootstrapped AIRA template rows.

---

## 5. Ongoing cycle, once production is live

This is the same shape as InteropHub's daily refresh, split into what lives in this repo vs. what's the operator's own tooling outside it (InteropHub's own equivalent scripts live outside its repo too, on the operator's machine — not something other developers or agents can see or run).

### Releasing a schema change to production (in this repo, manual, matches `v2-roadmap.md` §13 with the frozen-file step made explicit)

1. Back up the production database first.
2. Apply `src/db/unapplied_updates.sql` to production directly.
3. Verify the application against production.
4. Copy `unapplied_updates.sql`'s contents into a new `src/db/upgrade-N.N_description.sql` (next version number, short description) — permanent, never edited again after creation.
5. Empty `unapplied_updates.sql` back to nothing, ready to accumulate the next round.
6. Commit the new frozen file and the emptied `unapplied_updates.sql` together with the application code that depends on them, in one commit.

### Refreshing local dev from production (outside this repo — your own automation)

The sequence your daily-download tooling needs to perform, mirroring InteropHub's `refresh_interophub_db.py`:

1. Download the latest production backup (skip if already downloaded today).
2. Drop and recreate the local `matching_validation` database.
3. Import the production snapshot.
4. Apply `src/db/local_database_refresh.sql` (production URLs/secrets → local equivalents).
5. Apply `src/db/unapplied_updates.sql`, if non-empty (so local dev reflects production *plus* whatever's pending release).
6. Regenerate `src/db/schema.sql` via `mysqldump --no-data --routines --events --triggers`.

This repo provides everything steps 4–6 need (the two SQL files, and the `schema.sql` command); the download/orchestration itself (steps 1–3, and the scheduling) is yours to build, the same way InteropHub's is external to that repo.

---

## 6. Implemented and verified locally

- `HibernateSessionFactorySupport` (`org.immregistries.mismo.trainer`) — direct port of InteropHub's `HibernateUtil`, `MISMO_` prefix. Both `HomeServlet.getSessionFactory()` and `TemplateDataBootstrap.main()` route through it now instead of calling `AnnotationConfiguration` directly.
- `hibernate.cfg.xml` — username/password removed entirely; `hibernate.hbm2ddl.auto=validate` added.
- `create-user.sql` rewritten with a placeholder password and `caching_sha2_password` instead of the legacy `mysql_native_password`; `initial.sql` and `db-dump-2024-04-05.sql` deleted.
- `src/db/local_database_refresh.sql` added (rewrites `app_setting.hub.external.url` to the local Hub).
- Verified end to end with a standalone smoke test (not just compiled): confirmed `HibernateSessionFactorySupport.build()` fails fast with `IllegalStateException` when `MISMO_DB_USER`/`MISMO_DB_PASSWORD` are unset, and succeeds — including a full `validate`-mode schema check — once they're set.

**Two real bugs found and fixed along the way, surfaced by turning on `hbm2ddl.auto=validate` for the first time:**
- `Configuration.hbm.xml` mapped `generatedDate` as Hibernate type `date` (date-only) against an actual `datetime` column — every configuration row's generation time-of-day has likely been silently truncated on read since this field was introduced. Fixed to `type="timestamp"`.
- `Configuration.hbm.xml`/`MatchItem.hbm.xml` mapped `configuration_script`, `patient_data_a`, and `patient_data_b` as Hibernate type `string` (implies `varchar`) against actual `text` columns. Fixed to `type="text"`. (`review_notes` and `match_item_review.notes` were already mapped correctly.)
- The local `mv_web` MySQL account also had a stray `GRANT ALL ... ON aart.*` (a sibling project's database, not this one) alongside its legitimate `matching_validation` grant. With that in place, Hibernate 3.6's schema validator — which queries table metadata with a null catalog — non-deterministically inspected `aart`'s tables instead of this project's, producing false mismatch errors. Revoked; confirmed via `SHOW GRANTS` that only `matching_validation` access remains, and `hbm2ddl.auto=validate` passes cleanly afterward. Worth checking for the same stray grant on any other shared local MySQL account before assuming `validate` will behave on a different machine.

**Not done — needs your input:**
- `MISMO_DB_USER`/`MISMO_DB_PASSWORD` are set at **User scope** on this machine (`mv_web` / the existing password, unchanged — not rotated, since rotating it also means changing the actual MySQL account's password in lockstep, a deliberate separate step rather than a side effect of this refactor). Setting them at **Machine scope failed** — "Requested registry access is not allowed," this session isn't elevated. User-scope variables aren't visible to a process started as a Windows Service (which is how the local Tomcat instance appears to be running, based on an earlier session's note that it couldn't be stopped/started from here either) — a service reads its environment once, at its own start, from the Machine scope (or from environment configured specifically for that service). Two ways to finish this, your call: run `[Environment]::SetEnvironmentVariable('MISMO_DB_USER','mv_web','Machine')` (and the password) from an elevated PowerShell yourself, or set them directly on the Tomcat service's own environment via its service-configuration utility (e.g. `tomcat9w.exe`'s "Java" tab) — the second is generally the more robust option for a service specifically, rather than relying on machine-wide variables. Either way, **the Tomcat service needs a restart** afterward to pick up the new environment — variables set after a process starts aren't retroactively visible to it.

## 7. Open items

- Exact production server details (hostname, how environment variables get set for that Tomcat instance, backup storage/retrieval mechanism for the dump) — not yet specified; needed before step 4 (initial seed) can actually run.
- Whether `src/db/schema.sql` should exist yet, given local refresh automation doesn't exist yet either — fine to add once the refresh script is real; no value sitting unused before then.
