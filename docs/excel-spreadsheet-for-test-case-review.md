# Excel Test Case Review Workflow

## Purpose

The spreadsheet is an **expert adjudication interface for patient-pair test cases**.

Its primary workflow is:

**Load a test case → display Patient A and Patient B side-by-side → visually identify same/different/missing values → record the expert's classification → record reviewer/date → advance to the next case.**

The expert classification is then used to produce a revised version of the Mismo training/test data in which the `EXPECT:` value reflects the expert's determination.

This is functionality that belongs naturally in Mismo-Trainer. Mismo-Trainer is already conceptually responsible for maintaining labeled patient-pair cases and reviewing expected-versus-actual classifications, and the existing documentation explicitly notes that some of this workflow has historically occurred in Excel.

---

## 1. Workbook Structure

| Sheet | Function |
|---|---|
| **Comparison** | Main interactive expert-review interface and storage for review results |
| **Data** | Source test cases in Mismo's four-line text format and generation of revised test-case data |
| **Original** | Earlier/manual review information, including suggested results and comments |
| **Sheet2** | Auxiliary/legacy list of failed cases; it does not appear to drive the main review workflow |

The **Comparison** and **Data** tabs together contain the core functionality that should be replicated.

---

# 2. Source Test Case Structure

The `Data` sheet contains test cases in repeated four-row blocks:

```text
TEST: <test ID>:<scenario>:<data-quality profile>
EXPECT: <expected classification>
PATIENT A: <comma-separated key=value data>
PATIENT B: <comma-separated key=value data>
```

For example:

```text
TEST: D-1195:SUFFIX_MISSING:GREATB-GOODB
EXPECT: Not a Match
PATIENT A: addressState=MI,...,nameFirst=Lucilius,...
PATIENT B: addressState=MI,...,nameFirst=Lyle,...
```

There are approximately **3,162 source cases** in this workbook.

The spreadsheet therefore treats the test case as a unit consisting of:

```text
Test Case
 ├── Identifier / scenario metadata
 ├── Expected classification
 ├── Patient A
 └── Patient B
```

---

# 3. How the Comparison Tab Selects a Case

`Comparison!A1` is effectively the **current test-case index**.

For example, its current value is:

```text
2390
```

The spreadsheet calculates the corresponding starting row in `Data`:

```excel
(A1 - 1) * 4 + 1
```

For case 2390 this produces source row 9557.

It then retrieves the four source lines into working cells on the Comparison sheet:

```text
TEST line       → A31
EXPECT line     → A35
PATIENT A line  → A36
PATIENT B line  → A60
```

Changing `A1` therefore changes the entire case displayed to the reviewer.

This is the spreadsheet's equivalent of:

```text
currentTestCaseId
```

in an application.

---

# 4. Parsing the Patient Records

The patient records arrive as strings such as:

```text
nameFirst=Lucilius,nameMiddle=Sherman,addressCity=Wales,...
```

The Comparison sheet contains a large working area that parses those strings using `FIND`, `MID`, and `IFERROR`.

It extracts fields including:

- name
- DOB
- address
- phone
- MRN
- Medicaid
- SSN
- guardian names
- mother's maiden name
- gender
- birth information
- shot history

Patient A is parsed roughly in rows 37–59 and Patient B in rows 61–83.

This parsing machinery exists only because Excel is receiving serialized patient records. **It should not be replicated in the application.** Mismo already has structured Patient objects/field maps.

---

# 5. The Main Side-by-Side Comparison

The main reviewer display is approximately:

| Field | Patient A | Patient B |
|---|---|---|
| birthDate | value | value |
| nameFirst | value | value |
| nameMiddle | value | value |
| nameLast | value | value |
| addressStreet1 | value | value |
| addressCity | value | value |
| addressState | value | value |
| addressZip | value | value |
| phone | value | value |

The headings are explicitly **Patient A** and **Patient B**.

For each displayed field, helper formulas calculate:

```text
if either value is blank:
    MISS
else if Patient A == Patient B:
    SAME
else:
    DIFF
```

This is **literal string equality**, not the Mismo matching algorithm or fuzzy matching.

Conditional formatting then emphasizes the result:

- **SAME** → green-style highlighting
- **DIFF** → strong red/yellow highlighting
- **MISS** → essentially neutral

This visual comparison is one of the most important pieces to preserve in Mismo.

### Important limitation

Only nine fields are shown in the primary comparison panel even though substantially more patient data is parsed.

The currently selected case is `SUFFIX_MISSING`, for example, but **name suffix is not one of the fields in the main comparison table**. The reviewer would have to inspect the lower working area or infer the issue from the scenario name.

I would not reproduce this limitation. The replacement should probably show the union of relevant/populated Patient A and Patient B fields, while still allowing the most important demographics to appear first.

---

# 6. Recording the Expert's Determination

