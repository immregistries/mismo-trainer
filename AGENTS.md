# Mismo Trainer — Agent Guide

Mismo Trainer is the web application (Java 17, Tomcat 10.1, `jakarta.servlet`, Hibernate 3 `.hbm.xml` mappings, MySQL) for managing Test Sets, configurations, evaluations, signatures, and weight-set optimization for the Mismo patient-matching engine. The engine itself is the separate `mismo-match` library/repository (`../mismo-match`), consumed as a Maven dependency.

## Who uses this, and how to scope changes

Mismo Trainer supports **a single analyst's work**. That was true when it was built and is still true now. "Production" means the official deployed environment, used by that one person. It has no wider user base, and none is planned. Size every change to fit:

- **Keep solutions simple.** Don't add machinery for scale, concurrency between users, multi-team workflows, or backward compatibility that one user doesn't need.
- **Upgrades can be disruptive.** No one needs protecting while work is in flight during a deploy. Coordinate with the analyst, then redeploy. Rolling upgrades, long deprecation periods and compatibility shims for old clients or in-progress runs aren't needed unless asked for.
- **Trying new ideas in production is acceptable.** Experimental features, settings and optimizer changes can ship and be evaluated in real use, then kept or reverted. Recording what changed (for example, a version marker on runs) is enough. Gating, feature flags and extended staging aren't required.
- **`mismo-match` is in scope.** If the engine has a bug, fix it in `../mismo-match` rather than working around it here, then update the dependency version the trainer uses.

This is about engineering effort, not about the measurement rules below. Those still hold: the narrow field set and the scoring defaults are deliberate design decisions.

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

- **Schema changes** go in `mismo-trainer-web/src/db/unapplied_updates.sql` (applied manually after the daily backup, then folded into a versioned upgrade script on release). Hibernate runs with `hbm2ddl.auto=validate`, so the SQL and the `.hbm.xml` mappings must agree.
- **Organization scoping:** every read and write of tenant data goes through `OrgScope` (owned-or-template read, owner-only write). Never load tenant rows by a client-supplied id alone.
- **Web pages** use `aira-web-components` for look and feel; authentication is InteropHub SSO via `AuthenticationFilter`.
- **Build:** a multi-module Maven build (`docs/island-runner-implementation-plan.md` D2). `mvn clean package` from the repository root builds:
  - `mismo-optimizer` (jar): the genetic-algorithm engine (`World`, `Creature`, `Scorer`, `TestCaseLoader`) and, later, the runner protocol. It must not depend on servlets or Hibernate.
  - `mismo-trainer-web` (war): the web application, producing `mismo-trainer-web/target/mismo.war`.
  - `mismo-runner` (jar): the Island Runner console app, built as the shaded `mismo-runner/target/mismo-runner.jar` (`java -jar`, Java 17+). It talks to the webapp's Runner API at `/api/v1/*` (`RunnerApiServlet`); set `MISMO_RUNNER_HOME` to keep a test runner's identity out of `%LOCALAPPDATA%\Mismo Runner`. The Windows release zip (bundled Java, `Mismo Runner.exe`) is built by `mismo-runner/package-windows.ps1`; see the "Island Runner release" section of `docs/production-deployment-checklist.md`.

  `mismo-match` must be installed in the local Maven repository (`mvn install` in `../mismo-match`).
- **Database-backed tests** (`RunnerApiDatabaseTest`) are opt-in: set `MISMO_TEST_DB_USER`/`MISMO_TEST_DB_PASSWORD` to an account with access to the local `mismo` database. Each test runs in a rolled-back transaction and creates its own organizations and data.
