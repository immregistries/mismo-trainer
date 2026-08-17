# Mismo Patient Matching Engine
## Conceptual Overview and Documentation Starting Point

**Status:** Initial documentation draft for code analysis and refinement  
**Scope:** Mismo-Match and Mismo-Trainer software only  
**Out of scope:** The broader patient-matching measurement program and IIS measurement methodology, except where needed to explain current software use

---

## Verification Status (v1 code review complete)

The "Verify in code" items throughout this document have now been checked against the actual v1 codebase (`mismo-trainer` source, plus bytecode inspection of the compiled `mismo-match-1.1.jar` dependency, which is not present as source in this repo). Full findings live in five companion documents in this same `docs/` directory:

- **[`matching-engine.md`](matching-engine.md)** — patient data model, detectors, the decision-network math (traced to exact formulas), final classification logic, the signature algorithm, and the Mismo-Match public API surface (resolves §5, §8–§13).
- **[`data-model.md`](data-model.md)** — the real database schema, the actual `Patient` field list, and production data volumes (resolves §17, §18, §22).
- **[`trainer-pages.md`](trainer-pages.md)** — a page-by-page inventory of all 21 servlets: what each does, what it reads/writes, its auth model, and what's broken (resolves §14–§16, §24.3).
- **[`optimization-and-islands.md`](optimization-and-islands.md)** — the evolutionary algorithm, the Island sync protocol, and `GenerateWeightsServlet` (resolves §19–§21).
- **[`modernization-notes.md`](modernization-notes.md)** — the conceptual-map layer: where v1 diverges from this document's model, concrete bugs, security findings, and a regularized terminology table (addresses §24.4, §27–§30).

**Two corrections to this document's own content, confirmed in code:**

1. §10 below describes three decision networks (Match, Not a Match, Twin). There are actually **four** — a `Missing` network also exists, scoring how much of a pair's evidence is simply absent, and it is a first-class, equal-weight input to the final classification alongside Not a Match and Twin: it participates exactly as strongly as the other two "downgrade" networks. See `matching-engine.md` §3 and §7, and `modernization-notes.md` §1.
2. §9's open question about how min/max weights combine is now answered: each node linearly rescales its own 0–1 output into its configured `[min,max]`, a parent sums its enabled children's rescaled contributions and caps the sum at 1.0 — **and, not anticipated by this document, zeroes the sum entirely if it's under 0.5** before passing it to its own parent. This 0.5 activation gate applies at every level of the tree, not just the four top-level networks. See `matching-engine.md` §4–§6 and `modernization-notes.md` §2.

The rest of this document's conceptual framing was confirmed accurate by the code review; only these two details needed correcting. Treat the "Verify in code" callouts throughout the rest of this document as now answered by the companion documents above rather than removing them inline.

Everything below this point is the original pre-code-review conceptual draft, preserved as-is for context.

---

## 1. Purpose of This Document

This document captures the current conceptual understanding of the Mismo software from existing presentation material and maintainer knowledge.

It is intentionally **not** a complete implementation reference. Its primary purpose is to give a code-review agent or developer enough context to inspect the applications and determine:

- how the current implementation corresponds to the intended design;
- where implementation details are missing from the documentation;
- which concepts need clearer definitions;
- which workflows remain partly outside the application;
- what should be preserved, regularized, or modernized in Mismo-Trainer.

Sections labeled **Verify in code** identify areas where the implementation should be examined before this documentation is treated as authoritative.

---

# 2. What Is Mismo?

Mismo is a **patient matching engine**.

Its core job is deliberately narrow:

> Given two patient records, determine whether they represent a **Match**, **Possible Match**, or **Not a Match**.

Mismo can be incorporated into production patient-matching solutions if it is properly configured for the environment in which it will be used. The open-source project does **not** guarantee that its default configuration is appropriate for any particular production setting.

Mismo is only one component of a complete patient-matching solution. It evaluates already-selected patient pairs. It does not locate candidate records or manage patient data.

