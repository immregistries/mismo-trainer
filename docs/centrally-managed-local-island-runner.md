# Centrally Managed Local Island Runner

**Status:** Proposed architecture. Implementation decisions are in [`island-runner-implementation-plan.md`](island-runner-implementation-plan.md). In particular, "scoring settings" below are implemented as named, versioned Scoring Profiles (plan D6; see [`optimization-objectives-and-scoring-profiles.md`](optimization-objectives-and-scoring-profiles.md)).

**Purpose:** Define a simple, modern way for a configuration specialist to install a local Mismo optimization runner, manage it through the Mismo Trainer website, and return optimization results to the central application.

## 1. Summary

Mismo optimization should continue to run locally in a command-line application. The local application should retain its roll-and-scroll console interface, but it should no longer require the user to maintain an `island.yml`, place Test Set files manually, or configure optimization settings on the local computer.

Instead, the local application becomes a centrally managed runner:

```text
Mismo Trainer website
    defines the runner assignment
        |
        | configuration, Test Set, and run settings
        v
Local Mismo Island Runner
    performs optimization locally
        |
        | progress and candidate configurations
        v
Mismo Trainer website
    records and presents the results
```

The runner is installed and paired once. Its assignments can then be changed in the website between runs.

A run is immutable after it starts. The runner does not check for configuration changes while optimization is in progress. To use a new assignment, the current run must first be stopped and a new run started.

## 2. Design Goals

The design should:

- keep optimization computation on the specialist's local computer;
- preserve an interactive command-line, roll-and-scroll experience;
- make installation and startup straightforward for a nondeveloper;
- eliminate hand-edited local configuration;
- retrieve the matching configuration and Test Set from Mismo Trainer;
- ensure every run uses an immutable, auditable snapshot of its inputs;
- allow assignments to be managed through the website between runs;
- send progress and candidate configurations back to Mismo Trainer;
- use a new, versioned REST API rather than extending the legacy servlet protocol;
- use unique, revocable credentials for each installed runner;
- remain simple enough to operate and support reliably.

## 3. Non-Goals

The initial design will not:

- modify a running optimization when its website assignment changes;
- poll for new configurations or Test Sets during a run;
- hot-swap a configuration or Test Set;
- restore compatibility with the existing `CentralServlet` Island protocol;
- require compatibility with existing `island.yml` installations;
- run optimization in the web application process;
- support several simultaneous runs inside one local runner process;
- infer that two installations are the same because they have the same display name;
- require the user to install Maven or download the source repository.

This is a new protocol and distribution model. Existing Island behavior can inform the implementation, but does not constrain the new API.

## 4. Core Domain Model

The design separates installation identity, assignment, and execution history.

### 4.1 Runner Installation

A Runner Installation represents one installed copy of the local application on one computer.

Suggested fields:

```text
runner_id
organization_id
display_name
credential_hash
credential_created_at
credential_last_used_at
credential_revoked_at
client_version
protocol_version
last_seen_at
created_at
created_by_user_id
```

Examples of display names:

- `Shelby Laptop`
- `Nathan Development Computer`
- `Office Optimization Workstation`

The display name is for people. `runner_id` is the stable identity used by the system.

Each physical installation should have its own credential. Two installations must not share one access key merely because they have the same name. Unique credentials allow Mismo to identify, audit, rotate, or revoke one installation without affecting another.

### 4.2 Runner Assignment

A Runner Assignment describes what the installation should use for its next run.

Suggested fields:

```text
assignment_id
runner_id
revision
display_name
configuration_id
test_set_id
population_size
scoring_settings
created_at
created_by_user_id
```

The assignment is editable in Mismo Trainer while the runner is idle. Every material edit creates a new assignment revision or an equivalent immutable history entry.

An assignment is not itself a running process. It is a centrally managed instruction for the runner's next run.

### 4.3 Optimization Run

An Optimization Run is one execution of one assignment revision.

Suggested fields:

```text
run_id
runner_id
assignment_id
assignment_revision
configuration_id
configuration_hash
test_set_id
test_set_version
test_set_hash
population_size
scoring_settings_snapshot
client_version
protocol_version
status
started_at
stopped_at
last_reported_at
last_generation
best_score
stop_reason
```

The run snapshots every input that affects optimization. Editing the runner's assignment later does not alter the run record or the local process.

Suggested statuses:

```text
STARTING
RUNNING
STOPPING
STOPPED
FAILED
```

Candidate configurations and progress reports belong to a specific `run_id`.

## 5. Run Immutability

Run immutability is a central rule:

> Once a local optimization run begins, its configuration, Test Set, population size, and scoring settings do not change.

The runner does not check for assignment revisions during the run. The website may allow an administrator to edit the runner's next assignment, but those edits are staged for the next run only.

