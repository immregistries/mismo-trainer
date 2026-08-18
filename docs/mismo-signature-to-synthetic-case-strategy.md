# Mismo Trainer — Signature-to-Synthetic-Test-Case Generation Strategy

**Status:** Conceptual design note  
**Purpose:** Define a general strategy for generating synthetic patient-pair examples from a Mismo match signature.  
**Scope:** High-level algorithmic approach only. This document does not define the final solver implementation, exact scoring function, mutation rules, or user-interface design.

---

## 1. Goal

Mismo signatures describe the matching characteristics that the Mismo detector network observed when comparing two patient records.

The proposed capability is:

> **Given a Mismo signature, generate one or more synthetic Patient A / Patient B pairs that produce that same signature.**

The purpose is not to reconstruct the original production patient records. Instead, the goal is to create a plausible synthetic example that exhibits the same matching characteristics.

This supports a feedback loop such as:

```text
Production signature
        ↓
Pattern identified as important/problematic
        ↓
Generate synthetic example
        ↓
Expert reviews expected result
        ↓
Add example(s) to a Test Set
        ↓
Evaluate / optimize Mismo
```

This allows production matching behavior to inform future test and training data without exposing the source patient data.

---

## 2. This Is an Inverse Matching Problem

Normal Mismo operation is a forward calculation:

```text
Patient A + Patient B
        ↓
Detectors and signals
        ↓
Signature
```

The proposed generator attempts the reverse:

```text
Target Signature
        ↓
Find Patient A + Patient B
        ↓
Mismo generates same Signature
```

This is not a deterministic inverse function.

A signature describes characteristics of a patient pair, not the original values that produced those characteristics. For example, a signature may imply:

```text
First names exact match
Last names different
Middle names similar but not exact
DOB exact match
Phone missing
```

Many different synthetic patient pairs could satisfy those conditions.

Therefore the desired output is:

> **Any plausible synthetic pair that satisfies the target signature.**

Multiple valid solutions may exist.

---

## 3. Important Practical Constraint

The current Mismo measurement configuration considers only a relatively small subset of patient fields.

At present, approximately seven core fields account for most of the matching behavior that needs to be reproduced.

This substantially reduces the difficulty of the problem.

Although the complete Mismo detector architecture can contain dependencies among detectors and signals, the current signature-generation use case has a limited number of interacting inputs.

The first implementation should therefore be designed specifically for the matcher structure and field set currently in use rather than attempting to create a completely generic solver for every possible future Mismo detector.

This suggests an intentionally pragmatic architecture:

> **Solver behavior should understand the current detector network and work closely with the existing Mismo synthetic-data generation logic.**

A more general framework can be developed later if the number or complexity of detectors increases.

---

## 4. The Signature as a Set of Constraints

The signature should be decoded into the detector/signal values it represents.

Conceptually:

```text
Target Signature
        ↓
Decode
        ↓
Target detector/signal pattern
```

The resulting target may look conceptually like:

| Detector / Signal | Target |
|---|---:|
| First-name exact | 1.0 |
| First-name similar | 0.0 |
| Middle-name exact | 0.0 |
| Middle-name similarity | ~0.8 |
| Last-name exact | 0.0 |
| DOB exact | 1.0 |
| Phone missing | 1.0 |

These target values become constraints that the generated patient pair needs to satisfy.

Some constraints are straightforward. For example:

```text
First-name exact = true
```

can generally be satisfied by assigning the same first name to Patient A and Patient B.

Other constraints require finding suitable values. For example:

```text
Middle-name exact = false
Middle-name Jaro-Winkler similarity ≈ target range
```

requires two different values whose detector score falls into the required signature bucket.

The solver should therefore distinguish between:

- **direct constraints**, which can be constructed deliberately;
- **search constraints**, which require trying candidate values;
- **dependent constraints**, where changing a field may affect several detector or signal values.

---

## 5. Use Mismo as the Validation Oracle

The generator should not attempt to independently reproduce the entire Mismo matching algorithm.

Instead, Mismo itself should evaluate every candidate patient pair.

For each proposed pair:

```text
Candidate Patient A + Patient B
              ↓
             Mismo
              ↓
      Candidate Signature
              ↓
Compare with Target Signature
```