Mismo was originally created to support improved patient matching for a public health department. It was used in a production setting. The project is now maintained primarily to support patient-matching measurement work, where Mismo is used in a non-critical production role to evaluate whether patient record pairs appear to have been correctly merged or left separate.

That measurement program is outside the scope of this document.

---

# 3. Software Components

Mismo consists of two primary software components.

## 3.1 Mismo-Match

**Mismo-Match** is the runtime patient-matching engine.

It:

1. accepts two patient records;
2. evaluates the relationship between their demographic and other supported attributes;
3. propagates evidence through a fixed network of detectors and signals;
4. applies a configured set of weights;
5. returns one of three classifications:
   - `MATCH`
   - `POSSIBLE_MATCH`
   - `NOT_A_MATCH`
6. generates a **match signature** describing the general characteristics of the pair.

Mismo-Match is intended to be embedded in another application or system.

---

## 3.2 Mismo-Trainer

**Mismo-Trainer** is the configuration-development and training environment for Mismo-Match.

It is used to:

- maintain labeled patient-pair test cases;
- maintain candidate Mismo weight sets;
- evaluate weight sets against training data;
- coordinate distributed optimization processes;
- generate improved weight configurations;
- review disagreements between expected and actual classifications;
- support creation and management of new training cases;
- ultimately produce configurations for use by Mismo-Match.

Historically, some of this workflow has been handled in Excel spreadsheets. A major goal of the current Mismo-Trainer work is to make the application itself support the full configuration and training workflow.

---

# 4. System Boundary

Mismo is intentionally not a complete master-patient-index or record-linkage system.

## 4.1 Mismo Does

Mismo:

- compares two patient records;
- evaluates matching evidence;
- classifies the pair;
- exposes a match signature;
- supports configuration through trained weights;
- supports development of those configurations through Mismo-Trainer.

## 4.2 Mismo Does Not

Mismo does **not**:

- connect to a patient database;
- search a patient population;
- perform blocking or candidate selection;
- determine which records should be compared;
- merge patient records;
- update source patient data;
- manage a master patient index;
- perform the larger patient-matching measurement program.

A system using Mismo in production must provide the surrounding architecture needed to identify candidate patient pairs, retrieve patient data, act on Mismo's result, and manage patient records.

---

# 5. Patient Data Model

Conceptually, a patient record in Mismo is a collection of named values similar to a **hash map**.

The matching engine is designed to work with patient records that may contain different subsets of supported data.

## 5.1 Optional Data

All patient attributes are optional.

Mismo must therefore operate when:

- both records contain a value;
- only one record contains a value;
- neither record contains a value.

Missing data is an expected condition and is part of the matching model rather than an exceptional error.

## 5.2 Supported Patient Concepts

Historically supported attributes include concepts such as:

- first name;
- middle name;
- last name;
- alias;
- name suffix;
- date of birth;
- sex/gender;
- patient or medical-record identifiers;
- address;
- phone;
- guardian first name;
- guardian last name;
- mother's maiden name;
- birth status;
- birth order;
- shot/immunization history.

The current measurement use of Mismo uses only a subset of the available fields.

### Verify in code

Document:

- the exact current patient data structure;
- all supported field names;
- whether fields can contain multiple values;
- any type constraints;
- date representation;
- address representation;
- normalization performed before detector evaluation;
- whether legacy fields remain supported but unused;
- whether there are currently separate "focus field" configurations.

---

# 6. Match Classifications

Mismo does not return a percentage likelihood that two records represent the same person.

It returns one of three classifications.

## 6.1 Match

`MATCH` means that the patient pair has characteristics that correspond to pairs that experts classified as matches in the training data.

Operationally, this is intended to identify pairs that can reasonably be treated as representing the same patient under the trained matching model.

## 6.2 Possible Match

`POSSIBLE_MATCH` represents an intentionally ambiguous category.

These are patient pairs for which the available information does not support confident automatic classification as either a match or a non-match.

