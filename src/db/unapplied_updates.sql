-- unapplied_updates.sql
--
-- Pending, not-yet-released database changes. Applied manually against a
-- local copy of the database before development/testing (see
-- docs/v2-roadmap.md §8), then folded into a dated/versioned snapshot
-- (e.g. upgrade-2.0.sql) and reset to empty on release.
--
-- This is NOT applied automatically as part of the build, and has not been
-- run against any live database.

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
-- directly (queried, not assumed) against the local matching_validation
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