The transition is deliberately explicit:

```text
Run A starts with assignment revision 4
        |
        | website assignment changes to revision 5
        |
Run A continues unchanged on revision 4
        |
Run A is stopped locally
        |
Runner requests its current assignment
        |
Run B starts with revision 5
```

This prevents mixed or irreproducible results. It also avoids the complexity of coordinating live configuration replacement with an active population.

The website should display both:

- the assignment revision used by the active or most recent run; and
- the revision currently configured for the next run.

If they differ, it can show a simple message such as:

> Assignment changes are ready and will be used the next time this runner starts a run.

## 6. User Workflow

### 6.1 Create and pair a runner

In Mismo Trainer, the configuration specialist or administrator:

1. opens the Runners page;
2. selects **Create Runner**;
3. enters a display name;
4. receives a short-lived pairing code or one-time credential;
5. downloads the generic Mismo Island Runner application if it is not already installed.

On the local computer, the specialist:

1. launches the application;
2. enters the Mismo Trainer server address;
3. enters the pairing code;
4. sees confirmation of the runner name and organization;
5. completes pairing.

The application stores its durable runner identity and credential locally. The short-lived pairing code is no longer usable after successful pairing.

### 6.2 Configure the next run

In the website, the specialist selects the runner and chooses:

- assignment name;
- starting Mismo configuration;
- Test Set and version;
- population size;
- scoring settings;
- any initial run options added later.

The website validates that the selected configuration and Test Set are readable by the runner's organization.

### 6.3 Start locally

The specialist launches the runner and chooses `start`.

The runner:

1. authenticates to Mismo Trainer;
2. retrieves the current assignment;
3. downloads the configuration snapshot;
4. downloads the Test Set snapshot;
5. verifies identifiers and content hashes;
6. displays a summary;
7. creates the run through the REST API;
8. starts local optimization.

The console should show the exact inputs before starting:

```text
Runner:       Shelby Laptop
Assignment:   2026 Production Tuning, revision 5
Configuration: Candidate 142
Test Set:     AIRA Review Set, version 6, 1,651 cases
Population:   1,000

Starting run 928.
```

### 6.4 Run and report

During the run, the local process:

- evolves the population;
- displays local status;
- periodically reports progress;
- periodically uploads the best candidate when it has changed;
- responds to local console commands;
- does not request or apply assignment changes.

Progress reporting is not assignment polling. Responses to progress requests should acknowledge receipt and must not contain replacement run inputs.

### 6.5 Stop and start a new assignment

The specialist stops the run using the local `stop` or `exit` command.

The runner:

1. stops after the current safe processing boundary;
2. uploads the final best candidate;
3. marks the run stopped;
4. returns to an idle prompt or exits.

The next `start` retrieves the current website assignment. If it has changed, the new run uses the new revision.

## 7. Local Application Experience

The local interface remains a console application.

Suggested idle commands:

```text
start       Fetch the current assignment and start a new run
assignment Show the assignment that would be used for the next run
status      Show runner identity and connection status
pair        Pair or re-pair this installation
help        Show available commands
exit        Exit the application
```

Suggested running commands:

```text
status                  Redraw the optimization dashboard
sync                    Upload current progress and best candidate now
weights                 Display scoring weights for this run
script                  Display the current best configuration
diagnose <test-label>   Explain the current best result for one test
stop                    Stop this run and return to the idle prompt
exit                    Stop this run cleanly and exit
help                    Show available commands
```

The running dashboard should include:

```text
Mismo Island Runner 1.0
Runner: Shelby Laptop
Run: 928
Assignment: 2026 Production Tuning, revision 5
Test Set: AIRA Review Set v6
Connection: Connected
Last report: 2 minutes ago

Generation: 14,284
Rate: 328 candidates/second
Best score: 97.42%
```

Pressing Enter may continue to redraw the dashboard, preserving the existing roll-and-scroll style.

## 8. Local State

The user should not maintain operational configuration locally, but the application still needs private implementation state:

```text
Mismo Island Runner/
  application files

User data directory/
  identity.json
  cache/
  checkpoints/
  logs/
```

### Identity state

Contains:

- server base URL;
- runner identifier;
- durable credential or secure reference to it;
- pairing metadata.

### Assignment cache

Contains the exact configuration and Test Set downloaded for a run. Cached data is identified by content hash and must not be confused with the current website assignment.

### Checkpoint state

At minimum, record:

- run identifier;
- assignment revision;
- generation;
- best configuration;
- best score;
- timestamp;
- Test Set hash;
- random seed or run seed, when supported.

The first implementation may use checkpoints only for diagnosis and recovery of the best result. Restoring an entire population can be added later if justified.

### Logs

Verbose network and diagnostic messages should go to a local log. The console should present short, actionable messages.

