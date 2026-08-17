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
