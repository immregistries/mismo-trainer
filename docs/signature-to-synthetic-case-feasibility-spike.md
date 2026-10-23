# Signature-to-Synthetic-Case Feasibility Spike

**Status:** Proposed feasibility spike

**Purpose:** Determine whether Mismo can reliably generate plausible synthetic Patient A / Patient B pairs that reproduce signatures observed in production.

**Related design:** [`mismo-signature-to-synthetic-case-strategy.md`](mismo-signature-to-synthetic-case-strategy.md)

## 1. Decision to Make

Before building signature-to-example generation into Mismo Trainer, run a standalone spike that answers:

> Given a production Mismo signature and its compatible configuration, can we generate one or more wholly synthetic patient pairs whose Mismo signature exactly equals the target?

The spike should provide enough evidence to decide whether to:

1. proceed with a product implementation;
2. narrow the supported signatures or detector types; or
3. stop because exact reconstruction is not sufficiently reliable or practical.

This is an inverse matching problem, but it is not an attempt to reconstruct the original patients. The desired result is any independently generated, plausible synthetic pair exhibiting the same match characteristics.

## 2. Why a Spike Is Appropriate

The space of possible patient records is extremely large, so an unguided random search would be impractical. However, a Mismo signature is not an opaque hash. It contains a configuration hash followed by quantized detector results. Each enabled detector score is represented by one of sixteen levels.

The practical problem is therefore smaller than searching the full patient-record space:

- exact-match, missingness, and several date relationships can be constructed directly;
- similarity and address relationships can be searched within individual field groups;
- many signature positions are repeated or complementary observations of the same underlying field relationship;
- every signature actually observed in production had at least one originating pair and is therefore known to have been realizable under its original configuration;
- Mismo itself can validate every synthetic candidate, so the spike does not need to duplicate the matcher.

The main uncertainty is whether realistic synthetic witnesses can be found efficiently for the combined constraints represented by real signatures.

## 3. Scope

### In scope

- A standalone command-line or test-harness implementation.
- One known Mismo configuration at a time.
- Decoding target signatures into their detector-score buckets.
- Generating wholly synthetic candidate pairs.
- Exact validation through the existing Mismo matcher.
- Measuring success rate, search cost, failure modes, and output diversity.
- Producing human-readable reports and sample generated pairs.

### Out of scope

- Mismo Trainer UI work.
- Saving generated cases into a Test Set.
- Database changes.
- Supporting arbitrary future detector implementations.
- Inferring the expected expert classification from the signature.
- Reconstructing or approximating original production values.
- Declaring a partial match to be an exact result.

## 4. Inputs Needed

Obtain from Shelby:

- a representative collection of production signatures;
- frequency/count for each signature, if available;
- the exact configuration script corresponding to each signature hash;
- confirmation of the production period and system from which the sample came;
- no patient values or other source record data.

The preferred initial sample is 100 to 1,000 distinct signatures. It should include:

- the most frequent signatures;
- signatures across Match, Possible Match, and Not a Match classifications;
- common and rare patterns;
- signatures with missing data;
- signatures exercising name similarity, DOB variation, and address comparison;
- more than one configuration only if configuration-specific results can be reported separately.

Production frequency matters. The spike should report both the percentage of distinct signatures solved and the percentage of production volume represented by those signatures.

## 5. Safety and Privacy Boundary

The spike must operate on signatures and configurations only. It must not ingest production patient records.

All generated values must originate independently from synthetic value pools or deterministic test fixtures. Production values must never be used as mutation seeds. Reports should make clear that a generated pair is one possible witness for the signature and does not resemble or reconstruct the source pair.

A signature still reveals abstract characteristics of its source comparison, such as equality, similarity, and missingness. It should therefore be handled according to the project's rules for production-derived artifacts even though it contains no patient values.

## 6. Technical Hypothesis

The most promising approach is field-group witness construction followed by exact validation and targeted repair:

```text
Target signature
      |
      v
Resolve exact configuration
      |
      v
Decode detector buckets
      |
      v
Normalize constraints by field group
      |
      v
Construct/select synthetic witnesses
      |
      v
Compose Patient A / Patient B
      |
      v
Run the existing Mismo matcher
      |
      +-- exact signature --> retain result
      |
      +-- mismatch --------> targeted repair or bounded search
```

The initial solver should be specific to the current configuration and detector set. A generic inverse engine is not required to establish feasibility.

## 7. Spike Stages

### Stage 1: Signature diagnostics

Build a diagnostic representation that maps each signature position to:

- network and detector path;
- detector implementation;
- affected patient field or fields;
- target four-bit score bucket;
- whether the constraint is direct, searchable, dependent, or currently unsupported.

Normalize repeated and complementary detector results into underlying relationships where possible. The output should make contradictions, configuration mismatches, and unsupported detectors visible before generation begins.

Deliverable: a readable decoded report for every sampled signature.

### Stage 2: Direct witness construction

Implement deterministic constructors for inexpensive relationships, including:

- exact equality and inequality;
- present and missing values;
- same initial;
- single-character differences;
- exact DOB;
- supported DOB variations;
- exact or different phone values;
- shared or different identifiers, where enabled.

Start with a plausible synthetic Patient A, then construct Patient B according to the decoded relationships.

Deliverable: exact reconstruction results for signatures requiring only directly supported relationships.

### Stage 3: Field-group witness catalogs

Build small catalogs of plausible synthetic value pairs indexed by the detector buckets they actually produce. Likely catalogs include:

