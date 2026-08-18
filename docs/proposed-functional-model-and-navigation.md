# Mismo Trainer — Proposed Functional Model and Navigation

**Status:** Proposed conceptual model  
**Purpose:** Define the analyst-facing functions, concepts, workflows, and navigation structure needed for the next version of Mismo Trainer.  
**Scope:** Functional organization and conceptual requirements only. This document is intended to provide the basis for later database-schema design and user-interface design. It does **not** prescribe database tables, fields, API structures, or detailed screen layouts.

---

## 1. Purpose of Mismo Trainer

Mismo Trainer is the analyst environment for developing, reviewing, testing, and improving Mismo patient-matching behavior.

The application should support a continuous analyst workflow:

> **Curate evidence → establish expectations → evaluate a configuration → investigate results → understand signatures → create better evidence → improve the configuration → repeat**

The system therefore needs to manage two closely related forms of knowledge:

1. **Test cases and test sets** describing what analysts expect Mismo to do.
2. **Configurations and signatures** describing what Mismo actually sees and how it behaves.

Optimization connects those two sides by searching for configurations that better reproduce the expectations represented in curated test sets.

---

# 2. Primary Analyst Concepts

## 2.1 Test Set

A **Test Set** is a named, versioned collection of patient-pair test cases.

A Test Set represents a specific body of evidence against which one or more Mismo configurations can be evaluated.

Important characteristics:

- A Test Set owns its test cases.
- Test cases are not shared between Test Sets.
- Copying a Test Set creates a **deep copy** of its cases.
- A copied Test Set can evolve independently from its source.
- Provenance should identify where a copied case or set originated without creating a live dependency between copies.
- Test Sets should support version identifiers such as `v1.2` or `v1.3`.

### Proposed Test Set lifecycle

**Draft ⇄ Reviewed ⇄ Approved**

These states are intentionally reversible.

**Draft**
- Active curation is expected.
- Cases can be added, removed, edited, reviewed, or reclassified.

**Reviewed**
- The set has undergone deliberate analyst review.
- It may still be changed if additional work is needed.
- Returning to Draft should be permitted.

**Approved**
- Represents a stable reference version.
- Changes should be prohibited while the set remains Approved.
- The set may be moved back to an earlier state if deliberate changes are required.
- In normal use, analysts should usually create a new deep-copy version before making substantial changes to an approved reference set.

This allows statements such as:

> Test Set v1.2 is Approved.  
> Test Set v1.3 is Draft and contains the next round of changes.

## 2.2 Test Case

A **Test Case** represents a pair of patient records together with the analyst's current expectation about how Mismo should classify the pair.

The expected classifications are:

- **Match**
- **Possible Match**
- **Not a Match**
- **Not Sure**

`Not Sure` is a valid expert judgment, but it means the reviewer is not confident enough to establish a testable expectation.

Therefore:

> **A Test Case whose current expectation is `Not Sure` may be reviewed and analyzed, but it is excluded from scoring when evaluating a configuration.**

A Test Case should also support:

- descriptive information;
- source/scenario information;
- analyst notes;
- review status;
- a flag for additional review;
- current expectation;
- review/change history;
- provenance.

## 2.3 Test Case Provenance

Every Test Case should retain information describing how it entered the Test Set.

Useful provenance categories include:

- **Manual** — created directly by an analyst.
- **Imported** — loaded from an external test-case file or other supported format.
- **Generated** — created by Mismo's synthetic test-case generation capabilities.
- **Signature Generated** — generated to reproduce a selected Mismo signature.
- **Copied from Test Set** — created as part of a deep-copy operation.

Additional provenance information may identify:

- the source Test Set and version;
- the originating signature;
- an import source;
- a generator/scenario;
- the date of creation;
- the user or process responsible.

Provenance is historical information. It should not create a dependency in which modifying the source also modifies the copied Test Case.

## 2.4 Test Case Review

Test Case Review replaces and expands the workflow currently performed in the Excel review workbook.

An analyst should be able to view Patient A and Patient B side-by-side and quickly understand:

- values that are the same;
- values that are different;
- values present only on Patient A;
- values present only on Patient B;
- missing values.

The system should present the relevant patient fields dynamically rather than limiting the comparison to a fixed small set of demographic fields.

The analyst can assign:

- Match;
- Possible Match;
- Not a Match;
- Not Sure.

The analyst can also:

- add or edit notes;
- flag the case for further review;
- move to the next or previous case;
- navigate within a filtered review queue;
- return to previously reviewed cases;
- change the current expectation.

