-- upgrade-2.0.sql
--
-- Frozen, permanent record of every database change made for the v2.0
-- release (Phases 2-11 of docs/v2-roadmap.md) -- copied from
-- unapplied_updates.sql at release time, per docs/production-deployment-plan.md
-- §5. Never edited after creation; the current content below is exactly
-- what was applied to production for this release.
--
-- Already applied to the database this repo's own local dev environment
-- runs against (that database *is* the v2.0 seed -- see
-- docs/production-deployment-checklist.md). A fresh install starting from
-- upstream of this release would apply this file once, in full.

-- =====================================================================
-- Phase 2 (docs/v2-roadmap.md §3; docs/database-schema-migration-plan.md
-- §3, Phase 1 of §6) -- schema foundation: new organization/island_credential
-- tables, plus additive, nullable-for-now identity/attribution/organization
-- columns on the existing user/match_set/match_item/configuration tables.
--
-- All new foreign-key columns are left nullable in this phase so existing
-- data is not broken; NOT NULL, real FOREIGN KEY constraints, and
-- uniqueness enforcement are deferred to the Phase 7 constraint-tightening
-- pass (database-schema-migration-plan.md §6 Phase 7).
-- =====================================================================

-- ---------------------------------------------------------------------
-- organization (new) -- database-schema-migration-plan.md §3.1
-- ---------------------------------------------------------------------
CREATE TABLE organization (
    organization_id int NOT NULL AUTO_INCREMENT,
    name varchar(250) NOT NULL,
    domain varchar(250) DEFAULT NULL,
    created_at datetime NOT NULL,
    updated_at datetime NOT NULL,
    PRIMARY KEY (organization_id),
    UNIQUE KEY uq_organization_domain (domain),
    KEY idx_organization_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- island_credential (new) -- database-schema-migration-plan.md §3.6
-- organization_id / created_by_user_id are nullable for now, same as
-- every other new FK column added in this phase.
-- ---------------------------------------------------------------------
CREATE TABLE island_credential (
    island_credential_id int NOT NULL AUTO_INCREMENT,
    organization_id int DEFAULT NULL,
    name varchar(250) NOT NULL,
    credential_hash varchar(250) NOT NULL,
    created_by_user_id int DEFAULT NULL,
    created_at datetime NOT NULL,
    last_used_at datetime DEFAULT NULL,
    revoked_at datetime DEFAULT NULL,
    PRIMARY KEY (island_credential_id),
    UNIQUE KEY uq_island_credential_hash (credential_hash),
    KEY idx_island_credential_organization_id (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- user -- database-schema-migration-plan.md §3.2
-- hub_user_id is treated as an opaque string end to end (the InteropHub
-- client library returns it as String, never numeric) -- see §2.1.
-- Legacy `name`/`password` columns are untouched here; they are dropped
-- in Phase 7 once the InteropHub login path is live.
-- ---------------------------------------------------------------------
ALTER TABLE user
    ADD COLUMN hub_user_id varchar(64) DEFAULT NULL,
    ADD COLUMN organization_id int DEFAULT NULL,
    ADD COLUMN display_name varchar(250) DEFAULT NULL,
    ADD COLUMN first_name varchar(120) DEFAULT NULL,
    ADD COLUMN last_name varchar(120) DEFAULT NULL,
    ADD COLUMN title varchar(250) DEFAULT NULL,
    ADD COLUMN created_at datetime DEFAULT NULL,
    ADD COLUMN updated_at datetime DEFAULT NULL,
    ADD COLUMN last_login_at datetime DEFAULT NULL;

ALTER TABLE user
    ADD UNIQUE KEY uq_user_hub_user_id (hub_user_id),
    ADD KEY idx_user_organization_id (organization_id);

-- ---------------------------------------------------------------------
-- match_set -- database-schema-migration-plan.md §3.3
-- ---------------------------------------------------------------------
ALTER TABLE match_set
    ADD COLUMN organization_id int DEFAULT NULL,
    ADD COLUMN created_by_user_id int DEFAULT NULL,
    ADD COLUMN updated_by_user_id int DEFAULT NULL,
    ADD COLUMN created_at datetime DEFAULT NULL,
    ADD COLUMN updated_at datetime DEFAULT NULL;

ALTER TABLE match_set
    ADD KEY idx_match_set_organization_id (organization_id),
    ADD KEY idx_match_set_organization_label (organization_id, label);

-- ---------------------------------------------------------------------
-- match_item -- database-schema-migration-plan.md §3.4
-- The existing `user_id`/`update_date` columns are left as-is; they are
-- replaced by created_by_user_id/updated_by_user_id in application logic
-- during Phase 3 (backfill) and removed in Phase 7.
-- ---------------------------------------------------------------------
ALTER TABLE match_item
    ADD COLUMN created_by_user_id int DEFAULT NULL,
    ADD COLUMN updated_by_user_id int DEFAULT NULL,
    ADD COLUMN created_at datetime DEFAULT NULL,
    ADD COLUMN updated_at datetime DEFAULT NULL;

ALTER TABLE match_item
    ADD KEY idx_match_item_match_set_id (match_set_id),
    ADD KEY idx_match_item_match_set_label (match_set_id, label);

-- ---------------------------------------------------------------------
-- configuration -- database-schema-migration-plan.md §3.5
-- ---------------------------------------------------------------------
ALTER TABLE configuration
    ADD COLUMN organization_id int DEFAULT NULL,
    ADD COLUMN created_by_user_id int DEFAULT NULL,
    ADD COLUMN island_credential_id int DEFAULT NULL,
    ADD COLUMN created_at datetime DEFAULT NULL;

ALTER TABLE configuration
    ADD KEY idx_configuration_organization_id (organization_id),
    ADD KEY idx_configuration_organization_world (organization_id, world_name),
    ADD KEY idx_configuration_organization_world_island (organization_id, world_name, island_name),
    ADD KEY idx_configuration_island_credential_id (island_credential_id);

-- =====================================================================
-- Phase 3/4 (docs/v2-roadmap.md §4; database-schema-migration-plan.md §6
-- Phase 2-4) -- InteropHub SSO switch-over.
--
-- Two independent pieces:
--   (a) app_setting -- a minimal DB-driven runtime settings table holding
--       the InteropHub base URL, following the "Clear" integration
--       precedent in InteropHub-Client's docs/integration-clear.md (chosen
--       over a build-time property since this app already has Hibernate/
--       session-factory plumbing a settings table fits naturally into).
--   (b) backfill DML -- create one organization representing the pre-v2
--       data owner and assign the existing legacy user/match_set/
--       configuration rows to it, and backfill match_item's user_id/
--       update_date into the new attribution columns. All statements are
--       idempotent (safe to re-run; no-ops once already applied).
--
-- This does NOT touch user.password or any application code path -- that
-- removal is Phase 7 (schema plan §6 Phase 7); the application simply
-- stops *using* the column once InteropHub login is live.
-- =====================================================================

-- ---------------------------------------------------------------------
-- app_setting (new) -- see InteropHub-Client docs/integration-clear.md's
-- "system_settings" table. Currently holds only the Hub's base URL.
-- ---------------------------------------------------------------------
CREATE TABLE app_setting (
    setting_key varchar(100) NOT NULL,
    setting_value varchar(500) DEFAULT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Local dev default -- update via the row itself (no redeploy needed) for
-- other environments.
INSERT INTO app_setting (setting_key, setting_value)
VALUES ('hub.external.url', 'http://localhost:8080/hub');

-- ---------------------------------------------------------------------
-- Backfill -- database-schema-migration-plan.md §6 Phase 2/Phase 3.
-- Creates one organization representing the pre-v2 data owner and assigns
-- to it: the existing legacy user (any user row that predates InteropHub
-- login, i.e. hub_user_id is still NULL), all existing match_set rows,
-- and all existing configuration rows. match_item's legacy user_id/
-- update_date are copied into the new attribution columns.
--
-- Note: this intentionally does NOT try to match/merge a legacy
-- password-login user with a later InteropHub identity by email --
-- hub_user_id is the sole identity key (§2.1); a person who both has a
-- pre-v2 legacy row and later logs in via InteropHub gets a second,
-- separate user row, exactly as the migration plan specifies.
-- ---------------------------------------------------------------------
INSERT INTO organization (name, domain, created_at, updated_at)
SELECT 'Legacy Mismo-Trainer Data', NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM organization WHERE name = 'Legacy Mismo-Trainer Data');

UPDATE user
SET organization_id = (SELECT organization_id FROM organization WHERE name = 'Legacy Mismo-Trainer Data')
WHERE hub_user_id IS NULL AND organization_id IS NULL;

UPDATE match_set
SET organization_id = (SELECT organization_id FROM organization WHERE name = 'Legacy Mismo-Trainer Data'),
    created_by_user_id = COALESCE(created_by_user_id,
        (SELECT user_id FROM user WHERE hub_user_id IS NULL ORDER BY user_id LIMIT 1)),
    updated_by_user_id = COALESCE(updated_by_user_id,
        (SELECT user_id FROM user WHERE hub_user_id IS NULL ORDER BY user_id LIMIT 1)),
    created_at = COALESCE(created_at, update_date),
    updated_at = COALESCE(updated_at, update_date)
WHERE organization_id IS NULL;

UPDATE configuration
SET organization_id = (SELECT organization_id FROM organization WHERE name = 'Legacy Mismo-Trainer Data')
WHERE organization_id IS NULL;

UPDATE match_item
SET created_by_user_id = COALESCE(created_by_user_id, user_id),
    updated_by_user_id = COALESCE(updated_by_user_id, user_id),
    created_at = COALESCE(created_at, update_date),
    updated_at = COALESCE(updated_at, update_date)
WHERE created_by_user_id IS NULL OR updated_by_user_id IS NULL
   OR created_at IS NULL OR updated_at IS NULL;

-- =====================================================================
-- Phase 7 (docs/v2-roadmap.md §7; docs/database-schema-migration-plan.md
-- §6 Phase 7) -- constraint tightening and legacy column/table cleanup.
--
-- DESTRUCTIVE / IRREVERSIBLE -- kept as its own clearly-delineated block
-- per v2-roadmap.md §8, applied only after confirming every row already
-- has a populated organization_id and no orphaned foreign keys. Verified
-- directly (queried, not assumed) against the local mismo
-- database on 2026-08-18 before writing this block:
--   * user (2 rows) / match_set (9 rows) / configuration (15 rows) /
--     island_credential (1 row): zero NULL organization_id rows.
--   * Zero orphans: match_item.match_set_id -> match_set;
--     match_set/configuration/island_credential.organization_id ->
--     organization; match_set/match_item.created_by_user_id/
--     updated_by_user_id -> user; configuration.created_by_user_id ->
--     user; configuration.island_credential_id -> island_credential;
--     island_credential.created_by_user_id -> user.
--   * Zero duplicate values in user.hub_user_id, organization.domain,
--     island_credential.credential_hash.
-- Re-run the equivalent checks against production data before applying
-- this block there -- do not assume the same result holds.
-- =====================================================================

-- ---------------------------------------------------------------------
-- organization_id -> NOT NULL (database-schema-migration-plan.md §6
-- Phase 7 step 1). island_credential.organization_id is included here
-- too even though the roadmap phase prose only names user/match_set/
-- configuration -- schema plan §3.6 defines it NOT NULL as well, and the
-- verification above confirms local data already satisfies it.
-- ---------------------------------------------------------------------
ALTER TABLE user MODIFY COLUMN organization_id int NOT NULL;
ALTER TABLE match_set MODIFY COLUMN organization_id int NOT NULL;
ALTER TABLE configuration MODIFY COLUMN organization_id int NOT NULL;
ALTER TABLE island_credential MODIFY COLUMN organization_id int NOT NULL;

-- ---------------------------------------------------------------------
-- Real foreign-key constraints (database-schema-migration-plan.md §6
-- Phase 7 step 2, and the full relationship diagram in §7 of that doc).
-- ---------------------------------------------------------------------
ALTER TABLE user
    ADD CONSTRAINT fk_user_organization
        FOREIGN KEY (organization_id) REFERENCES organization (organization_id);

ALTER TABLE match_set
    ADD CONSTRAINT fk_match_set_organization
        FOREIGN KEY (organization_id) REFERENCES organization (organization_id),
    ADD CONSTRAINT fk_match_set_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES user (user_id),
    ADD CONSTRAINT fk_match_set_updated_by_user
        FOREIGN KEY (updated_by_user_id) REFERENCES user (user_id);

ALTER TABLE match_item
    ADD CONSTRAINT fk_match_item_match_set
        FOREIGN KEY (match_set_id) REFERENCES match_set (match_set_id),
    ADD CONSTRAINT fk_match_item_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES user (user_id),
    ADD CONSTRAINT fk_match_item_updated_by_user
        FOREIGN KEY (updated_by_user_id) REFERENCES user (user_id);

ALTER TABLE configuration
    ADD CONSTRAINT fk_configuration_organization
        FOREIGN KEY (organization_id) REFERENCES organization (organization_id),
    ADD CONSTRAINT fk_configuration_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES user (user_id),
    ADD CONSTRAINT fk_configuration_island_credential
        FOREIGN KEY (island_credential_id) REFERENCES island_credential (island_credential_id);

ALTER TABLE island_credential
    ADD CONSTRAINT fk_island_credential_organization
        FOREIGN KEY (organization_id) REFERENCES organization (organization_id),
    ADD CONSTRAINT fk_island_credential_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES user (user_id);

-- ---------------------------------------------------------------------
-- Uniqueness constraints (database-schema-migration-plan.md §3.1/§3.2/
-- §3.6) -- all three were already added back in the Phase 2 block above:
-- user.hub_user_id (uq_user_hub_user_id), organization.domain
-- (uq_organization_domain), island_credential.credential_hash
-- (uq_island_credential_hash). Nothing further to add here; MySQL UNIQUE
-- indexes already permit multiple NULLs, which is what makes "domain
-- unique where populated" and a still-nullable legacy hub_user_id work.
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- Drop legacy columns (database-schema-migration-plan.md §6 Phase 7
-- steps 4-7). Application code no longer reads or writes these -- see
-- User.hbm.xml/MatchSet.hbm.xml/MatchItem.hbm.xml and the corresponding
-- trainer model classes, updated in this same phase.
-- ---------------------------------------------------------------------
ALTER TABLE user
    DROP COLUMN password,
    DROP COLUMN name;

ALTER TABLE match_item
    DROP COLUMN user_id,
    DROP COLUMN update_date;

ALTER TABLE match_set
    DROP COLUMN update_date;

-- ---------------------------------------------------------------------
-- Retire the dead patient table (database-schema-migration-plan.md
-- §3.7). Unused in production; match_item.patient_data_a/patient_data_b
-- remain the authoritative persisted patient-pair representation.
-- ---------------------------------------------------------------------
DROP TABLE patient;

-- =====================================================================
-- Phase 7 (docs/v2-roadmap.md §8; docs/database-schema-migration-plan.md
-- §2.10, §6 Phase 8) -- template organization and starter data.
--
-- Purely additive (new boolean columns, all NOT NULL DEFAULT false) plus
-- one idempotent UPDATE -- no drops, no NOT NULL tightening of existing
-- columns -- so this follows the same accumulate-and-release-early
-- pattern as Phase 2 above, not the destructive block immediately above
-- it (v2-roadmap.md §13).
-- =====================================================================

ALTER TABLE organization
    ADD COLUMN is_template_org boolean NOT NULL DEFAULT false;

ALTER TABLE match_set
    ADD COLUMN is_template boolean NOT NULL DEFAULT false;

ALTER TABLE configuration
    ADD COLUMN is_template boolean NOT NULL DEFAULT false;

-- Publish AIRA as the one template-eligible organization (§2.10). AIRA is
-- not a synthetic "system" org -- it's the same real organization that
-- LoginServlet's email-domain auto-provisioning (§2.4) already lands every
-- *@immregistries.org login in. Matched by domain, not by name (InteropHub
-- supplies the display name, which is not a stable match key). Idempotent:
-- a no-op once already set, and also a no-op (nothing to update) on a
-- database where nobody from that domain has logged in yet -- re-run this
-- statement after that first login creates the row.
UPDATE organization
SET is_template_org = true
WHERE domain = 'immregistries.org';

-- =====================================================================
-- Phase 9 (docs/v2-roadmap.md §10; docs/database-changes-for-functional-
-- model.md §2-4, §9) -- Test Set lifecycle/versioning and Test Case
-- review workflow.
--
-- Purely additive: new nullable/defaulted columns on match_set/match_item
-- plus one new table (match_item_review). No drops, no NOT NULL
-- tightening of any *existing* column.
--
-- root_match_set_id is declared NOT NULL (per the design: always
-- populated, a root set points at its own id) but a brand-new row's own
-- id isn't known until after its INSERT, so the application writes 0 on
-- insert and immediately self-corrects it to the just-generated id in the
-- same transaction (OrgScope.createMatchSet/copyMatchSet) -- 0 is never a
-- real match_set_id (AUTO_INCREMENT starts at 1), so it only ever exists
-- as a same-transaction transient value, never a committed one. Existing
-- rows are backfilled the same way: every pre-Phase-9 match_set is the
-- root of its own one-row family.
--
-- No FOREIGN KEY constraints are added for the new self-referential
-- columns (root_match_set_id, copied_from_match_set_id,
-- copied_from_match_item_id) or match_item_review's match_item_id/
-- reviewer_user_id -- consistent with this file's existing pattern
-- (e.g. island_credential's FKs were likewise deferred to the Phase 7
-- constraint-tightening block above, even though it was a brand-new,
-- empty table at the time). A future constraint-tightening pass can add
-- them once there's confidence no orphaned values exist.
-- =====================================================================

-- ---------------------------------------------------------------------
-- match_set -- database-changes-for-functional-model.md §2.
-- ---------------------------------------------------------------------
ALTER TABLE match_set
    ADD COLUMN lifecycle_status varchar(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN version varchar(50) DEFAULT NULL,
    ADD COLUMN copied_from_match_set_id int DEFAULT NULL,
    ADD COLUMN root_match_set_id int NOT NULL DEFAULT 0,
    ADD COLUMN archived_at datetime DEFAULT NULL;

-- Every match_set that exists before this migration is the root of its
-- own family (none of them are copies yet -- copying is introduced by
-- this same phase).
UPDATE match_set
SET root_match_set_id = match_set_id
WHERE root_match_set_id = 0;

ALTER TABLE match_set
    ADD KEY idx_match_set_root_match_set_id (root_match_set_id),
    ADD KEY idx_match_set_copied_from_match_set_id (copied_from_match_set_id);

-- ---------------------------------------------------------------------
-- match_item -- database-changes-for-functional-model.md §3 (folding in
-- the original_expect_status/is_reviewed/needs_review trio that the
-- older, never-built schema-plan Phase 9 had already documented).
-- ---------------------------------------------------------------------
ALTER TABLE match_item
    ADD COLUMN original_expect_status varchar(20) DEFAULT NULL,
    ADD COLUMN is_reviewed boolean NOT NULL DEFAULT false,
    ADD COLUMN needs_review boolean NOT NULL DEFAULT false,
    ADD COLUMN provenance_type varchar(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN copied_from_match_item_id int DEFAULT NULL,
    ADD COLUMN source_signature varchar(500) DEFAULT NULL,
    ADD COLUMN review_notes text DEFAULT NULL;

-- Snapshot every pre-existing row's current expectation as its "original"
-- one, since no snapshot was ever taken for rows created before this
-- column existed.
UPDATE match_item
SET original_expect_status = expect_status
WHERE original_expect_status IS NULL;

ALTER TABLE match_item
    ADD KEY idx_match_item_copied_from_match_item_id (copied_from_match_item_id);

-- ---------------------------------------------------------------------
-- match_item_review (new) -- database-changes-for-functional-model.md
-- §4. Append-only: rows are only ever inserted, never updated or
-- deleted. Inserting a row is what updates match_item.expect_status and
-- sets match_item.is_reviewed = true; the row itself is immutable
-- history.
-- ---------------------------------------------------------------------
CREATE TABLE match_item_review (
    match_item_review_id int NOT NULL AUTO_INCREMENT,
    match_item_id int NOT NULL,
    reviewer_user_id int NOT NULL,
    classification varchar(20) NOT NULL,
    notes text DEFAULT NULL,
    reviewed_at datetime NOT NULL,
    PRIMARY KEY (match_item_review_id),
    KEY idx_match_item_review_match_item_id (match_item_id, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- Phase 10 (docs/v2-roadmap.md §11; docs/database-changes-for-functional-
-- model.md §5) -- Evaluation, plus Configuration comparison.
--
-- Purely additive: two new tables, nothing else touched. Today
-- "evaluation" isn't persisted at all -- TestMatchingServlet computes
-- pass/fail live against whatever configuration is in session and throws
-- it away after rendering. This makes it durable and queryable.
--
-- The confusion matrix is deliberately NOT a column anywhere -- it's
-- derived from evaluation_result by aggregation (GROUP BY
-- expected_classification, calculated_classification), avoiding a second
-- place for the same data to go stale.
--
-- Configuration comparison (A vs. B against the same match_set) needs no
-- schema beyond these two tables -- it's a query joining two evaluations'
-- evaluation_result rows on match_item_id.
--
-- No FOREIGN KEY constraints are added, consistent with this file's
-- established pattern for brand-new tables (match_item_review above,
-- island_credential when it was first created) -- deferred to a future
-- constraint-tightening pass once there's confidence no orphaned values
-- exist. Indexes are added for every column real query patterns need.
-- =====================================================================

-- ---------------------------------------------------------------------
-- evaluation (new) -- one row per Test Set + Configuration run.
-- organization_id is the *running* user's organization -- not necessarily
-- the same as match_set_id's or configuration_id's owning organization,
-- since either side can be a readable template row owned by another
-- (template-eligible) organization.
-- ---------------------------------------------------------------------
CREATE TABLE evaluation (
    evaluation_id int NOT NULL AUTO_INCREMENT,
    organization_id int NOT NULL,
    match_set_id int NOT NULL,
    configuration_id int NOT NULL,
    run_by_user_id int DEFAULT NULL,
    run_at datetime NOT NULL,
    total_cases int NOT NULL,
    scorable_cases int NOT NULL,
    not_sure_cases int NOT NULL,
    agree_count int NOT NULL,
    disagree_count int NOT NULL,
    score double DEFAULT NULL,
    PRIMARY KEY (evaluation_id),
    KEY idx_evaluation_organization_id (organization_id),
    KEY idx_evaluation_match_set_id (match_set_id),
    KEY idx_evaluation_configuration_id (configuration_id),
    KEY idx_evaluation_match_set_configuration (match_set_id, configuration_id, run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- evaluation_result (new) -- one row per match_item scored by an
-- evaluation run, including "Not Sure" expectations (they're excluded
-- from evaluation's scorable_cases/agree_count/disagree_count/score, per
-- the functional model's "Not Sure is a valid analyst classification but
-- is not scorable" rule, but still get a row here so every case is
-- accounted for).
--
-- expected_classification is a snapshot of match_item.expect_status taken
-- at evaluation run time -- never re-read later, so it can't silently
-- drift if the case gets reclassified after the fact. Same "don't let
-- history quietly rewrite itself" principle as match_item.
-- original_expect_status.
--
-- Retention (whether re-running an evaluation also prunes the previous
-- run's rows) is explicitly not decided -- see
-- database-changes-for-functional-model.md §5's reasoning. No
-- cascade-delete is configured; evaluation_result is a self-contained
-- child of evaluation with nothing else pointing back at it.
-- ---------------------------------------------------------------------
CREATE TABLE evaluation_result (
    evaluation_result_id int NOT NULL AUTO_INCREMENT,
    evaluation_id int NOT NULL,
    match_item_id int NOT NULL,
    expected_classification varchar(20) NOT NULL,
    calculated_classification varchar(20) NOT NULL,
    signature varchar(500) DEFAULT NULL,
    agrees boolean NOT NULL,
    PRIMARY KEY (evaluation_result_id),
    KEY idx_evaluation_result_evaluation_id (evaluation_id),
    KEY idx_evaluation_result_evaluation_agrees (evaluation_id, agrees),
    KEY idx_evaluation_result_evaluation_signature (evaluation_id, signature),
    KEY idx_evaluation_result_match_item_id (match_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- Phase 11 (docs/v2-roadmap.md §12; docs/database-changes-for-functional-
-- model.md §7) -- Signature batch analysis.
--
-- Purely additive: two new tables, nothing else touched. Deliberately
-- stores only the raw uploaded (signature, count) pairs -- not decoded
-- classifications or which configuration was used to decode them -- so a
-- batch stays re-analyzable against any configuration later rather than
-- being tied to whichever one was picked at upload time (decode-on-demand,
-- same principle as evaluation_result NOT storing the confusion matrix
-- directly).
--
-- No FOREIGN KEY constraints are added, consistent with this file's
-- established pattern for brand-new tables (match_item_review, evaluation/
-- evaluation_result above) -- deferred to a future constraint-tightening
-- pass once there's confidence no orphaned values exist.
-- =====================================================================

-- ---------------------------------------------------------------------
-- signature_batch (new) -- one row per upload.
-- ---------------------------------------------------------------------
CREATE TABLE signature_batch (
    signature_batch_id int NOT NULL AUTO_INCREMENT,
    organization_id int NOT NULL,
    label varchar(250) DEFAULT NULL,
    uploaded_by_user_id int DEFAULT NULL,
    uploaded_at datetime NOT NULL,
    PRIMARY KEY (signature_batch_id),
    KEY idx_signature_batch_organization_id (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- signature_batch_entry (new) -- one row per (signature, count) pair in
-- an uploaded batch. No decoded classification column -- see above.
-- ---------------------------------------------------------------------
CREATE TABLE signature_batch_entry (
    signature_batch_entry_id int NOT NULL AUTO_INCREMENT,
    signature_batch_id int NOT NULL,
    signature varchar(500) NOT NULL,
    count int NOT NULL,
    PRIMARY KEY (signature_batch_entry_id),
    KEY idx_signature_batch_entry_signature_batch_id (signature_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