The workbook contains VBA routines for four intended expert determinations:

```text
Match
Possible Match
Not a Match
Not Sure
```

These correspond well to the current Mismo data model, which already permits `Match`, `Possible Match`, `Not a Match`, `Research`, and `Not Sure` as expected statuses.

The primary VBA procedures are:

```text
RecordIndicatorMatch
RecordIndicatorPossibleMatch
RecordIndicatorNotAMatch
RecordIndicatorNotSure
```

When a determination is made, the macro calculates the review-storage row as:

```text
current case number + 90
```

It then stores:

| Column | Stored information |
|---|---|
| B | Expert classification |
| C | Notes |
| D | Review flag |
| F | Review timestamp |
| G | Reviewer name |

Conceptually:

```text
ExpertReview
    testCase
    determination
    notes
    needsReview
    reviewedAt
    reviewedBy
```

---

# 7. What Happens When the Reviewer Clicks "Match"

The VBA is essentially:

```text
reviewRow = currentCase + 90

reviewRow.classification = "Match"
reviewRow.reviewedAt = Now
reviewRow.reviewedBy = reviewerName

goToNextCase()
```

`Possible Match` and `Not a Match` work identically except for the classification written.

This is important behavior to preserve: **choosing an answer also saves the adjudication and advances the review workflow.**

---

# 8. Reviewer Identity and Timestamp

The reviewer enters or has their name in `G8`.

For example, the workbook currently contains:

```text
Shelby Sandstrom
```

When a classification is recorded, the VBA copies that name into the review row and records `Now`.

In Mismo this should instead come from the authenticated user:

```text
reviewedBy = currentUser
reviewedAt = server timestamp
```

There should be no need for the reviewer to type their name into the review interface.

---

# 9. Notes

There is a working note cell, `F17`.

When moving away from a case, `GoNext`, `GoPrevious`, and `GoJump` save the contents of that working cell into:

```text
Comparison column C for the current review row
```

After changing cases, the note belonging to the newly selected case is loaded back into `F17`.

So notes are **per-test-case expert-review notes**, not part of either Patient record.

This should be a normal editable comment/note field in Mismo.

---

# 10. "Review" Is Different From "Not Sure"

There is also a **Review** action.

Its VBA writes:

```text
Review
```

into column D of the current review row and advances.

This appears to mean:

> Flag this case for additional review.

That is conceptually separate from the classification `Not Sure`.

The replacement should preserve that distinction:

```text
determination = MATCH | POSSIBLE_MATCH | NOT_A_MATCH | NOT_SURE

needsFurtherReview = true/false
```

A reviewer could therefore theoretically classify something and independently flag it for discussion.

---

# 11. Navigation

The workbook supports:

**Previous** and **Next**

These decrement or increment the current case pointer.

**Jump**

The reviewer enters a test-case number and jumps directly to it.

The VBA also contains routines intended to find the next:

```text
Not Sure
Fail
Not Sure or Fail
case edited by a particular person
```

These indicate useful workflow requirements even though some of the corresponding controls appear to be legacy or broken.

For Mismo, useful filters/navigation would therefore include:

```text
Next unreviewed
Next Not Sure
Next flagged for review
Next disagreement/failure
Reviewed by me
Reviewed by another reviewer
Jump to case
Previous / Next
```

---

# 12. Existing Classification Display

The Comparison screen also displays the classification already stored for the current case.

Separate display cells illuminate when the current recorded determination is:

```text
Match
Possible Match
Not a Match
Not Sure
```

This lets someone revisit cases and see the existing expert decision before changing it.

For an application this can simply be a selected button/radio state.

---

# 13. Suggested Result / Prior Analysis

The Comparison screen contains:

```text
Suggested Result: ...
```

This comes from the `Original` sheet, which contains prior manual review of cases including:

- expected result
- actual algorithm result
- suggested result
- whether the algorithm was considered wrong
- reviewer notes

This suggestion does **not** determine the expert's recorded answer. It is informational.

There is an important design question here for Mismo: if the purpose is independent expert adjudication, showing the previous expected value, algorithm result, or another expert's suggested result **before the reviewer decides can bias the gold-standard classification**.

I would make this information optionally hidden until after an independent determination, rather than automatically copying the spreadsheet behavior.

---

# 14. How the Expert Review Becomes Training Data

This is a particularly important part of the workbook.

The `Data` sheet has a second generated representation of every test case.

For `TEST`, `PATIENT A`, and `PATIENT B`, it simply copies the original source.

But for each `EXPECT:` row, it substitutes the expert classification stored on the Comparison sheet:

```excel
"EXPECT: " &
INDEX(Comparison!B$91:B$3925, caseNumber)
```

So the workflow is effectively:

```text
Original test case
       ↓
Expert review
       ↓
New expected classification
       ↓
Regenerated Mismo test/training file
```