The current expectation is the value used by Mismo evaluations.

## 2.5 Review History

Mismo currently operates primarily with a single active reviewer judgment, but the application should preserve previous judgments rather than overwriting history.

Operationally:

> **The most recent accepted review becomes the current expectation.**

History may contain entries such as:

```text
Reviewer A — Possible Match
Reviewer B — Not Sure
Reviewer A — Match
```

The current Test Case expectation would be `Match`, while the earlier opinions remain available for context.

This is not intended to require a formal multi-reviewer adjudication process. It simply preserves analyst history and provides traceability.

## 2.6 Configuration

A **Configuration** defines how the Mismo matcher interprets the evidence produced by its detector/signal network.

The application should allow analysts to:

- view available configurations;
- load or import configurations;
- inspect configuration details;
- identify the configuration associated with a signature;
- select configurations for evaluation;
- compare configurations;
- access candidate configurations produced through optimization.

A convenient "current" or "selected" configuration may be useful for interactive tools, but evaluations should explicitly record the exact configuration they used.

## 2.7 Evaluation

An **Evaluation** is the execution of one configuration against one Test Set.

Conceptually:

> **Evaluation = Configuration × Test Set**

The evaluation determines how the configuration performs against the expectations contained in the Test Set.

An Evaluation should include:

- total number of cases;
- number of scorable cases;
- number of `Not Sure` / unscored cases;
- expected classifications;
- calculated classifications;
- agreement/disagreement counts;
- confusion matrix;
- overall scoring information;
- individual cases that disagree with their expectations;
- signature-group analysis.

A disagreement should be treated as:

> **Evidence that requires investigation, not automatically evidence that the matcher is wrong.**

The analyst may determine that:

- the Configuration should change; or
- the Test Case expectation should change.

The analyst should be able to open the ordinary Test Case Review experience directly from Evaluation results and modify the Test Case if the Test Set is editable.

## 2.8 Signature

A **Signature** is a distilled representation of what Mismo's detector structure observed when comparing a patient pair.

A signature:

- represents matching characteristics rather than patient identity data;
- is tied to the detector/network structure that generated it;
- may identify or imply the compatible configuration structure;
- can be analyzed without possessing the original patient pair.

Signatures connect production matching behavior back to configuration development and Test Set curation.

## 2.9 Optimization

**Optimization** is the process of searching for improved Mismo configurations using curated Test Sets.

The existing Island and Central architecture supports this process.

From an analyst perspective, the important concepts are:

- optimization runs;
- Islands contributing candidate configurations;
- current/best candidate configurations;
- configuration scores;
- promotion of candidate configurations into normal Configuration analysis.

The **Central Service** remains an important system component but should be presented as infrastructure supporting Optimization rather than as one of the primary analyst concepts.

---

# 3. Primary Navigation

The application should use five primary analyst-facing navigation areas in the top navigation bar:

1. **Test Sets**
2. **Configurations**
3. **Evaluations**
4. **Signatures**
5. **Optimization**

A Home/Dashboard option may also be used if useful, while remaining within the available top-level navigation capacity.

The top-level navigation identifies the analyst's major area of work.

Detailed navigation, actions, editing functions, and context-specific tools should appear in the right-side navigation area.

---

# 4. Test Sets

## 4.1 Purpose

The Test Sets area answers:

> **What should Mismo do?**

It is where analysts curate the body of cases used to describe expected matching behavior.

## 4.2 Test Set Management

Analysts should be able to:

- view existing Test Sets;
- create a new Test Set;
- create a new version by deep-copying another Test Set;
- rename or describe a Test Set;
- assign or change its version;
- change lifecycle status;
- inspect Test Set provenance/history;
- remove or archive Test Sets as appropriate.

Approved Test Sets should not permit case-level modification until their lifecycle state is deliberately changed.

## 4.3 Case List

Within a Test Set, the analyst should have access to the complete list of Test Cases.

Useful filtering/navigation categories include:

- All Cases
- Unreviewed
- Reviewed
- Not Sure
- Flagged for Review
- Match
- Possible Match
- Not a Match
- Manually Created
- Imported
- Generated
- Signature Generated

Search and filtering should allow analysts to locate cases by relevant identifiers, labels, scenarios, provenance, or other metadata.

## 4.4 Add Cases

A Test Set should support several ways of adding Test Cases.

### Create Manually

The analyst creates Patient A and Patient B and assigns an initial expectation.

### Import

The analyst imports one or many cases from a supported external representation.

Imported expectations may be accepted as provided. Human review is not mandatory before those cases are used.