## 9. Distribution

The application should be distributed independently of the Mismo Trainer WAR.

The preferred end-user package is a portable Windows application containing its own Java runtime:

```text
mismo-island-runner/
  mismo-island-runner.exe
  app/
  runtime/
  README.txt
  VERSION.txt
```

The user should be able to download, extract, and run it without:

- Maven;
- a source checkout;
- administrator access;
- hand-built classpaths;
- a separately installed Java runtime;
- an editable YAML configuration.

A smaller executable JAR distribution requiring Java 17 may be useful for development and early testing, but it should not be the preferred configuration-specialist experience.

The application binary should be generic. Pairing establishes the runner identity. A new application build should not be required when the assignment changes.

## 10. REST API

The Runner API should be a new, versioned JSON API. It should not extend the form-encoded `CentralServlet` protocol.

Suggested base path:

```text
/api/v1/runners
```

Exact resource names may change during implementation, but the domain boundaries should remain clear.

### 10.1 Pairing

```text
POST /api/v1/runner-pairings
```

Exchanges a short-lived pairing code for a durable runner identity and credential.

Example request:

```json
{
  "pairingCode": "ABCD-EFGH",
  "clientVersion": "1.0.0",
  "protocolVersion": "1"
}
```

Example response:

```json
{
  "runnerId": 37,
  "runnerName": "Shelby Laptop",
  "organizationName": "AIRA",
  "credential": "one-time-returned-secret"
}
```

### 10.2 Runner identity and health

```text
GET  /api/v1/runners/me
POST /api/v1/runners/me/heartbeats
```

These endpoints validate authentication and record version/connectivity information. A heartbeat is operational reporting, not a configuration-change mechanism.

### 10.3 Current assignment

```text
GET /api/v1/runners/me/assignment
```

This endpoint is called while idle, immediately before starting a run. It is not called during active optimization.

Example response:

```json
{
  "assignmentId": 81,
  "revision": 5,
  "name": "2026 Production Tuning",
  "configuration": {
    "id": 142,
    "hash": "configuration-content-hash",
    "url": "/api/v1/configurations/142/content"
  },
  "testSet": {
    "id": 31,
    "version": 6,
    "hash": "test-set-content-hash",
    "caseCount": 1651,
    "url": "/api/v1/test-sets/31/versions/6/content"
  },
  "populationSize": 1000,
  "scoringSettings": {
    "shouldMatch_Matches": 12
  }
}
```

### 10.4 Configuration and Test Set content

```text
GET /api/v1/configurations/{configurationId}/content
GET /api/v1/test-sets/{testSetId}/versions/{version}/content
```

Content responses should include or be validated against cryptographic hashes from the assignment. A Test Set version used by a run must be immutable.

The Test Set representation should be explicitly versioned. A new JSON representation is preferable to carrying the legacy custom flat-file format into a new protocol, although the server may initially translate existing data into that representation internally.

### 10.5 Runs

```text
POST /api/v1/runs
GET  /api/v1/runs/{runId}
POST /api/v1/runs/{runId}/progress
POST /api/v1/runs/{runId}/candidates
POST /api/v1/runs/{runId}/stop
POST /api/v1/runs/{runId}/fail
```

Creating a run snapshots the assignment revision and verifies that the submitted configuration and Test Set hashes match the assignment.

Progress payloads may include:

```json
{
  "generation": 14284,
  "candidateRate": 328.4,
  "bestScore": 0.9742,
  "reportedAt": "2026-10-23T18:42:00Z"
}
```

Candidate payloads should contain the candidate configuration, its score, generation, signature/hash, and enough metadata to tie it unambiguously to the run.

The server should make candidate submission idempotent so retrying after a network failure does not create accidental duplicates.

## 11. Connectivity Model

An Internet connection is required to:

- pair the runner;
- retrieve an assignment;
- download the configuration and Test Set;
- create a run;
- report progress and candidates;
- stop or fail a run cleanly on the server.

The runner should not start a new run from an unconfirmed assignment when Mismo Trainer is unavailable.

Temporary loss of connectivity during an already-started run does not require changing the run. The runner may continue using its immutable local snapshot while queuing progress and the latest best candidate for retry. The console should clearly show that it is disconnected.

This resilience does not introduce configuration polling. On reconnection, the runner resumes reporting for the same `run_id`; it does not request a new assignment until the run has stopped.

## 12. Authentication and Security

The new Runner API should require:

- HTTPS outside explicitly enabled local development;
- a unique credential for every Runner Installation;
- server-side storage of credential hashes rather than raw credentials;
- organization scoping on every runner, assignment, configuration, Test Set, run, and candidate query;
- revocable and replaceable credentials;
- short-lived, single-use pairing codes;
- no secrets in URLs, command history, console output, or logs;
- request size limits and rate limits;
- content hashes for downloaded run inputs;
- audit history for assignment and credential changes;
- explicit client and protocol versions.