This has several advantages:

- the generator cannot silently diverge from the real matcher;
- detector interactions do not have to be perfectly duplicated in generator code;
- new or revised detector logic remains visible through actual Mismo output;
- the final success criterion is unambiguous.

The strongest completion condition is:

```text
generatedSignature == targetSignature
```

Mismo therefore acts as the authoritative validation mechanism.

---

## 6. Integrate with Existing Synthetic-Data Generation

Mismo Trainer already contains logic for producing synthetic patients and introducing realistic data-quality conditions.

That machinery should form the foundation of signature-to-example generation.

The solver should not generate arbitrary strings purely to satisfy mathematical detector conditions.

Instead, it should use synthetic-data operations such as:

- generate plausible names;
- generate plausible DOBs;
- generate plausible addresses;
- generate plausible phone numbers;
- copy values from one patient to another;
- remove values;
- introduce realistic typos;
- transpose characters or digits;
- change addresses;
- create similar names;
- create initials;
- share or alter identifiers.

The target signature should guide **which transformations are applied**.

Conceptually:

```text
Synthetic Patient Generator
           +
Signature Constraints
           +
Targeted Transformations
           ↓
Candidate Patient Pair
```

This increases the likelihood that generated examples remain recognizable as realistic patient-data scenarios rather than artificial strings created solely to satisfy detector math.

---

## 7. Recommended Hybrid Solver Strategy

The likely best approach is neither purely deterministic nor purely random.

Instead, use a **constraint-guided stochastic search**.

The basic strategy is:

1. Decode the target signature.
2. Identify the patient fields involved.
3. Construct as many required relationships as possible directly.
4. Generate an initial realistic patient pair.
5. Run the pair through Mismo.
6. Compare the resulting signature to the target.
7. Identify which constraints are still incorrect.
8. Apply targeted mutations to fields associated with those constraints.
9. Re-evaluate.
10. Repeat until the exact signature is produced or a defined search limit is reached.

Conceptually:

```text
Target Signature
       ↓
Decode constraints
       ↓
Generate plausible starting pair
       ↓
Apply obvious relationships
       ↓
Run through Mismo
       ↓
Compare signatures
       ↓
   Exact match? ─────────────── Yes ──→ Return pair
       │
       No
       ↓
Identify mismatched constraints
       ↓
Targeted mutations
       ↓
Try again
```

---

## 8. Direct Construction First

Many detector relationships should be satisfied deliberately before search begins.

Examples:

| Desired relationship | Possible construction |
|---|---|
| Exact match | Copy A value to B |
| Different | Generate unrelated B value |
| Missing | Remove value from A or B |
| Same initial | Generate compatible full-name / initial combination |
| DOB exact | Copy DOB |
| Identifier overlap | Give patients a shared identifier |
| Identifier conflict | Generate different identifiers |

These constraints should not be left to random chance.

The initial candidate should already satisfy as much of the target signature as is straightforward to construct.

This reduces the search space significantly.

---

## 9. Search Where Necessary

Some detector outputs cannot be conveniently inverted.

String-similarity detectors are a good example.

Suppose the target requires:

```text
exact match = false
Jaro-Winkler similarity = target bucket
```

The solver could:

1. choose a plausible base name;
2. generate realistic variants;
3. calculate the resulting detector score through Mismo;
4. retain variants that move toward the target.

Possible candidate transformations might include:

- single-letter substitution;
- character omission;
- transposition;
- keyboard-adjacent typo;
- alternate spelling;
- shortened form;
- prefix/suffix modification.

Rather than calculate what string is mathematically guaranteed to produce a particular Jaro-Winkler score, the generator can search among realistic variants until it finds one that lands in the required signature range.

---

## 10. Target Mutations to the Problem

Mutation should not be uniformly random across all patient fields.

After each attempt, the solver should determine which portions of the signature remain incorrect.

If:

```text
Middle-name similarity is wrong
DOB is already correct
Phone missing state is already correct
```

then the next search iteration should strongly favor changes to the middle-name fields and avoid changing DOB or phone.

Conceptually:

```text
Current candidate
        ↓
Compare to target
        ↓
Identify mismatched detector nodes
        ↓
Map nodes to affected patient fields
        ↓
Mutate those fields
```