### Generate Synthetic Cases

The application may use Mismo's existing synthetic patient/test-case generation functionality to produce test cases.

Generated cases may arrive with expectations based on the generation scenario and may be immediately usable unless the analyst chooses to review them.

### Generate from Signatures

Future functionality should allow analysts to select one or more signatures and generate synthetic patient pairs that reproduce those patterns.

Generated examples can then be added to the selected Test Set.

---

# 5. Test Case Review Workflow

## 5.1 Review Queue

The application should support efficient sequential review similar to, and better than, the current Excel workflow.

An analyst should be able to define a working queue such as:

- Unreviewed;
- Not Sure;
- Flagged for Review;
- Cases of a selected expectation;
- Cases discovered through an Evaluation;
- Cases within a selected signature group.

Once the queue is selected, **Previous** and **Next** should operate within that queue rather than simply moving by raw database order.

## 5.2 Patient Comparison

The Test Case Review experience should prominently display Patient A and Patient B side-by-side.

The interface should visually distinguish:

- same values;
- different values;
- Patient-A-only values;
- Patient-B-only values;
- missing values.

The primary comparison should include the relevant fields that actually exist in either record rather than reproducing the Excel workbook's fixed-field limitation.

## 5.3 Review Actions

The analyst should be able to select:

- Match;
- Possible Match;
- Not a Match;
- Not Sure.

Additional actions include:

- add/edit notes;
- flag for further review;
- save changes;
- move to the next case;
- move to the previous case;
- jump to a selected case;
- view history;
- view provenance.

The authenticated user should provide reviewer identity automatically.

---

# 6. Configurations

## 6.1 Purpose

The Configurations area answers:

> **How is Mismo configured to make matching decisions?**

## 6.2 Functions

Analysts should be able to:

- browse configurations;
- select a working configuration;
- load/import a configuration;
- inspect configuration details;
- see configuration identifiers/signature hashes;
- understand compatibility with signatures;
- access configurations produced by Optimization;
- select configurations for Evaluation;
- select configurations for Signature inspection.

Future lifecycle/version concepts for configurations may be useful, but are not defined by this document.

---

# 7. Evaluations

## 7.1 Purpose

The Evaluations area answers:

> **How well does this configuration reproduce the expectations in this Test Set?**

The existing Mismo "Review" functionality should conceptually move here.

The term **Review** should primarily refer to human Test Case Review, while **Evaluation** refers to executing a matcher configuration against a Test Set.

## 7.2 Start an Evaluation

The analyst selects:

- a Test Set;
- a Configuration.

The system executes the complete Test Set against the selected Configuration.

Cases whose expectation is `Not Sure` are retained in the results but excluded from scoring.

## 7.3 Evaluation Summary

The Evaluation should provide a high-level overview including:

- Test Set and version;
- Configuration;
- total cases;
- scorable cases;
- unscored `Not Sure` cases;
- cases matching expectation;
- cases disagreeing with expectation;
- confusion matrix;
- overall score or other established Mismo metrics.

## 7.4 Failure / Disagreement Review

The analyst should be able to focus on cases where:

```text
Expected classification ≠ Calculated classification
```

From this list the analyst can open the Test Case and investigate the disagreement.

Possible outcomes include:

1. The Test Case expectation is correct and the Configuration is deficient.
2. The calculated result reveals that the Test Case expectation should be changed.
3. The case is ambiguous and should become `Not Sure`.
4. The case needs additional review.

Where the Test Set is editable, the analyst may change the Test Case directly and then rerun the Evaluation.

## 7.5 Signature Group Analysis

Evaluation results should support grouping Test Cases by signature.

For each signature group, useful information includes:

- signature;
- number of cases;
- calculated classification;
- expected-classification distribution;
- whether all Test Cases share the same expectation;
- cases where expectations conflict.

A signature group containing different expectations is particularly important because the matcher sees those cases as having the same detector pattern while the Test Set expects different outcomes.

Analysts should be able to drill from the group into individual Test Cases.

---

# 8. Configuration Comparison

Configuration comparison should be part of the Evaluations area.

The analyst selects:

- one Test Set;
- Configuration A;
- Configuration B.

The system should compare their behavior on the same Test Set.

Useful results include:

- overall score difference;
- confusion-matrix differences;
- cases improved by Configuration B;
- cases regressed by Configuration B;
- unchanged cases;
- cases whose calculated classification changed;
- affected signature groups.

Particularly useful analyst questions include:

> Which failures did the candidate configuration fix?

and:

