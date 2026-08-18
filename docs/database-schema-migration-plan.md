# Mismo-Trainer v2 Database Schema Migration Plan

## 1. Purpose

This document defines the initial database changes needed for the Mismo-Trainer v2 modernization.

The immediate goals are to:

- replace the legacy Mismo-Trainer username/password login with InteropHub authentication;
- maintain a local Mismo-Trainer user record for attribution and application relationships;
- organize users and data by organization;
- prevent users from seeing or changing another organization's data;
- support lightweight created/updated attribution;
- authenticate Island optimization processes separately from human users;
- add database-enforced foreign keys where practical;
- preserve the existing training/test data and configuration model unless a change is required for these goals.

This is intentionally a minimal migration plan. It does not attempt to redesign every legacy Mismo-Trainer concept at once.

---

## 2. Core Design Decisions

### 2.1 InteropHub is the identity authority

Mismo-Trainer will not maintain passwords.

On login, InteropHub authenticates the user and returns identity information including:

- `hub_user_id`
- email
- display name
- first name
- last name
- organization
- title

Mismo-Trainer will maintain its own local `user_id` because application records already need a durable local foreign key.

`hub_user_id` will be the stable external identity used to find the local Mismo-Trainer user.

Mismo-Trainer should require a non-null/non-blank `hub_user_id` even if the shared InteropHub client library does not currently require it.