Possible matches reflect real-world situations in which:

- evidence is incomplete;
- evidence conflicts;
- additional information may be required;
- manual review may be needed before records can safely be merged.

Ideally, a patient-matching process would minimize the number of possible matches, but the category is necessary because real patient data can be ambiguous.

## 6.3 Not a Match

`NOT_A_MATCH` means that the pair has characteristics corresponding to patient pairs that experts classified as not representing the same person.

## 6.4 Training-Based Meaning

The meaning of all three categories is ultimately grounded in labeled training examples.

Mismo is trained to reproduce the classification judgments represented in its training data rather than to produce a mathematically meaningful probability of identity.

---

# 7. Matching Architecture

Mismo's internal architecture is loosely modeled on a neural-network concept, but it is an expert-designed signal network rather than a conventional learned neural network.

The overall structure of the network is fixed.

Configuration primarily adjusts the strength with which signals propagate through that network.

The conceptual flow is:

```text
Patient A + Patient B
        |
        v
    Detectors
        |
        v
 Low-level signals
        |
        v
 Higher-level signals
        |
        v
 Decision networks
        |
        v
 Match / Possible Match / Not a Match
```

The architecture is designed so that matching logic can be understood as progressively combining increasingly abstract evidence.

---

# 8. Detectors

A **detector** is the most basic sensing unit in Mismo.

A useful analogy is a light-sensitive diode in an imaging system: detectors define what Mismo is capable of "seeing" when it looks at two patient records.

Each detector:

- evaluates some defined relationship between values in Patient A and Patient B;
- uses a defined comparison method;
- produces a floating-point signal between `0.0` and `1.0`.

Many detectors are effectively binary and return only:

- `0.0` — the detector did not observe the condition;
- `1.0` — the detector observed the condition.

Other detectors may return intermediate values.

## 8.1 Reusable Detector Logic

Detectors may reuse common comparison logic.

For example, an exact-match detector may compare two strings and return:

- `1.0` if they are exactly equal;
- `0.0` otherwise.

Other detector types may evaluate concepts such as similarity rather than exact equality.

The detector definitions themselves are not tuned by Mismo-Trainer. Their implementation is fixed in code.

A detector can, however, effectively be disabled by configuring its downstream influence so that its output contributes no meaningful weight.

### Verify in code

Identify and document:

- every detector;
- its name or identifier;
- which patient fields it consumes;
- its comparison algorithm;
- its output range;
- whether it is binary or continuous;
- normalization performed before comparison;
- reusable detector utility classes;
- handling of missing values;
- whether detector outputs are directly exposed through APIs.

---

# 9. Signals and the Matching Network

Detector outputs feed into **signals**.

Signals progressively combine lower-level evidence into more abstract evidence about the patient pair.

Examples of conceptual signal groupings have historically included:

- first name;
- middle name;
- last name;
- date of birth;
- patient identifier;
- guardian;
- location;
- household;
- person;
- birth information;
- shot history;
- overall match evidence.

A signal produces a floating-point value from `0.0` to `1.0`.

Signals may receive input from:

- detectors;
- other signals;
- multiple lower-level components.

## 9.1 Min and Max Configuration

Signals have configurable minimum and maximum values that act as weights and limits on how strongly input evidence is passed to the next level.

These configuration values are the primary parameters optimized by Mismo-Trainer.

Conceptually:

```text
Detector/Signal output
        |
        v
 apply configured min/max influence
        |
        v
 combine with other inputs
        |
        v
 next-level signal
```

The network structure is fixed, but the configured weights controlling propagation through the network are adjustable.

### Verify in code

Document precisely:

- how `min` and `max` are mathematically applied;
- how multiple signal inputs are combined;
- whether sums are capped at `1.0`;
- whether any negative evidence exists;
- defaults for each weight;
- serialization format for weights;
- configuration validation;
- whether weights can be changed at runtime.

---

# 10. Decision Networks