> Which previously correct cases became incorrect?

Configuration comparison should therefore emphasize case-level and pattern-level changes, not only overall numeric scores.

---

# 9. Signatures

## 9.1 Purpose

The Signatures area answers:

> **What matching patterns is Mismo seeing, what do they mean, and how can they inform future Test Sets and configurations?**

Signature functionality should support both individual diagnostic use and analysis of production-derived signature collections.

## 9.2 Signature Inspector

The analyst should be able to enter or select a single signature.

The application should:

- select or identify a compatible Configuration;
- decode the signature;
- show detector/signal scoring details;
- show how the Configuration interprets the signature;
- show the resulting classification.

The default presentation should emphasize the information most useful to analysts, particularly non-zero values in important Match and Missing portions of the network.

Full details may remain available for deeper investigation.

## 9.3 Batch Signature Analysis

Analysts should be able to upload a collection of signatures.

The input may include frequency/count information.

Conceptually:

```text
Signature, Count
Signature-A, 18492
Signature-B, 7210
Signature-C, 352
```

The application should support:

- configuration compatibility;
- decoded scoring results;
- resulting classifications;
- signature frequency;
- sorting and filtering;
- grouping and summarization;
- export to a delimited file.

Frequency is important because it allows analysts to prioritize patterns that occur commonly in production.

## 9.4 Signature-to-Example Generation

Future functionality should allow the analyst to generate synthetic Patient A / Patient B pairs that produce a selected signature.

The basic workflow is:

1. Select a signature.
2. Generate one plausible synthetic patient pair.
3. Inspect the pair.
4. Generate another if desired.
5. Edit or review the generated case.
6. Add the case to a Test Set.

The analyst should also be able to request multiple generated examples from the same signature.

This supports scenarios where a production signature represents behavior the analyst believes Mismo handles incorrectly.

The analyst can create a family of synthetic Test Cases having similar matching characteristics without accessing or retaining the production patient data that originally produced the signature.

---

# 10. Optimization

## 10.1 Purpose

The Optimization area answers:

> **Can Mismo find a configuration that performs better against the curated evidence?**

## 10.2 Analyst Functions

Optimization should provide analyst-facing access to:

- optimization runs;
- Islands;
- run status;
- candidate configurations;
- most recent/best configurations;
- candidate scores;
- configuration details;
- actions to evaluate or retain a candidate configuration.

The Central Service supports these functions by coordinating Island results and retaining the latest generated weight information.

## 10.3 Relationship to Other Areas

Optimization should connect naturally with Configurations and Evaluations.

Conceptually:

```text
Approved or working Test Set
        ↓
Optimization
        ↓
Candidate Configuration
        ↓
Evaluation
        ↓
Configuration comparison
        ↓
Accept, reject, or continue optimization
```

The Central Service itself is primarily technical infrastructure and does not need to define the analyst's primary navigation model.

---

# 11. End-to-End Analyst Workflows

## 11.1 Build a New Test Set

```text
Create or copy Test Set
        ↓
Add manual/imported/generated cases
        ↓
Review selected cases
        ↓
Resolve Not Sure / flagged cases as appropriate
        ↓
Mark Reviewed
        ↓
Mark Approved
```

Not every imported or generated case must receive human review before being used.

## 11.2 Improve an Existing Test Set

```text
Approved Test Set v1.2
        ↓
Deep copy
        ↓
Create v1.3 Draft
        ↓
Add or modify cases
        ↓
Review
        ↓
Evaluate
        ↓
Approve v1.3
```

Cases in the new Test Set have independent histories after the copy.

## 11.3 Investigate Matcher Failures

```text
Run Evaluation
        ↓
Open failures/disagreements
        ↓
Review patient pair
        ↓
Decide:
    Configuration is wrong
    OR expectation is wrong
    OR case is Not Sure
        ↓
Update evidence and/or configuration
        ↓
Rerun Evaluation
```

## 11.4 Compare Candidate Configurations

```text
Select Test Set
        ↓
Select current and candidate Configurations
        ↓
Compare Evaluations
        ↓
Inspect improvements
        ↓
Inspect regressions
        ↓
Decide whether candidate is better
```

## 11.5 Learn from Production Signatures

```text
Collect production signatures + counts
        ↓
Upload to Signature Analysis
        ↓
Identify common/problematic patterns
        ↓
Inspect selected signature
        ↓
Generate synthetic example pair
        ↓
Generate additional examples if useful
        ↓
Add to next Test Set version
        ↓
Assign expectations
        ↓
Optimize / evaluate
```

