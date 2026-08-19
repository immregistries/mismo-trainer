# Local Database Refresh Scripts

Mirrors InteropHub's `T:\scripts\python\refresh_interophub_db.py` and
`restore_interophub_db_from_latest_local.py` (documented behavior copied from
`docs/database-release-practice.md` in that repo — the actual scripts aren't accessible outside
that machine, so these are new scripts implementing the same documented pipeline, not copies).

**Location:** `C:\dev\mismo\scripts\` — outside this git repository, same as InteropHub's own
equivalents live outside that repo. Not tracked in version control.

**Status:** Written and tested against the real local database (see §4). **Not scheduled** —
deliberately not wired into any hourly/daily runner yet, per explicit instruction. Running them is
a manual, on-demand action until that changes.

---

## 1. Files

| File | Role |
|---|---|
| `mismo_db_refresh_common.py` | Shared pipeline logic both scripts call — no need to run this directly. |
| `refresh_mismo_db.py` | The daily-intended script. No-ops if the local backup cache has nothing newer than what's already applied. |
| `restore_mismo_db_from_latest_local.py` | The on-demand script. Always re-runs the pipeline against whatever the most recent local backup is, regardless of whether it's already been applied — for resetting local state mid-testing. |

---

## 2. What the pipeline actually does

Both scripts run the same steps (`mismo_db_refresh_common.run_restore_pipeline`):

1. Drop and recreate the local `mismo` database.
2. Import the backup file.
3. Apply `src/db/local_database_refresh.sql` (production values → local equivalents, e.g. the InteropHub URL).
4. Apply `src/db/unapplied_updates.sql`, if non-empty.
5. Regenerate `src/db/schema.sql` (`mysqldump --no-data --routines --events --triggers`, banner-marked auto-generated, matching `docs/production-deployment-plan.md` §3).

`refresh_mismo_db.py` additionally checks a small local state file
(`scripts/.refresh_mismo_db_state.json`, not tracked in git) to skip re-running the pipeline if
the most recent backup in the cache is the same one it already applied last time — this is the
"no-op if nothing new" behavior from the InteropHub pattern. `restore_mismo_db_from_latest_local.py`
has no such state and always runs, on purpose.

---

## 3. Configuration

Everything is overridable via environment variables; sensible local-dev defaults are baked in.

| Variable | Default | Purpose |
|---|---|---|
| `MISMO_REPO_DIR` | `C:\dev\mismo\mismo-trainer` | Where to find `src/db/*.sql` and write `schema.sql`. |
| `MISMO_BACKUP_CACHE_DIR` | `C:\dev\mismo\db` | Where dated backup files (`mismoYYYYMMDD.sql`) are looked for — matches the naming convention already in use there. |
| `MISMO_DATABASE_NAME` | `mismo` | — |
| `MISMO_DB_ADMIN_HOST` | `localhost` | — |
| `MISMO_DB_ADMIN_USER` | `root` | **Must** be able to `DROP`/`CREATE DATABASE` — the application's own `mismo_web` account (`MISMO_DB_USER`) only has grants on the one database and can't do this. |
| `MISMO_DB_ADMIN_PASSWORD` | *(none — required)* | Not defaulted, not hardcoded anywhere in these scripts, same rule as the application's own `MISMO_DB_PASSWORD`. The script exits immediately with a clear message if it's unset. |
| `MYSQL_BIN_DIR` | `C:\Program Files\MySQL\MySQL Server 8.1\bin` | Where `mysql.exe`/`mysqldump.exe` live. |

---

## 4. Verified

Ran `restore_mismo_db_from_latest_local.py` for real against the local database (2026-08-19), using the fresh `mismo20260819.sql` export in `C:\dev\mismo\db`:

- Drop/recreate, import, and `local_database_refresh.sql` all completed cleanly.
- `schema.sql` regenerated correctly — banner present, 12 `CREATE TABLE` statements, matching the live schema.
- Confirmed the restored database has real data intact (29,221 `match_item` rows, 11 `match_set`, 4 `evaluation`, matching known counts from before the restore) — not an empty shell.

**One expected, not-a-bug failure hit during this test:** applying `unapplied_updates.sql` failed, because at the time it still contained *every* schema change from Phases 2–11 — and the backup it was being applied on top of was dumped from this same, already-fully-migrated local database, so everything in the file already existed. Not a script defect; the expected state of a project that hadn't done its first production release yet.

**Resolved:** `unapplied_updates.sql` has since been frozen into `src/db/upgrade-2.0.sql` and reset to empty (`docs/production-deployment-plan.md` §5). From here on, a production backup won't already contain whatever's newly accumulated in `unapplied_updates.sql`, so this step applies cleanly as intended.

---

## 5. Not yet wired up

**How a production backup actually reaches the local cache directory.** `refresh_mismo_db.py` has a marked `TODO` where InteropHub's equivalent presumably downloads the latest backup from wherever production backups land. That mechanism doesn't exist for this project yet — it depends on whatever the installing engineer sets up during the first production deploy (see the checklist). Until it's decided and wired in, both scripts only look at whatever's already sitting in `C:\dev\mismo\db` — dropping a new file there by hand (matching the existing `mismoYYYYMMDD.sql` naming) works fine as a manual stand-in.

**Scheduling.** Neither script is hooked into any runner. Once the transport above exists and this has been used successfully a few times by hand, wiring `refresh_mismo_db.py` into an hourly/daily scheduler (matching InteropHub's `run_hourly.py` pattern) is the natural next step — deliberately not done yet.
