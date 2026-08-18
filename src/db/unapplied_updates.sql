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