This creates a privacy-preserving feedback loop from real production matching patterns into future Mismo training behavior.

---

# 12. Navigation Model

## Top Navigation

The recommended top navigation is:

```text
Test Sets | Configurations | Evaluations | Signatures | Optimization
```

Optional:

```text
Home | Test Sets | Configurations | Evaluations | Signatures | Optimization
```

These top-level areas correspond to stable analyst concepts rather than individual implementation pages.

## Right-Side Context Navigation

The right-side navigation should contain functions and destinations relevant to the selected top-level area and current object.

Examples below are conceptual rather than final screen designs.

### Test Sets

- Test Set Details
- Cases
- Review Queue
- Add Case
- Import Cases
- Generate Cases
- Copy / Create New Version
- Lifecycle / Status
- History / Provenance

### Configurations

- Configuration Details
- Load / Import
- Select Configuration
- Evaluate
- Compare
- Signature Compatibility
- Optimization Origin

### Evaluations

- Summary
- Failures / Disagreements
- All Cases
- Signature Groups
- Compare Configurations
- Rerun
- Evaluation Details

### Signatures

- Inspect Signature
- Batch Analysis
- Upload
- Export
- Generate Example
- Add Generated Cases to Test Set

### Optimization

- Runs
- Islands
- Candidate Configurations
- Best / Recent Results
- Central Service Status
- Evaluate Candidate

The right-side navigation should also contain context-appropriate editing actions rather than adding those actions to the top-level navigation.

---

# 13. Conceptual Relationships

The principal conceptual relationships are:

```text
Test Set
    owns many Test Cases

Test Case
    has one current expectation
    has review history
    has provenance

Evaluation
    uses one Test Set
    uses one Configuration
    produces results for Test Cases
    groups results by Signature

Configuration
    evaluates Test Cases
    interprets Signatures
    may be produced by Optimization

Signature
    may come from a Test Case
    may come from production
    may have a frequency/count
    may be used to generate synthetic Test Cases

Optimization
    uses Test Sets
    produces candidate Configurations
```

These relationships should guide later data-model work without implying a specific relational schema.

---

# 14. Key Behavioral Rules

1. **Test Cases belong to one Test Set.** Test Set copying is deep copying, not shared-case reuse.
2. **Test Set versions are independent after copying.**
3. **Approved Test Sets are immutable while Approved.**
4. **Lifecycle states are reversible.**
5. **Each Test Case has one current expectation.**
6. **Previous reviewer opinions are retained in history.**
7. **The most recent accepted review determines the current expectation.**
8. **`Not Sure` is a valid analyst classification but is not scorable.**
9. **Imported/generated cases do not require human review before use.**
10. **A reviewer may change a Test Case expectation before or after Evaluation, provided the Test Set is editable.**
11. **Evaluation disagreements do not automatically mean the matcher is wrong.**
12. **Evaluation must identify the exact Test Set and Configuration used.**
13. **Configuration comparison should expose improvements and regressions at the Test Case level.**
14. **Signatures are dependent on the matcher detector/network structure that generated them.**
15. **Production signature frequency is meaningful analyst information.**
16. **Signature-generated examples are synthetic Test Cases, not recovered production patient records.**
17. **One signature may be used to generate multiple distinct example patient pairs.**
18. **Central Service is Optimization infrastructure rather than the primary analyst mental model.**

---

# 15. Product Mental Model

| Area | Analyst Question |
|---|---|
| **Test Sets** | What should the matcher do? |
| **Configurations** | How is the matcher configured? |
| **Evaluations** | How well does this configuration do what we expect? |
| **Signatures** | What matching patterns are we seeing and what do they mean? |
| **Optimization** | Can we find a better configuration? |

Together they form a single improvement loop:

```text
                 ┌─────────────────┐
                 │    Test Sets    │
                 │ Expected behavior
                 └────────┬────────┘
                          │
                          ▼
┌────────────────┐   ┌────────────────┐
│ Configurations │──▶│  Evaluations   │
│ Matcher rules  │   │ Actual vs expected
└───────▲────────┘   └───────┬────────┘
        │                    │
        │                    ▼
┌───────┴────────┐    Failures / patterns
│  Optimization │            │
│ Better weights│            ▼
└────────────────┘    ┌────────────────┐
                      │   Signatures   │
                      │ Pattern insight│
                      └───────┬────────┘
                              │
                              ▼
                       Synthetic examples
                              │
                              └──────▶ Test Sets
```

This should be the central conceptual model used when designing the next Mismo Trainer database and user experience.
::: ​​