This requires the solver to know which patient fields feed the detector nodes in the current matching structure.

Because the current matcher uses a limited field set, this mapping can initially be explicit and purpose-built.

---

## 11. Preserve Correct Relationships Where Possible

Once part of the candidate matches the target signature, the solver should generally protect it.

For example:

```text
DOB exact            correct
First-name exact     correct
Phone missing        correct
Middle-name score    incorrect
```

The solver should avoid altering DOB, first name, or phone while working on middle name.

This creates a progressively constrained search.

However, fields may affect more than one detector.

Therefore the solver should retain the ability to reconsider previously correct fields if it reaches a dead end.

This is one reason a purely greedy step-by-step algorithm may not always succeed.

---

## 12. Maintain Multiple Candidate Solutions if Needed

A useful improvement over a single candidate is to maintain a small population of promising candidates.

For example:

```text
20 candidate patient pairs
        ↓
Generate several targeted mutations of each
        ↓
Evaluate all candidates
        ↓
Keep the best 20
        ↓
Repeat
```

This resembles a small beam search or evolutionary search.

It prevents the solver from becoming trapped because of one earlier decision.

The search population can remain small because:

- the current field set is limited;
- many relationships can be constructed directly;
- mutations can be targeted;
- the goal is to find one valid solution rather than globally optimize a large mathematical space.

A first implementation may prove that even a much simpler search is sufficient.

---

## 13. Measuring How Close a Candidate Is

The solver needs a way to rank candidates before an exact signature is found.

A conceptual distance function might compare decoded target and candidate detector values.

For example:

```text
distance =
    sum of differences between
    target detector values and candidate detector values
```

The comparison can be weighted.

Potential rules include:

- large penalty for an incorrect exact-match detector;
- large penalty for incorrect missing/not-missing state;
- smaller penalty for a similarity detector being one bucket away;
- larger penalty for being many similarity buckets away.

The exact distance function should be designed later based on the actual detector set.

The signature's progressively detailed structure may also allow the search to prioritize coarse agreement first and finer agreement second.

---

## 14. Solve by Logical Field Groups

Because dependencies are limited, the solver may benefit from considering logical field groups separately.

Possible groups include:

```text
Names
DOB
Address
Phone
Identifiers
```

A likely process is:

1. construct or search for a solution within a field group;
2. combine the partial solutions;
3. evaluate the complete patient pair;
4. resolve remaining cross-group or aggregate differences.

For example:

```text
Name constraints
     ↓
Candidate name pair

DOB constraints
     ↓
Candidate DOB pair

Address constraints
     ↓
Candidate address pair

Combine
     ↓
Run Mismo
     ↓
Fine-tune
```

This is especially attractive for the current Mismo use case because the number of active fields and cross-field dependencies is relatively small.

---

## 15. Solver Logic May Be Matcher-Specific

The first version of this capability should not be over-generalized.

The solver can explicitly understand:

- the current set of enabled fields;
- the current detector types;
- which detectors depend on which fields;
- known interactions among detectors;
- useful synthetic transformations for each field;
- the signature representation used by the current matcher.

This means the implementation may include logic such as:

```text
If target requires:
    first-name exact
then:
    copy generated first name from Patient A to Patient B
```

or:

```text
If target requires:
    middle-name non-exact similarity in bucket X
then:
    generate/mutate middle-name variants until Mismo reports bucket X
```

This is acceptable.

The feature is intended to reproduce patterns from a known matcher, not solve arbitrary inverse machine-learning problems.

If the detector network changes significantly in the future, corresponding solver behavior may also need to change.

---

## 16. Configuration and Signature Compatibility

A signature only makes sense in the context of the detector/network structure that generated it.

The solver therefore needs to know which matching structure/configuration is compatible with the signature.

Conceptually:

```text
Signature
    ↓
Identify compatible matcher/configuration structure
    ↓
Decode target
    ↓
Generate candidate using that structure
```

The generator must always validate the resulting pair using the same compatible matcher structure.

A generated pair should not be claimed to reproduce the signature merely because it looks conceptually similar.

The final generated signature must verify the result.

---