- first, middle, and last names;
- dates of birth;
- addresses;
- phone numbers;
- identifiers if they are enabled in the sampled configuration.

Catalog entries must be scored by the actual Mismo detector code. Similarity values should not be predicted independently and assumed correct.

This changes common search operations into lookups such as:

```text
(name relationship constraints) -> candidate name pair
(address detector bucket)        -> candidate address pair
```

Deliverable: coverage statistics showing which observed field-group constraint combinations have available witnesses.

### Stage 4: Composition and targeted repair

Combine field-group witnesses into complete patient pairs and validate them with Mismo. When the signature differs:

1. identify the mismatched detector positions;
2. map them back to affected fields;
3. preserve field groups that are already correct where practical;
4. mutate or replace only implicated witnesses;
5. re-run Mismo;
6. stop at an explicit attempt or time limit.

If a single candidate repeatedly reaches a dead end, retain a small beam of the best candidates. Do not begin with an unrestricted evolutionary search over whole patient records.

Deliverable: final exact solutions, best partial candidates for diagnosis, and per-signature search statistics.

### Stage 5: Multiple-example generation

For signatures that can be solved, request multiple independently generated pairs by varying:

- base synthetic patients;
- witness choices;
- random seeds;
- supported transformations.

Deduplicate outputs and verify every returned pair independently.

Deliverable: evidence that the feature can produce a family of examples rather than one hard-coded witness.

## 8. Validation Rule

The authoritative success condition is literal equality:

```text
candidatePatientCompare.getSignature().equals(targetSignature)
```

An exact result must also use the configuration identified by the signature's configuration hash.

A near match may be retained in the spike report to explain failures, but it must never be counted or presented as a successful reconstruction.

## 9. Measurements

Record at least the following for each target signature:

- configuration hash;
- production frequency, when supplied;
- decoded detector count and normalized constraint count;
- whether all involved detector types are supported;
- exact solution found or not found;
- number of exact, distinct examples requested and produced;
- elapsed time;
- number of candidate evaluations;
- number and identity of mismatched detector buckets for the best failure;
- failure category;
- generated values' plausibility-review result.

Aggregate results should include:

- exact success rate by distinct signature;
- frequency-weighted production coverage;
- median and high-percentile generation time;
- success rate by classification;
- success rate by detector and field group;
- diversity rate when multiple examples are requested;
- unsupported, invalid, timed-out, and search-exhausted counts.

## 10. Failure Categories

Use explicit categories so failures guide the next decision:

- **Configuration unavailable** — the signature's matcher structure cannot be loaded.
- **Invalid signature** — the signature cannot be decoded for the selected configuration.
- **Unsupported detector** — the spike has no construction/search strategy for an involved detector.
- **Unsupported constraint combination** — individual detectors are supported but their combined relationship is not.
- **No plausible witness found** — arbitrary values might work, but none satisfying the synthetic plausibility rules were found.
- **Search limit reached** — a best candidate exists, but exact equality was not reached within the budget.
- **Suspected unreachable combination** — useful for hand-created or corrupted signatures; this should be rare for signatures genuinely observed in production.
- **Generator defect** — candidate generation or validation behaved incorrectly.

## 11. Proposed Initial Success Criteria

The exact thresholds should be agreed on before running the sample. A reasonable starting decision rule is:

- exact solutions for at least 90% of distinct signatures in the initial supported configuration;
- exact solutions covering at least 95% of frequency-weighted production volume;
- a median generation time suitable for an interactive workflow;
- at least five distinct exact examples for most successfully solved signatures;
- no production values used or required;
- failures concentrated in identifiable detector groups that can reasonably be added in a subsequent iteration.

These are proposed product-readiness signals, not assumptions about the spike's outcome. The report must preserve the observed results even if they fall well below these thresholds.

## 12. Deliverables

The spike should produce:

1. a standalone generator/test harness;
2. automated tests for signature decoding, witness construction, and exact validation;
3. a machine-readable result file for all sampled signatures;
4. a concise feasibility report with aggregate measurements;
5. representative successful synthetic pairs;
6. representative failures with mismatched detectors and likely causes;
7. a recommendation to proceed, narrow scope, or stop;
8. an implementation outline only if the evidence supports moving the capability into Mismo Trainer.

## 13. Decision Guidance

### Proceed

Proceed toward an application feature if common production signatures can be reproduced exactly, quickly, and with diverse plausible values. Product work can then add the paste/select workflow, background generation if necessary, review, provenance, and Test Set integration.

### Narrow scope

Proceed with an explicitly limited feature if failures cluster around uncommon configurations or a few detector types. The UI should state supported configurations and explain unsupported results rather than silently returning partial examples.

### Stop or reconsider

Reconsider the approach if exact success remains low after field-group construction and targeted search, if common signatures require unacceptable computation, or if synthetic plausibility constraints make useful witnesses rare. Partial signature reproduction alone is not enough to complete the proposed production-to-training feedback loop.

## 14. Recommended First Run

After receiving the sample from Shelby:

1. verify that every configuration hash can be resolved;
2. decode all signatures and inventory the active detector and constraint combinations;
3. rank signatures by production frequency;
4. implement the smallest set of field-group witnesses needed for the highest-volume signatures;
5. run exact validation and publish early coverage numbers;
6. add targeted support for the largest failure clusters;
7. make the proceed/narrow/stop decision from the measured results.

This order tests the central proposition quickly while ensuring that engineering effort follows actual production patterns rather than hypothetical detector combinations.