`HubUserInfo.getHubUserId()` returns a `String` (parsed from the Hub's `hub_user_id` JSON field, but never converted to a numeric type by the client library). Mismo-Trainer should treat `hub_user_id` as an opaque string identifier end to end — store it as a string column, and reject login with a non-blank check rather than assuming it is numeric.

### 2.2 Users are provisioned automatically

Any user successfully authorized by InteropHub may enter Mismo-Trainer.

On first login:

1. Find the local user by `hub_user_id`.
2. If none exists, create the user.
3. Assign the user to an organization.
4. Start the Mismo-Trainer session.

There is no separate Mismo-Trainer approval workflow.

### 2.3 Organization is the tenant boundary

Each ordinary user belongs to one organization.

Users may see and modify data belonging to their own organization only.

All ordinary users initially have the same rights within their organization.

Organization membership is therefore a security boundary, not merely display metadata.

### 2.4 Email domain is only an initial organization-assignment mechanism

For the first implementation, `organization` will contain a single optional email domain.

When the first user from an unknown private domain signs in:

1. create an organization using the organization name supplied by InteropHub;
2. store the user's email domain on that organization;
3. assign the user to that organization.

Subsequent users with that same domain are assigned to the existing organization.

This is intentionally provisional. InteropHub may later provide authoritative organization identifiers and membership. The schema should therefore avoid building a complicated domain-management model.

Public/shared email domains such as `gmail.com`, `outlook.com`, and `yahoo.com` must not automatically group unrelated users into one organization. Users from such domains should receive a separate organization unless organization membership is otherwise explicitly assigned.

After the initial user record has been created, email-domain changes should not automatically move the user between organizations.

### 2.5 InteropHub profile fields are refreshed on login

InteropHub remains authoritative for the user's:

- email;
- first name;
- last name;
- display name;
- title.

These fields should be updated in the local user row after successful authentication.

`organization_id` is different: after initial assignment, it remains a Mismo-Trainer relationship and is not automatically recalculated from the current email domain.

### 2.6 Island authentication is separate

Island optimization processes are not InteropHub users.

Mismo-Trainer will issue an Island credential associated with an organization. The Island uses that credential when communicating with the central Trainer.

The raw credential should only be shown when created. The database stores a hash of the credential rather than the credential itself.

### 2.7 Mismo-Trainer defines and owns its persistence model, separate from Mismo-Match

`User`, `MatchItem`, `MatchSet`, and `Configuration` are currently Hibernate-mapped to Java classes that live inside the compiled `mismo-match-1.1.jar` dependency (`org.immregistries.mismo.match.model.*`), not in Mismo-Trainer's own source. Mismo-Trainer ships only the `.hbm.xml` mapping files; the POJOs themselves belong to the match engine, which is frozen at Java 8 and is not being modified as part of this migration.

None of the new columns and relationships in this plan can be added to those classes without rebuilding `mismo-match`, which is out of scope. Mismo-Trainer v2 will therefore define its **own** entity classes for every table in this document — `org.immregistries.mismo.trainer.model.User`, `Organization`, `IslandCredential`, and trainer-local replacements for `MatchSet`, `MatchItem`, and `Configuration` — each with its own `.hbm.xml` mapping, replacing the mappings that currently point at the `mismo-match`-jar classes.

This is a deliberate, welcome side effect, not just a workaround: it decouples what Mismo-Trainer needs to *persist* from what Mismo-Match needs to *compute with*. Mismo-Trainer continues to use `mismo-match`'s own `Patient`, `Configuration`, `PatientCompare`, and `PatientMatcher` classes exactly as today, purely in memory, for the actual matching/scoring calls (e.g. building a `Patient` from `match_item.patient_data_a`/`patient_data_b`, or loading a `configuration_script` into a `PatientCompare`) — only the persistence layer moves to trainer-owned classes.

Every table definition in section 3 below assumes this: a "Proposed" column list describes a trainer-owned entity and mapping, not an extension of the existing `mismo-match`-jar-sourced classes.

### 2.8 Every application page requires InteropHub login

There is no anonymous/guest mode anywhere in Mismo-Trainer v2. Every page a human can reach requires a successful InteropHub session. This retires the v1 behavior where `TestMatchingServlet` and `TestScriptExploreServlet` allowed an anonymous visitor to score the bundled flat-file test corpora without logging in — that guest branch is removed, not preserved behind an allowlist.

The one exception is machine-to-machine access: the Island optimization API (`CentralServlet`'s `doPost` endpoints) is not an InteropHub user and does not go through the login filter. It authenticates separately via `island_credential` (§2.6, §3.6). The authentication filter's public-path allowlist should therefore be effectively empty for human-facing URLs — limited to the login callback itself and any static asset paths — and should explicitly exclude the Island API path rather than exempt it by omission.

### 2.9 The bundled flat-file test corpora remain global and unscoped for now

**Superseded by §2.10.** This section originally deferred the bundled `MIIS-*.txt`/`AIRA-*.txt` files' removal to "a later phase." That phase is now — §2.10 below moves the one starter test set and starter configuration Mismo-Trainer actually needs out of the classpath and into the database, and retires the files.

### 2.10 A real organization is the source of shared starter data — "templates"

Every new organization should be able to see and use a starting point (a labeled test set and a weight-set configuration) without needing production data pulled in from anywhere, and without every organization getting its own redundant private copy of the same starter material. The mechanism is a **template**: a `match_set` or `configuration` row that any organization can read and copy, but only its owning organization can edit.

**There is no synthetic "system" organization.** The templating organization is a real one — AIRA (`*@immregistries.org`), the same organization Phase 3's email-domain auto-provisioning already creates the first time someone from that domain logs in. Two flags realize this:

- **`organization.is_template_org`** (boolean, default `false`) — marks which organization's content is *eligible* to be published as a template. Set `true` on the AIRA organization only.
- **`match_set.is_template`** / **`configuration.is_template`** (boolean, default `false`) — the actual publish switch, set row by row. The application must enforce that this can only be set `true` on a row whose owning organization has `is_template_org = true` — this is what "AIRA controls the template" means concretely: they choose, row by row, what becomes a public starting point. Nothing is exposed just by being AIRA's data.

**Access rules (extends §4's tenant enforcement):**

- **Read:** an organization can read/browse/score against a row belonging to its own organization, **or** any row with `is_template = true`, regardless of which organization owns it.
- **Write:** unchanged — only the *owning* organization can edit or delete a row, template or not. AIRA keeps full ongoing control over its own template data, including continuing to refine it after publishing; no other organization gets write access to it under any circumstance.
- **Copy:** any organization can copy a template `match_set` (and all of its `match_item` rows) or a template `configuration` into a brand-new row owned by its own organization. This is the only way a non-owning organization gets an editable version — there is no in-place "fork" that retains a link back to the template.

**Migration off the bundled classpath files:**

1. Move `src/main/resources/{AIRA-A,AIRA-B,AIRA-C,AIRA-D,AIRA-2026-A,AIRA-2026-B}.txt`, `{MIIS-B,MIIS-C,MIIS-D,MIIS-E,MIIS-E2,MIIS-E3,MIIS-F1,MIIS-F2}.txt`, and `Configuration.yml` to a new top-level `legacy-test-data/` folder (sibling to `src/`). Maven only packages `src/main/resources`/`src/main/webapp` into the WAR, so this alone removes them from the deployed application. Kept there for reference and as the one-time import source, not shipped.
2. A one-time, idempotent (check-before-insert, matching `LoginServlet`'s org-resolution style) bootstrap loads `AIRA-D.txt` into a new AIRA-owned, `is_template=true` `match_set` — reusing `TestSetUploadServlet`'s existing `TEST:`/`EXPECT:`/`PATIENT A:`/`PATIENT B:` parser — and loads `Configuration.yml` into a new AIRA-owned, `is_template=true` `configuration` row, reusing `mismo-match`'s `Configuration(InputStream)` + `.setup()` (the same canonicalization `CentralServlet` already performs on Island-submitted scripts).
3. Remove the code that becomes dead once the files are gone rather than repointing it at the new file location:
   - `TestMatchingServlet`'s and `ReviewServlet`'s `MIIS-*` flat-file dropdown — redundant with match-set browsing from the database, which is already the primary path.
   - `GenerateWeightsServlet` entirely — already a `modernization-notes.md` deletion candidate (superseded by the CLI `Island` process; carries the static-`Scorer.weights`/shared-`ServletContext`-scoped-`World` hazards flagged in the v1 review) and its only remaining reason to exist was its hardcoded `MIIS-E2.txt` load.
   - `island.yml`'s `testCaseFileName` is different — it's a genuinely-still-needed local input for the CLI `Island` optimizer's own training corpus, not redundant with anything. Just update the path to point at `legacy-test-data/`. Whether `Island` should eventually read its training set from the database instead of a local file is a separate, bigger question, left alone here.
4. Delete `Configuration.yml` and the flat-file corpus from `src/main/resources` once the bootstrap has run and been verified against the real database (see §2's note in the "Recommended v2 Migration Principle," §9 — this development database is becoming production, so this is a real, one-time data-loading event, not a repeatable seed script).

---

# 3. Proposed v2 Schema

## 3.1 `organization` — new

```text
organization
------------
organization_id      int/bigint PK AUTO_INCREMENT
name                 varchar(250) NOT NULL
domain               varchar(250) NULL
is_template_org      boolean NOT NULL DEFAULT false
created_at           datetime NOT NULL
updated_at           datetime NOT NULL
```

### Notes

- `name` is initially populated from the InteropHub organization value.
- `domain` is a convenience for initial automatic assignment only.
- `domain` should be unique when populated.
- Public email domains should normally not be stored as reusable organization domains.
- The schema intentionally supports only one domain per organization for now.
- If InteropHub later provides an authoritative organization ID, an `interop_hub_organization_id` column can be added without changing the basic ownership model.
- `is_template_org` (§2.10) marks an organization whose `match_set`/`configuration` rows are eligible to be published as templates. Set `true` on the AIRA organization only; ordinary organizations leave this `false`.

Recommended indexes:

```text
UNIQUE(domain)
INDEX(name)
```

---

## 3.2 `user` — replace legacy authentication fields

### Current

```text
user_id
name
email
password
```

### Proposed

```text
user
----
user_id              int/bigint PK AUTO_INCREMENT
hub_user_id           varchar(64) NOT NULL
organization_id       int/bigint NOT NULL FK -> organization.organization_id
email                 varchar(250) NOT NULL
display_name          varchar(250) NULL
first_name            varchar(120) NULL
last_name             varchar(120) NULL
title                 varchar(250) NULL
created_at            datetime NOT NULL
updated_at            datetime NOT NULL
last_login_at         datetime NULL
```

Constraints/indexes:

```text
UNIQUE(hub_user_id)
INDEX(organization_id)
INDEX(email)
FOREIGN KEY (organization_id)
    REFERENCES organization(organization_id)
```

### Removed

```text
password
```

The old `name` column should be replaced by the more explicit name fields. `display_name` can retain the InteropHub `name` value.

Email should not be unique because it is not the identity key.

---

## 3.3 `match_set` — add organization ownership and attribution

### Current

```text
match_set_id
label
update_date
```

### Proposed

```text
match_set
---------
match_set_id           int/bigint PK AUTO_INCREMENT
organization_id        int/bigint NOT NULL FK -> organization.organization_id
label                  varchar(250) NOT NULL
is_template            boolean NOT NULL DEFAULT false
created_by_user_id     int/bigint NULL FK -> user.user_id
updated_by_user_id     int/bigint NULL FK -> user.user_id
created_at             datetime NOT NULL
updated_at             datetime NOT NULL
```

The organization belongs on `match_set` because it is the container for its test cases.

`is_template` (§2.10) may only be `true` when `organization_id` refers to an `is_template_org` organization — enforced by the application, not a database constraint. A template `match_set` is readable and copyable by every organization but remains editable only by its owner.

`match_item` does not need its own `organization_id`; organization ownership can be derived through:

```text
match_item
    -> match_set
        -> organization
```

Recommended indexes:

```text
INDEX(organization_id)
INDEX(organization_id, label)
```

A unique label per organization can be considered later if the UI requires it. It is not necessary for the initial migration.

---

## 3.4 `match_item` — preserve data model, regularize attribution

### Current

```text
match_item_id
match_set_id
label
description
patient_data_a
patient_data_b
expect_status
user_id
update_date
data_source
```

### Proposed

```text
match_item
----------
match_item_id          int/bigint PK AUTO_INCREMENT
match_set_id           int/bigint NOT NULL FK -> match_set.match_set_id
label                  varchar(250) NOT NULL
description            varchar(1000) NULL
patient_data_a         text NOT NULL
patient_data_b         text NOT NULL
expect_status          varchar(20) NOT NULL
data_source            varchar(120) NOT NULL
created_by_user_id     int/bigint NULL FK -> user.user_id
updated_by_user_id     int/bigint NULL FK -> user.user_id
created_at             datetime NOT NULL
updated_at             datetime NOT NULL
```

### Decisions

- Preserve `patient_data_a` and `patient_data_b` as they currently work.
- Do not revive the unused `patient` table as part of this migration.
- Preserve `data_source`; it represents provenance and is different from the application user who edited the record.
- Replace the ambiguous existing `user_id` with explicit created/updated attribution.

Recommended indexes:

```text
INDEX(match_set_id)
INDEX(match_set_id, label)
```

Add a real foreign key:

```text
FOREIGN KEY (match_set_id)
    REFERENCES match_set(match_set_id)
```

---

## 3.5 `configuration` — add organization ownership and attribution

### Current

```text
configuration_id
world_name
island_name
generation
generation_score
generated_date
hash_for_signature
configuration_script
```

### Proposed initial v2 structure

```text
configuration
-------------
configuration_id       int/bigint PK AUTO_INCREMENT
organization_id        int/bigint NOT NULL FK -> organization.organization_id
world_name             varchar(250) NOT NULL
island_name            varchar(250) NOT NULL
generation              int NOT NULL
generation_score        double NOT NULL
generated_date          datetime NOT NULL
hash_for_signature      varchar(250) NOT NULL
configuration_script    text NOT NULL
is_template             boolean NOT NULL DEFAULT false
created_by_user_id      int/bigint NULL FK -> user.user_id
island_credential_id    int/bigint NULL FK -> island_credential.island_credential_id
created_at              datetime NOT NULL
```

`is_template` (§2.10) follows the same rule as `match_set.is_template`: only settable `true` for a row owned by an `is_template_org` organization, enforced by the application. Readable/copyable by every organization; editable only by its owner.

`created_by_user_id` is used when a configuration is created or imported by a human.

`island_credential_id` identifies configurations submitted by an Island.

Both may be nullable because migrated historical data may not have reliable attribution.

### History behavior

v2 should stop overwriting an Island's prior configuration row. New Island submissions should be insert-only so generation history is retained.

This does not require a more elaborate configuration lifecycle in the first migration.

A future enhancement may add concepts such as candidate/approved/active configuration or a reference to the exact training set used for evaluation.

Recommended indexes:

```text
INDEX(organization_id)
INDEX(organization_id, world_name)
INDEX(organization_id, world_name, island_name)
INDEX(island_credential_id)
```

---

## 3.6 `island_credential` — new

```text
island_credential
-----------------
island_credential_id   int/bigint PK AUTO_INCREMENT
organization_id        int/bigint NOT NULL FK -> organization.organization_id
name                   varchar(250) NOT NULL
credential_hash        varchar(250) NOT NULL
created_by_user_id     int/bigint NOT NULL FK -> user.user_id
created_at             datetime NOT NULL
last_used_at           datetime NULL
revoked_at             datetime NULL
```

Recommended indexes:

```text
INDEX(organization_id)
UNIQUE(credential_hash)
```

### Behavior

A user creates an Island credential from within Mismo-Trainer.

The credential automatically inherits the user's `organization_id`.

When an Island authenticates with that credential:

1. Mismo-Trainer resolves the credential;
2. determines the organization;
3. accepts or returns optimization data only for that organization;
4. attributes uploaded configurations to that Island credential.

Revoking a credential prevents future use without deleting historical configuration records associated with it.

---

## 3.7 `patient` — retire from active v2 model

The existing `patient` table is unused in production and does not represent the complete patient model actually used by Mismo.

Recommended migration:

- do not migrate application logic to this table;
- stop mapping it in the v2 application;
- retain it temporarily during database migration if desired for rollback;
- drop it after the v2 migration is validated.

The existing `match_item.patient_data_a` and `patient_data_b` fields remain the authoritative persisted patient-pair representation for this phase.

---

# 4. Tenant Enforcement Rules

Organization security must be enforced by application queries, not only by page navigation.

The authenticated session contains:

```text
user_id
hub_user_id
organization_id
profile/display fields
```

Every request for organization-owned data must include the current `organization_id`.

Example:

```sql
SELECT *
FROM match_set
WHERE match_set_id = :matchSetId
  AND organization_id = :organizationId;
```

For child records such as `match_item`, ownership should be validated through the parent:

```sql
SELECT mi.*
FROM match_item mi
JOIN match_set ms
  ON ms.match_set_id = mi.match_set_id
WHERE mi.match_item_id = :matchItemId
  AND ms.organization_id = :organizationId;
```

The same rule applies to:

- lists;
- detail pages;
- edits;
- deletes;
- uploads;
- downloads;
- scoring;
- configuration selection;
- Island synchronization.

Client-supplied `organization_id` values must never determine access. The active organization always comes from the authenticated Mismo-Trainer user or authenticated Island credential.

### 4.1 Template rows widen read access only (§2.10)

`match_set` and `configuration` reads must match a row belonging to the session's own `organization_id`, **or** a row with `is_template = true` regardless of owner:

```sql
SELECT *
FROM match_set
WHERE match_set_id = :matchSetId
  AND (organization_id = :organizationId OR is_template = true);
```

This applies only to reads (including listing, browsing, and scoring against a template) and to the copy action. Every write path — create, edit, delete, and setting `is_template` itself — keeps the unmodified rule above: `organization_id = :organizationId`, with no exception for template rows. Copying a template creates a brand-new row owned by the copying organization; it never grants write access to the original.

---

# 5. Lightweight Audit Model

The first v2 release does not need a general-purpose audit-event system.

For normal records, basic attribution is sufficient:

```text
created_by_user_id
created_at
updated_by_user_id
updated_at
```

This supports UI statements such as:

> Last updated by Jane Smith on August 17, 2026.

Historical versions of every edit are out of scope for this migration.

Configuration submissions are slightly different because they may originate from either a user or an Island; their source should therefore be captured using `created_by_user_id` and/or `island_credential_id`.

---

# 6. Migration Strategy

All DDL/DML for the phases below is applied manually, staged in `src/db/unapplied_updates.sql` and cycled out on release — see `v2-roadmap.md` §8 for the full process.

## Phase 1 — Add the new identity and organization structures

1. Define trainer-owned entity classes and `.hbm.xml` mappings for `User`, `Organization`, `IslandCredential`, `MatchSet`, `MatchItem`, and `Configuration` (§2.7), replacing the mappings that currently point at the `mismo-match`-jar classes of the same simple names.
2. Create `organization`.
3. Create the new InteropHub-oriented columns on `user`, using `varchar` for `hub_user_id` (§2.1).
4. Add `organization_id` to `user`.
5. Create `island_credential`.
6. Add organization and attribution columns to `match_set`.
7. Add explicit attribution columns to `match_item`.
8. Add organization/source columns to `configuration`.

Initially allow new foreign-key columns to be nullable so existing data can be migrated safely.

## Phase 2 — Create the initial organization

The existing production database contains one legacy user and existing shared Mismo data.

Create one initial organization representing the current owner of that data.

Assign to that organization:

- the existing legacy user;
- all existing `match_set` rows;
- all existing `configuration` rows.

Existing `match_item` rows inherit organization ownership through their match set.

## Phase 3 — Migrate attribution

For `match_item`:

- copy existing `user_id` into `updated_by_user_id`;
- also use it for `created_by_user_id` where no better creation history exists;
- copy `update_date` into `updated_at`;
- use `update_date` for `created_at` when no true creation timestamp exists.

For existing `match_set` rows:

- use the legacy user as `created_by_user_id` / `updated_by_user_id` if appropriate;
- use existing `update_date` as the best available historical timestamp.

For existing configuration rows:

- assign the initial organization;
- leave human/Island attribution null if the source cannot be established reliably;
- preserve `generated_date`.

The migration should not invent more precise history than the v1 database actually contains.

## Phase 4 — Switch authentication

Replace the legacy username/password login flow with InteropHub SSO.

After successful InteropHub exchange:

1. require `hub_user_id`;
2. find `user` by `hub_user_id`;
3. create the user if needed;
4. perform initial organization assignment if needed;
5. refresh InteropHub profile fields;
6. update `last_login_at`;
7. establish the local application session.

Once the SSO path is functioning, remove all application use of the legacy password field.

At the same time, remove the anonymous/guest branches in `TestMatchingServlet` and `TestScriptExploreServlet` (§2.8) — every human-facing page requires a session after this phase. The only endpoint left outside the login filter is the Island machine API, which is authenticated separately in Phase 6.

## Phase 5 — Enforce organization scoping

Before allowing general InteropHub access, update every workflow that reads or modifies persisted data so it is organization scoped.

Particular attention is required for:

- match-set browsing;
- match-item review/edit;
- test-set upload;
- testing/scoring;
- configuration browsing and selection;
- configuration downloads;
- Island synchronization.

This is a release requirement. Organization filtering must not be deferred until after outside users can log in.

The bundled `MIIS-*`/`AIRA-*` flat-file corpora are the one exception: they stay global and unscoped per §2.9, so the pages that serve them need a login check but no organization filter on that particular data source.

## Phase 6 — Replace Island anonymous access

Add authenticated Island synchronization using `island_credential`.

The existing unauthenticated Island API should be disabled once credential-based synchronization is available.

Island operations should always be scoped to the organization associated with the credential.

## Phase 7 — Add database constraints

After all rows have been backfilled successfully:

1. make required `organization_id` columns `NOT NULL`;
2. add foreign-key constraints;
3. add uniqueness constraints and indexes;
4. remove the legacy `user.password` column;
5. remove or rename legacy `user.name`;
6. remove obsolete `match_item.user_id` and `update_date` after the new attribution columns are validated;
7. remove obsolete `match_set.update_date` after migration;
8. stop overwriting historical `configuration` rows.

## Phase 8 — Template organization and starter data (§2.10)

1. Add `organization.is_template_org`, `match_set.is_template`, `configuration.is_template` (all `boolean NOT NULL DEFAULT false`).
2. Set `is_template_org = true` on the AIRA organization.
3. Move the bundled flat-file corpus and `Configuration.yml` out of `src/main/resources` into `legacy-test-data/`, per §2.10's migration steps.
4. Run the one-time bootstrap that loads `AIRA-D.txt` and `Configuration.yml` into an AIRA-owned, `is_template=true` `match_set` and `configuration`; verify against the real database before deleting the source files.
5. Remove the now-dead `MIIS-*` dropdown code in `TestMatchingServlet`/`ReviewServlet`, delete `GenerateWeightsServlet`, and update `island.yml`'s `testCaseFileName` to the new `legacy-test-data/` path.
6. Extend `OrgScope`'s read/list methods per §4.1; add the copy action for `match_set` and `configuration`; enforce the "`is_template` only settable by an `is_template_org` owner" rule wherever it's set.

---

# 7. Foreign-Key Relationships

The resulting core relationship model is:

```text
organization
    |
    +---- user
    |
    +---- match_set
    |        |
    |        +---- match_item
    |
    +---- configuration
    |
    +---- island_credential
             |
             +---- configuration
```

User attribution adds additional relationships:

```text
user
  +---- match_set.created_by_user_id
  +---- match_set.updated_by_user_id
  +---- match_item.created_by_user_id
  +---- match_item.updated_by_user_id
  +---- configuration.created_by_user_id
  +---- island_credential.created_by_user_id
```

---

# 8. Deferred Decisions

The following are intentionally not required for the initial access/schema migration:

- multiple email domains per organization;
- authoritative organization IDs from InteropHub;
- organization administrators or detailed user roles;
- full edit-history/audit tables;
- training-set versioning or locking;
- configuration approval/promotion/active status;
- normalized patient tables;
- JSON replacement for the existing serialized patient strings;
- richer Island/run/job modeling;
- full schema migration framework beyond the migration scripts needed for v2;
- ~~formal ownership/organization-scoping of the bundled `MIIS-*`/`AIRA-*` flat-file test corpora~~ — resolved by §2.10/Phase 8: the one starter test set actually needed moves into the database as an AIRA-owned template; the rest of the files are archived out of the WAR rather than modeled.
- a general-purpose template/marketplace model (multiple template-eligible organizations, template categories, versioning of published templates) — §2.10 intentionally supports exactly one template-eligible organization (AIRA) publishing individual rows; broaden this only if a real second case appears.

These may be added later without changing the core `organization -> user/resources` ownership model.

---

# 9. Recommended v2 Migration Principle

The migration should establish one rule consistently throughout the application:

> **InteropHub determines who the human user is. Mismo-Trainer determines which organization that user belongs to. All persisted Trainer work belongs to an organization. Human activity is attributed to the local Mismo user, while Island activity is authenticated and attributed separately. Every human-facing page requires a login; there is no anonymous path. One organization's work may be published as a template — visible and copyable by every other organization, but never editable by them.**

This provides the access boundary needed to make Mismo-Trainer available to additional InteropHub users without exposing one organization's training work to another, while keeping the initial implementation small enough to support the current modernization effort.