The final classification is not produced from a single "match percentage."

Mismo uses multiple detection networks representing different kinds of evidence.

The known decision networks include:

- a **Match** network;
- a **Not a Match** network;
- a **Twin** network;
- a **Missing** network — scores how much of the pair's evidence is simply absent rather than conflicting. *(Confirmed in code review; see `matching-engine.md` §3, §7. Not part of the original documentation draft.)*

A final `MATCH` requires positive match evidence without contradictory Not-a-Match or Twin evidence.

The thresholds and decision logic that turn those network outputs into the final three-way classification are fixed rather than trainer-configurable.

This is important because Mismo does **not** interpret its final result as:

> "These records are an 87% match."

Intermediate signal strengths are meaningful internally to the algorithm, but they are not trained or calibrated as probabilities and should not be presented as a probabilistic patient-match score.

### Verify in code

Document:

- the final decision algorithm;
- thresholds used;
- precedence among Match, Not-a-Match, and Twin signals;
- how `POSSIBLE_MATCH` is selected;
- treatment of exact ties or boundary values;
- any special-case decision rules.

---

# 11. Determinism and Symmetry

Mismo matching is intended to be:

## 11.1 Deterministic

Given:

- the same Patient A;
- the same Patient B;
- the same Mismo configuration;

the engine should return the same result.

## 11.2 Symmetric

Matching should be symmetric:

```text
match(A, B) == match(B, A)
```

The ordering of the two records should not affect the final classification.

### Verify in code

Confirm both properties with implementation review and automated tests.

---

# 12. Match Signatures

The **match signature** is a major Mismo concept and is distinct from the final classification.

A signature is a progressively generalized summary of what Mismo's detectors observed about a patient pair.

For example, a signature may capture a pattern conceptually similar to:

```text
first names match
last names do not match
dates of birth match
```

The signature does not expose the full patient data. Instead, it creates a reduced representation of the matching characteristics observed in the pair.

## 12.1 Progressive Information Reduction

Signature generation progressively reduces or "blurs" information.

The signature is structured so that:

- the left side is more general;
- additional information toward the right makes the signature more specific.

This creates a hash-like representation that allows similar kinds of patient pairs to be grouped together.

## 12.2 Why Signatures Matter

Signatures make it possible to understand **what kinds of patient pairs** Mismo is seeing without relying only on the final match classification.

This is especially useful for improving training.

For example:

1. a particular signature appears frequently;
2. users indicate that pairs with this signature are being classified incorrectly;
3. maintainers create new test cases representing that matching pattern;
4. experts establish the expected classification;
5. the Trainer uses the new cases in future configuration optimization.

Signatures can also reveal missing matching concepts.

If experts classify different patient pairs with the same signature differently in a consistent way, it may indicate that humans are using information that Mismo does not currently detect. That can suggest a need for:

- a new patient field;
- a new detector;
- a new signal;
- refinement of the signature model.

## 12.3 Privacy and Measurement Use

Signatures also provide a way for remote systems to report the kinds of patient-pair patterns they encounter without returning the underlying identifiable patient records.

The broader measurement workflow that uses signatures is outside the scope of this document, but signature generation itself is a core Mismo-Match capability.

### Verify in code

Document:

- exact signature syntax;
- signature generation algorithm;
- ordering rules;
- progressive/generalized levels;
- detector-to-signature mappings;
- whether signatures are stable across software versions;
- whether signature format is considered a public API;
- examples of common signatures;
- whether all match classifications receive signatures.

---

# 13. Mismo-Match API

Conceptually, Mismo-Match exposes functionality equivalent to:

```java
PatientMatcher matcher = new PatientMatcher();

Patient patientA = new Patient();
Patient patientB = new Patient();

// Populate whichever fields are available.

PatientMatchDetermination result =
    matcher.match(patientA, patientB);
```

The matcher then returns a three-way patient-match determination.

The actual API may expose richer objects containing the classification, signature, and internal matching information.

### Verify in code