## 17. Exact and Best-Effort Results

The normal successful result should be:

> **Exact synthetic example found.**

Meaning:

```text
generated signature == target signature
```

The system may also benefit from representing a best-effort result when an exact solution cannot be found within reasonable search limits.

For example:

> No exact example was found. This candidate matches all but two target detector values.

Such a result could help diagnose:

- incompatible configuration versions;
- unsupported signature structures;
- solver limitations;
- particularly difficult detector combinations;
- potentially unreachable detector combinations.

A best-effort candidate should never be presented as an exact reproduction.

---

## 18. Generating Multiple Examples

A signature generally represents a class of possible patient pairs rather than one unique pair.

Once the solver can generate one valid example, it should be possible to generate additional examples by:

- starting with different synthetic base patients;
- using different valid mutations;
- explicitly avoiding duplicates;
- rerunning the solver with new random seeds.

This enables two use cases.

### Inspect One Example

The analyst wants to understand:

> What might a pair with this signature look like?

### Generate a Test Family

The analyst determines that the signature represents a pattern Mismo should learn differently and requests:

> Generate 10 or 50 distinct synthetic examples with this signature.

Those cases can then be reviewed and added to a future Test Set.

---

## 19. Relationship to Test-Case Curation

Signature-generated examples should become ordinary Test Cases once added to a Test Set.

Their provenance should indicate:

```text
Source: Signature Generated
Source Signature: <signature>
Generator/version: <generator information>
```

The generated pair does not automatically determine the expert expectation.

The workflow remains:

```text
Generate from signature
        ↓
Synthetic Patient A / Patient B
        ↓
Analyst reviews pair
        ↓
Assign Match / Possible Match / Not a Match / Not Sure
        ↓
Add/use in Test Set
```

This preserves the distinction between:

- what Mismo's detectors observe; and
- what an expert believes the correct matching outcome should be.

---

## 20. Recommended Initial Implementation Philosophy

The initial implementation should favor a **small, understandable solver** over a general optimization framework.

A reasonable progression is:

### Phase 1 — Direct Construction

Support signatures whose detector relationships can be constructed with straightforward synthetic-data transformations.

### Phase 2 — Targeted Local Search

Add search for similarity-based values and other detectors that cannot be directly inverted.

### Phase 3 — Multi-Candidate Search

If needed, maintain several candidate pairs simultaneously to escape local search dead ends.

### Phase 4 — Broader Detector Support

Expand solver knowledge when additional Mismo fields or detector structures become relevant.

The current limited field set makes it practical to learn from the actual signatures encountered and add solver sophistication only where needed.

---

## 21. Core Design Principles

1. **Do not attempt to recover original patient data.**
2. **Treat the signature as a set of constraints, not a serialized patient pair.**
3. **Use Mismo itself as the authoritative validator.**
4. **Reuse the existing realistic synthetic-patient generator.**
5. **Construct obvious detector relationships directly.**
6. **Use search only where direct construction is difficult.**
7. **Target mutations at detector constraints that are currently wrong.**
8. **Preserve already-correct relationships where practical.**
9. **Allow controlled randomness because many valid solutions may exist.**
10. **Keep solver behavior specific to the current matcher when that simplifies the problem.**
11. **Verify exact signature equality before claiming success.**
12. **Support generating multiple distinct examples from one signature.**
13. **Record signature provenance when generated examples become Test Cases.**
14. **Keep expert expectation separate from the signature Mismo generated.**

---

## 22. Conceptual Summary

The proposed approach is best described as:

> **A constraint-guided synthetic-data search in which Mismo itself evaluates candidate patient pairs until one reproduces the desired signature.**

The generator does not need a complete mathematical inverse of Mismo.

Instead:

```text
Understand what the signature requires
        ↓
Use synthetic-data logic to construct a plausible candidate
        ↓
Ask Mismo what signature it actually produces
        ↓
Adjust the patient pair based on the differences
        ↓
Repeat until the target is reproduced
```

Because the current matcher uses a relatively small number of fields with limited dependencies, the first implementation can be deliberately tailored to the detector relationships currently in use.

This should make signature-to-example generation a tractable engineering problem while leaving room to evolve into a more general solver if future Mismo configurations require it.