This means the spreadsheet is not merely a review UI. It is also an **adjudication-to-training-data pipeline**.

That behavior needs an explicit replacement in Mismo.

I would not overwrite the original label immediately. A stronger model would preserve:

```text
original expected classification
expert review(s)
adjudicated/final classification
```

and only use the adjudicated value when constructing an approved training set.

---

# 15. Secondary Logic on the Comparison Sheet

Columns approximately `M:AF` contain another matrix involving:

- error/scenario types such as Address Changed, DOB Swapped, First Name Typo, MRN Shared, SSN Typo, etc.
- severity levels such as Low / Medium / High
- data-quality levels such as IDEAL / GREAT / GOOD / POOR / BAD
- numeric weights
- formulas producing Match / Possible / Not Sure recommendations

This appears to be an **older heuristic or analysis aid**.

I found no dependency from this matrix into the core expert-classification recording or the regenerated training data.

I therefore would **not consider it part of the minimum functionality that Mismo must reproduce** without further evidence that reviewers actually use it.

---

# 16. Workbook Problems That Should Not Be Replicated

There are several signs of accumulated spreadsheet complexity.

Most significantly, **the saved form-control wiring for `Not Sure` appears wrong**.

The VBA contains a proper:

```text
RecordIndicatorNotSure
```

routine that writes `"Not Sure"`.

However, the visible form control labeled **Not Sure** is wired in the workbook package to:

```text
RecordIndicatorPossibleMatch
```

which writes `"Possible Match"`.

Likewise, controls labeled **Next Not Sure** and **Next Fail** are not connected to the corresponding navigation routines even though routines such as `GoNextNotSure` and `GoNextFail` exist.

I would treat these as stale/broken Excel control wiring rather than intended requirements.

There is supporting evidence in the data: the source data contains `Not Sure` cases, but the review-result area contains essentially Match / Possible Match / Not a Match and no recorded Not Sure classifications.

There are also some stale `#REF!` formulas and miscellaneous cells whose labels and contents no longer correspond cleanly.

Finally, the workbook contains about **3,162 source test cases but slightly more populated adjudication rows**, another indication that row position is being used as an implicit database key and has drifted over time.

These are good reasons to model the workflow explicitly rather than reproducing the spreadsheet structurally.

---

# 17. Minimum Mismo Replacement

The functionality I would regard as essential is:

| Capability | Required behavior |
|---|---|
| Test-case queue | Select an individual Patient A / Patient B pair for review |
| Case context | Show test ID, scenario/source information, existing expected classification |
| Side-by-side comparison | Show the two Patient records by field |
| Visual differences | Clearly indicate same, different, Patient-A-only, Patient-B-only, and both missing |
| Expert classification | Match / Possible Match / Not a Match / Not Sure |
| Review flag | Independently flag a case for further review |
| Notes | Free-text expert comments |
| Provenance | Reviewer identity and timestamp |
| Navigation | Previous, Next, Jump, Next unreviewed, filtered queues |
| Re-review | Show and permit editing of an existing expert determination |
| Adjudication | Distinguish original expected value from expert-reviewed/final value |
| Training-set integration | Generate/use the adjudicated classification as the expected result for Mismo training/testing |

---

# 18. Recommended Mismo UI Concept

A direct replacement could be considerably simpler than the spreadsheet:

```text
Test D-1195 · SUFFIX_MISSING · GREATB-GOODB
Original expected: Not a Match

                   PATIENT A             PATIENT B
Birth Date         20110706     SAME     20110706
First Name         Lucilius     DIFF     Lyle
Middle Name        Sherman      SAME     Sherman
Last Name          Van Zandt    DIFF     Vance
Suffix             —            MISS     —
Street             266 Oldham   DIFF     335 Toale
City               Wales        DIFF     Cross Village
State              MI           SAME     MI
ZIP                48027         DIFF     49723
Phone               (810)...     A ONLY   —

[ Match ] [ Possible Match ] [ Not a Match ] [ Not Sure ]

Notes: _______________________________________________

[ ] Needs additional review

                     Previous     Save & Next
```

Unlike the spreadsheet, I would show **all relevant/populated fields**, not a fixed nine-field subset.

The comparison indicator should remain a simple visual description of the raw data. Mismo's actual matching evidence and detector output could optionally be exposed separately, but it should not be confused with the raw field comparison.

---

## Bottom Line

The spreadsheet is effectively a small, Excel-based **expert adjudication application**.

Its important conceptual pieces are not the formulas or VBA. They are:

**test-case selection → side-by-side patient comparison → visual differences → expert determination → notes/provenance → review workflow → finalized training label.**

Those concepts should be implemented directly in Mismo's domain model and UI rather than recreating the spreadsheet's row offsets, string parsing, formulas, or macros.