Document:

- packages and primary public classes;
- patient object/API;
- matcher construction;
- configuration loading;
- return object;
- classification enumeration;
- signature access;
- error handling;
- threading assumptions;
- statefulness;
- performance considerations;
- Maven/Gradle artifact information;
- supported Java version.

---

# 14. Mismo-Trainer Overview

Mismo-Trainer exists to answer a central question:

> Given a collection of patient pairs with expert-defined expected classifications, what Mismo weight configuration best reproduces those expectations?

The Trainer coordinates the process of finding increasingly better candidate weight sets.

Conceptually:

```text
Labeled patient-pair training set
              |
              v
        Mismo-Trainer
              |
              v
      optimization process
              |
              v
      candidate weight sets
              |
              v
   evaluate against training set
              |
              v
     select better solutions
              |
              v
     optimized configuration
              |
              v
         Mismo-Match
```

---

# 15. Training and Test Cases

A training/test case fundamentally contains:

- Patient A;
- Patient B;
- the expected classification.

Expected classifications use the same three categories produced by Mismo-Match:

- Match;
- Possible Match;
- Not a Match.

Experts establish the expected result.

The training set therefore represents the community's or maintainers' judgment about how representative patient-pair scenarios should be classified.

## 15.1 Sources of Test Cases

Historically, test cases have been created through a combination of:

- manually created scenarios;
- Mismo-generated patient pairs;
- spreadsheet-based workflows;
- feedback derived from observed match signatures.

Some of these cases have been maintained in Excel and imported into Mismo-Trainer.

The intended future state is for Mismo-Trainer to become the authoritative system for managing the complete training workflow.

### Verify in code

Document:

- test-case database schema;
- patient serialization;
- expected-result representation;
- metadata available on tests;
- import formats;
- export formats;
- test grouping;
- test status;
- provenance;
- review status;
- versioning;
- duplicate handling.

---

# 16. Historical Excel Workflow

Historically, Mismo-Trainer did not provide a sufficiently usable interface for expert classification of test cases.

The workflow therefore included Excel:

```text
Trainer generates patient pairs
          |
          v
 export to spreadsheet
          |
          v
 experts classify pairs
          |
          v
 import classifications
          |
          v
 Trainer optimization
```

This is not considered an essential architectural requirement.

A goal of the current modernization is to move this workflow into Mismo-Trainer so that test creation, expert review, classification, training, optimization, and configuration management can all occur within one coherent application.

---

# 17. Weight Sets

A **weight set** is a proposed Mismo configuration.

It contains the tunable values that determine how strongly detector and signal evidence propagates through the matching network.

Weight sets are candidate solutions to the matching problem represented by a particular training set.

Historically, weight sets appear to be named and versioned, although configuration management needs to be reviewed and regularized.

### Verify in code

Document:

- weight-set entity/schema;
- naming;
- identifiers;
- versioning;
- status;
- parent/ancestry relationships;
- creation timestamp;
- creator/process;
- association with training sets;
- score storage;
- promotion to production/current configuration;
- import/export.

---

# 18. Training Sets

A **training set** or **test set** is a named collection of labeled patient-pair test cases used to evaluate candidate weight sets.

A candidate weight set is meaningful only in the context of the training set against which it was evaluated.

Training sets should eventually support clear lifecycle and provenance management.

### Verify in code

Determine:

- whether "test set" and "training set" are identical concepts in the implementation;
- how cases are assigned to sets;
- whether cases may belong to multiple sets;
- versioning behavior;
- locking/finalization;
- current database constraints.

---

# 19. Scoring Candidate Weight Sets

The Trainer evaluates a candidate weight set by running Mismo-Match against the labeled training cases and comparing actual classifications with expected classifications.

The optimization objective is to minimize disagreements between:

```text
expected classification
```

and:

```text
Mismo classification
```

A separate set of **steering weights** is used to score the quality of a candidate configuration.

These steering values control how different kinds of correct and incorrect classifications affect the overall optimization score.

