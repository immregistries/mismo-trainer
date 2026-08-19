-- Run this after restoring a production database snapshot onto a local development machine,
-- before starting the app against it -- rewrites production-specific values back to their local
-- equivalents, so local dev doesn't end up pointed at production's InteropHub instance.
-- See docs/production-deployment-plan.md.
--
-- Separate concern from schema versioning: this file is not part of the unapplied_updates.sql /
-- upgrade-N.N_*.sql schema-migration chain, and is never applied to production.

UPDATE app_setting
SET setting_value = 'http://localhost:8080/hub'
WHERE setting_key = 'hub.external.url';
