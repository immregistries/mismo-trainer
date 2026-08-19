# Production Deployment Checklist — Mismo-Trainer v2.0 (First Deploy)

Follows the same conventions as InteropHub. Full rationale in `docs/production-deployment-plan.md`
if anything here needs more context than a checklist gives.

**Stack:** Java 17, Tomcat 10.1.x, MySQL 8.0, `mysql-connector-java:8.0.30` / `com.mysql.cj.jdbc.Driver`.

**Provided out of band, not in the repo:** `mismo20260819.sql` (full dump of the current dev database
— this *is* the initial data, not an empty schema. It already contains the AIRA organization row
and its template Test Set/Configuration, and already has `app_setting.hub.external.url` set to
production's InteropHub URL — nothing to edit in it before importing).

---

## Engineer: server setup

- [ ] Create the `mismo` database (`utf8mb4` / `utf8mb4_0900_ai_ci`).
- [ ] Create the `mismo_web` MySQL account with a **freshly generated password**. Template:
  `src/db/create-user.sql` (`caching_sha2_password`, MySQL 8's default — confirm this server isn't
  configured to require something else). Grants only `ALL PRIVILEGES` on `mismo.*` — nothing broader,
  no `GRANT OPTION`.
- [ ] Import `mismo20260819.sql` into `mismo`.
- [ ] `mvn clean package` from the repo root → `target/mismo.war` (`finalName` is already set in
  `pom.xml`; don't override it — the InteropHub app registration below depends on it deploying at
  `/mismo`).
- [ ] Before starting Tomcat, set in its environment (service-level, not just an interactive shell —
  see `docs/production-deployment-plan.md` §6 for why that distinction matters):
  - `MISMO_DB_USER=mismo_web`
  - `MISMO_DB_PASSWORD=<the password you generated>`
  - Only if MySQL isn't reachable at `localhost:3306` from this box: `MISMO_DB_URL=jdbc:mysql://<host>/mismo`
- [ ] Also set as a JVM system property on Tomcat's startup command (e.g. `CATALINA_OPTS`, or the
  "Java Options" field if Tomcat runs as a Windows service) — **not an env var, a `-D` flag**:
  - `-Dmismo.external.url=https://informatics.immregistries.org/mismo`

  This is this app's own externally-reachable base URL (`HubClientSupport.MISMO_EXTERNAL_URL`), used
  to build the InteropHub login redirect/callback. It defaults to `http://localhost:8080/mismo` if
  left unset — SSO login will silently redirect to the wrong host if this is missed.
- [ ] Deploy `mismo.war`, confirm it comes up at `/mismo`.
- [ ] Deploy the `legacy-test-data/` folder (from the repo root) alongside the app — **not** inside
  the WAR, it's deliberately excluded from packaging. Used at runtime by the Test Set explore/upload
  pages, not just one-time setup.

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
- [x] Freeze the release, per `docs/production-deployment-plan.md` §5 — done: `src/db/upgrade-2.0.sql`
  now holds the full Phases 2-11 change history, and `unapplied_updates.sql` is reset to just its
  purpose comment, ready to accumulate whatever comes after v2.0. Still needs committing (see below).
- [x] Rename `matching_validation` → `mismo` and `mv_web` → `mismo_web` everywhere (repo, refresh
  scripts, local backups) — done, since production didn't exist yet this was the right time to do it
  cleanly rather than carry the legacy name forward. See `docs/production-deployment-plan.md` §6.
- [ ] Once the engineer reports back on backup retrieval: wire the actual transport into
  `refresh_mismo_db.py`'s marked `TODO`, test it once by hand, then decide when to schedule it (still
  deliberately not wired into any runner yet).