This score is a property of a **weight-set evaluation**, not a patient-match probability.

### Verify in code

Document:

- scoring formula;
- steering-weight definitions;
- whether different error types receive different penalties;
- how possible matches are scored;
- handling of unclassified tests;
- whether scores are normalized;
- score persistence;
- ranking logic;
- comparison of scores across different training sets.

---

# 20. Evolutionary Optimization

Mismo-Trainer uses an evolutionary optimization algorithm to search for better weight configurations.

The conceptual process is:

1. begin with one or more candidate weight sets;
2. create random variations of existing configurations;
3. also generate some random candidate configurations;
4. evaluate each candidate against the training set;
5. score each candidate;
6. retain the best-performing solutions;
7. create new candidate generations by combining values from two successful ancestor configurations;
8. repeat;
9. preserve the highest-scoring configuration found.

The algorithm generally finds substantial improvements quickly.

As optimization continues, improvements become less frequent. After running for several hours without meaningful improvement, the best configuration found is treated as the practical solution.

This is an optimization process and should not be described as producing a probabilistically learned model.

### Verify in code

Document:

- population size;
- initialization;
- random-generation algorithm;
- mutation strategy;
- crossover/permutation strategy;
- selection strategy;
- elitism/best-solution preservation;
- convergence/stopping behavior;
- random seeding;
- reproducibility;
- score comparison;
- performance characteristics.

---

# 21. Distributed "Island" Optimization

Optimization work can be performed by locally executed Java command-line processes referred to as **islands**.

An island:

1. connects to the central Mismo-Trainer application;
2. receives or obtains current optimization information;
3. runs the evolutionary algorithm locally;
4. discovers candidate weight sets;
5. sends improved weight sets and progress information back to the Trainer.

Multiple islands can operate concurrently.

Because improved candidate configurations are uploaded to the central Trainer, islands can indirectly share advances. A strong solution found by one island can become part of the optimization process used by other islands.

This distributed architecture remains conceptually acceptable and is not currently targeted for replacement.

### Verify in code

Document:

- island executable/project;
- authentication;
- registration;
- polling/communication protocol;
- REST endpoints or other transport;
- work assignment;
- upload frequency;
- conflict handling;
- synchronization;
- how islands obtain the current best configuration;
- failure/reconnect behavior;
- security assumptions;
- supported concurrent island count.

---

# 22. Mismo-Trainer Data Model

The Trainer database currently contains some combination of:

- training/test cases;
- patient-pair data;
- expected classifications;
- training/test sets;
- candidate weight sets;
- candidate scores;
- optimization progress;
- possibly users and operational state.

The current database and workflow have not been maintained as a clean, comprehensive source of truth.

Some important information has historically remained in spreadsheets and been imported when needed.

One modernization objective is to regularize the Trainer's data model so that the application becomes the authoritative location for the complete training/configuration process.

### Verify in code

The code-review phase should create an actual data-model inventory showing:

- tables/entities;
- keys;
- relationships;
- lifecycle/status fields;
- unused legacy structures;
- data stored outside the database;
- import/export dependencies.

---

# 23. Configuration Lifecycle

The intended lifecycle is conceptually:

```text
Create/maintain test cases
        |
        v
Expert classification
        |
        v
Create training set
        |
        v
Run candidate weight sets
        |
        v
Score results
        |
        v
Run evolutionary optimization
        |
        v
Review best configuration
        |
        v
Adopt/version configuration
        |
        v
Use configuration in Mismo-Match
```

The existing application supports portions of this lifecycle, with some steps historically performed outside the application.

A primary modernization goal is to make this lifecycle explicit and fully supported.

### Verify in code

For each step above, determine:

- whether it exists;
- where it occurs;
- which UI supports it;
- which database objects support it;
- whether manual database or spreadsheet work is required;
- what should be added or redesigned.

---

# 24. Mismo-Trainer Modernization Goals

The immediate modernization goals are:

