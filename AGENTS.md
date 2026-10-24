# Mismo Trainer — Agent Guide

Mismo Trainer is the web application (Java 17, Tomcat 10.1, `jakarta.servlet`, Hibernate 3 `.hbm.xml` mappings, MySQL) for managing Test Sets, configurations, evaluations, signatures, and weight-set optimization for the Mismo patient-matching engine. The engine itself is the separate `mismo-match` library/repository (`../mismo-match`), consumed as a Maven dependency.

## Read before changing scoring, weights, or what the optimizer is steered toward

**[`docs/optimization-objectives-and-scoring-profiles.md`](docs/optimization-objectives-and-scoring-profiles.md)** is authoritative. In short:

- Mismo is currently used as a **national measurement instrument** (counting potential matches across IIS), not as a production matcher. Match and Possible Match are **deliberately treated as one signal**.
- The default scoring matrix, the narrow field set (first/middle/last name, DOB, phone, address, with limited roles for mother's maiden name and gender), the lean toward over-identifying, and twins landing in Possible Match are **intentional**. They will look like mistakes from an IIS-matching point of view. Do not "fix" them in a measurement configuration.
- Production calibration is a legitimate *separate* use. Steer toward it with a separately named Scoring Profile and seed configuration, never by changing the measurement defaults.

## Where things are documented

| Topic | Document |
|---|---|
| Concepts, terminology, and the original design | `docs/mismo-conceptual-overview.md` |
| Engine internals (networks, classification, signatures, fields used) | `docs/matching-engine.md` |
| Optimizer (genetic algorithm) internals; v1 Island snapshot | `docs/optimization-and-islands.md` |
| New centrally managed Island Runner: architecture | `docs/centrally-managed-local-island-runner.md` |
| Island Runner: implementation decisions and slices | `docs/island-runner-implementation-plan.md` |
| Database model and multi-tenant (organization) rules | `docs/data-model.md`, `docs/database-schema-migration-plan.md` |
| Roadmap and phase history | `docs/v2-roadmap.md` |
| Known bugs and their status | `docs/known-issues.md` |
| Deployment | `docs/production-deployment-plan.md`, `docs/production-deployment-checklist.md` |

Some v1-snapshot documents describe bugs that have since been fixed. Check `docs/known-issues.md` and the current code before acting on them.

## Conventions

- **Schema changes** go in `src/db/unapplied_updates.sql` (applied manually after the daily backup, then folded into a versioned upgrade script on release). Hibernate runs with `hbm2ddl.auto=validate`, so the SQL and the `.hbm.xml` mappings must agree.
- **Organization scoping:** every read and write of tenant data goes through `OrgScope` (owned-or-template read, owner-only write). Never load tenant rows by a client-supplied id alone.
- **Web pages** use `aira-web-components` for look and feel; authentication is InteropHub SSO via `AuthenticationFilter`.
- **Build:** `mvn package` produces `target/mismo.war`. `mismo-match` must be installed in the local Maven repository (`mvn install` in `../mismo-match`). The Island Runner plan restructures this into a multi-module build; see its Slice 0.
