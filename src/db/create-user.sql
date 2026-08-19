-- Creates the application's MySQL account. Generate a fresh, unique password for every
-- environment (local dev, production, etc.) -- never reuse one across environments, and never
-- commit a real password here. Set the same value as that environment's MISMO_DB_PASSWORD
-- (see docs/production-deployment-plan.md).
--
-- caching_sha2_password (MySQL 8's default auth plugin) is used here rather than the legacy
-- mysql_native_password this file used to specify.

CREATE USER 'mv_web'@'localhost' IDENTIFIED WITH caching_sha2_password BY '<REPLACE_WITH_A_FRESH_GENERATED_PASSWORD>';

GRANT ALL PRIVILEGES ON matching_validation.* TO 'mv_web'@'localhost' WITH GRANT OPTION;