## 24.1 InteropHub Single Sign-On

Mismo-Trainer should use InteropHub for authentication/SSO rather than maintaining an isolated authentication experience.

### Verify in code

Identify:

- current authentication framework;
- user/session model;
- authorization assumptions;
- changes required for InteropHub SSO.

## 24.2 AIRA Look and Feel

Mismo-Trainer should be updated to use the current AIRA visual design and application conventions.

This is primarily a presentation and usability change and should not alter the patient-matching model.

## 24.3 Complete In-Application Workflow

Mismo-Trainer should evolve from a partial tool plus spreadsheets into a proper application supporting the full training lifecycle.

This includes, at minimum:

- creating and managing test cases;
- expert review/classification;
- organizing training sets;
- managing weight sets;
- running/monitoring optimization;
- reviewing mismatches;
- selecting configurations;
- preserving versions and provenance.

## 24.4 Regularize Concepts and Terminology

The modernization should establish consistent definitions and relationships for concepts such as:

- detector;
- signal;
- signature;
- test case;
- training set;
- weight set;
- steering weights;
- optimization run;
- island;
- configuration.

---

# 25. Important Design Principles

The following principles should be preserved unless code analysis shows a compelling reason to revise them.

## 25.1 Pair Evaluation Is the Core Boundary

Mismo-Match compares exactly two patient records.

Candidate discovery belongs outside Mismo.

## 25.2 Missing Data Is Normal

Patient fields are optional, and the matching model must work under incomplete data.

## 25.3 The Final Classification Matters More Than Intermediate Scores

Training is based on producing the correct final classification.

Intermediate signal strengths are implementation evidence, not patient-match probabilities.

## 25.4 Matching Should Remain Explainable

The detector/signal architecture and match signatures provide an important conceptual explanation of how Mismo distinguishes patient-pair patterns.

## 25.5 Configuration Is Environment-Specific

A weight set should not be assumed to be universally appropriate.

The open-source project's default configuration is not a guarantee of safe production matching.

## 25.6 Training Data Defines Expected Behavior

The practical meaning of Match, Possible Match, and Not a Match is grounded in expert-classified training scenarios.

## 25.7 Signatures Support Continuous Improvement

Observed signatures provide a feedback mechanism for identifying:

- poorly trained scenarios;
- ambiguous patterns;
- missing detectors;
- missing patient information;
- areas requiring additional test cases.

---

# 26. Terminology

| Term | Working Definition |
|---|---|
| **Mismo** | The overall patient-matching engine project. |
| **Mismo-Match** | Runtime library/component that compares two patient records. |
| **Mismo-Trainer** | Application used to develop and evaluate Mismo-Match configurations. |
| **Patient Pair** | Two patient records being evaluated together. |
| **Detector** | Lowest-level comparison unit that observes a defined relationship between patient values and emits a value from 0.0 to 1.0. |
| **Signal** | A 0.0–1.0 value that combines detector and/or lower-level signal outputs into higher-level evidence. |
| **Missing Network** | The fourth decision network (alongside Match, Not a Match, Twin); scores absence of evidence rather than conflicting evidence. Confirmed in code review, see `matching-engine.md` §3, §7. |
| **Weight** | Configurable value controlling how strongly evidence propagates through the signal network. |
| **Weight Set** | A complete proposed set of configurable Mismo weights. |
| **Test Case** | Patient A, Patient B, and an expert-defined expected classification. |
| **Training/Test Set** | A collection of labeled test cases used to evaluate weight sets. |
| **Steering Weights** | Values used to score how well a candidate weight set reproduces expected classifications. |
| **Match Signature** | Generalized representation of the detector pattern observed for a patient pair. |
| **Island** | Locally run Java optimization process that collaborates with the central Trainer. |
| **Match** | Pair classified as representing the same patient according to the trained model. |
| **Possible Match** | Ambiguous pair requiring additional information or review. |
| **Not a Match** | Pair classified as representing different patients according to the trained model. |