Bearer-token authentication is appropriate for the initial machine client. The durable credential should be stored using the best secure local facility available for the target platform. If secure OS credential storage is not implemented initially, the fallback file must have restricted permissions and be clearly treated as a secret.

## 13. Website Experience

The website should include a Runners area showing:

| Runner | State | Active/last run | Current generation | Best score | Last seen |
|---|---|---|---:|---:|---|
| Shelby Laptop | Running | Run 928 | 14,284 | 97.42% | 20 seconds ago |
| Nathan Development | Offline | Run 915 | 2,113 | 96.81% | Yesterday |

The runner detail page should provide:

- runner identity and version;
- last connection time;
- credential creation, replacement, and revocation;
- current assignment for the next run;
- active or most recent run;
- a clear indication when the next assignment differs from the running assignment;
- assignment history;
- run history;
- progress and candidate configurations;
- Test Set and configuration links;
- download and pairing instructions.

Editing the assignment while a run is active is allowed only as preparation for the next run. The website must not imply that the active run has changed.

## 14. Stop Semantics

The initial control model is intentionally local and simple:

- the user types `stop` or `exit` in the Runner console;
- the runner stops at a safe boundary;
- the final result is reported;
- the server records the run as stopped;
- the next start fetches the current assignment.

The website does not need to remotely interrupt a running process in the first implementation. Supporting remote stop would require the runner to receive control messages while running, which is a separate feature and should not be conflated with assignment management.

An administrator may mark a run administratively abandoned or cancelled in the website, but that alone cannot guarantee that a disconnected local process has stopped.

## 15. Error Handling

Startup failures should be explicit and actionable:

```text
Unable to start a run:
The assigned Test Set could not be downloaded.

No optimization has started.
Use `start` to try again or `help` for assistance.
```

The runner should refuse to start when:

- authentication fails;
- no assignment exists;
- the assigned configuration is unavailable;
- the Test Set is unavailable;
- a downloaded content hash does not match;
- the client or protocol version is unsupported;
- the assignment is invalid;
- run creation fails.

During a run, reporting failures should change the displayed connection state and be retried with bounded exponential backoff. They should not silently change the optimization inputs.

## 16. Versioning and Compatibility

The client should report:

- application version;
- Runner API protocol version;
- Java/runtime version;
- Mismo matching-engine version;
- operating system information useful for support.

The API should return a structured incompatibility response when a client is too old or otherwise unsupported. The website should show when a runner needs an application update.

API versioning and application versioning are separate. A new application release does not necessarily require a new API version.

## 17. Recommended Implementation Sequence

### Phase 1: Domain and API foundation

- Add Runner Installation, Assignment, and Optimization Run persistence.
- Define immutable Test Set versions or snapshots.
- Implement pairing and runner authentication.
- Implement assignment retrieval and content downloads.
- Implement run creation, progress, candidate, stop, and failure endpoints.
- Add API integration tests, organization-isolation tests, and idempotency tests.

### Phase 2: New local runner

- Extract the optimization engine from web-application concerns.
- Build the new REST client.
- Add pairing and secure identity storage.
- Add assignment/configuration/Test Set download and validation.
- Preserve and refine the interactive console.
- Add local logs, reporting retries, shutdown handling, and best-result checkpoints.
- Build an executable development JAR.

### Phase 3: Website management

- Add runner creation and pairing UI.
- Add assignment creation/editing.
- Add current state and last-seen display.
- Add immutable run history and candidate inspection.
- Clearly distinguish the active run snapshot from the next assignment.

### Phase 4: End-user distribution

- Produce a portable Windows application with a bundled runtime.
- Add download instructions and checksums.
- Test installation and operation on a clean nondevelopment computer.
- Document credential replacement, application upgrades, logs, and troubleshooting.

## 18. Acceptance Criteria

The architecture is successful when a configuration specialist can:

1. create and name a Runner in the website;
2. download and launch the local application without Maven or a source checkout;
3. pair it using a short-lived code;
4. assign a configuration and Test Set entirely through the website;
5. start the local optimization without editing files;
6. observe roll-and-scroll progress locally;
7. see progress and candidates in the website;
8. change the website assignment without affecting the active run;
9. stop the active run locally;
10. start again and receive the new assignment;
11. inspect a durable record showing exactly which configuration, Test Set version, settings, and client version produced every result.

## 19. Architectural Principle

The design can be summarized as:

> Install once, pair once, configure centrally, and run immutable optimization assignments locally.

The website is the control and audit plane. The local Runner is the computation plane. They communicate through a new authenticated REST API, but assignment changes occur only between runs.