---

# 27. Questions for Code Analysis

The next documentation phase should answer the following from the implementation.

## Mismo-Match

- What modules/projects make up Mismo-Match?
- What are its public APIs?
- How is a patient represented?
- What fields are supported?
- What normalization occurs?
- What detectors exist?
- How does each detector work?
- What signals exist?
- How are signal inputs combined?
- How are min/max weights applied mathematically?
- What are the Match, Not-a-Match, and Twin networks?
- What exact logic produces the three final classifications?
- How is configuration loaded?
- What is the configuration file/schema?
- How is the signature generated?
- What information is available in the result object?
- Is the current implementation demonstrably deterministic and symmetric?
- What unit/integration tests exist?

## Mismo-Trainer

- What framework and architecture does the Trainer use?
- What is the current database schema?
- How are test cases stored?
- How are training sets stored?
- How are weight sets stored?
- How are steering weights represented?
- What functionality exists for test-case generation?
- What functionality exists for expert classification?
- What spreadsheet import/export remains?
- How is a weight set scored?
- How is the evolutionary algorithm implemented?
- How are generations, mutations, and crossover handled?
- How are optimization runs represented?
- How does an island connect to the Trainer?
- What APIs do islands use?
- How are improvements shared across islands?
- How is the best configuration selected?
- How is a configuration exported for Mismo-Match?
- What user/account model exists today?
- What must change for InteropHub SSO?
- Which pages/workflows should be retained, redesigned, or removed?
- Which current concepts are legacy and no longer used?

---

# 28. Suggested Documentation Deliverables After Code Review

After inspecting the code, this document should be expanded or divided into a small documentation set.

Recommended structure:

```text
README.md
docs/
  architecture.md
  mismo-match.md
  detectors-and-signals.md
  signatures.md
  configuration.md
  mismo-trainer.md
  training-data.md
  optimization.md
  islands.md
  data-model.md
  modernization-notes.md
```

The exact split should be based on the size and complexity discovered during implementation review.

---

# 29. Current Documentation Confidence

The following concepts are considered well established:

- Mismo compares patient pairs.
- Mismo-Match and Mismo-Trainer are separate components.
- Mismo does not perform blocking, database lookup, or record merging.
- patient attributes are optional;
- detectors produce values from 0.0 to 1.0;
- signals combine lower-level evidence;
- network structure is fixed;
- signal weights are configurable;
- Match, Not-a-Match, and Twin networks contribute to the final classification;
- Mismo returns Match, Possible Match, or Not a Match;
- matching is intended to be symmetric and deterministic;
- signatures are a core output;
- Mismo-Trainer uses expert-labeled patient pairs;
- candidate weight sets are scored against training expectations;
- an evolutionary algorithm searches for improved configurations;
- local island processes can perform optimization and share progress through the central Trainer;
- spreadsheet workflows currently supplement the application;
- the future Trainer should own the complete workflow;
- InteropHub SSO and AIRA visual standards are current modernization requirements.

The following should be treated as provisional until verified in code:

- exact class/API names;
- precise detector list;
- precise signal list;
- mathematical signal aggregation;
- min/max weight behavior;
- final classification thresholds;
- signature format;
- database schema;
- weight-set versioning;
- test-set versioning;
- steering-weight scoring rules;
- evolutionary algorithm parameters;
- island communication protocol;
- current import/export formats;
- current authentication implementation.

---

# 30. Documentation Principle for the Next Phase

The code-review phase should **not** assume that every existing implementation detail represents the intended design.

For each major behavior, the reviewer should distinguish among:

1. **Conceptual requirement** — behavior that is intentional and should be preserved.
2. **Current implementation** — how the software does it today.
3. **Legacy implementation** — behavior that exists because of historical constraints.
4. **Modernization need** — behavior that should be regularized or redesigned.

This distinction will be particularly important in Mismo-Trainer, where the current application and spreadsheet-based workflows together represent the real operating process.
