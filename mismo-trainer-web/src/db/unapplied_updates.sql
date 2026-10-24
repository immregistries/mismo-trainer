-- unapplied_updates.sql
--
-- Pending, not-yet-released database changes. Applied manually against a
-- local copy of the database before development/testing (see
-- docs/v2-roadmap.md §8), then folded into a dated/versioned snapshot
-- (e.g. upgrade-2.1.sql) and reset to empty on release.
--
-- This is NOT applied automatically as part of the build, and has not been
-- run against any live database.
--
-- Empty as of the v2.0 release -- everything that had accumulated here was
-- copied into upgrade-2.0.sql and this file was reset. See that file for
-- the full v2.0 change history.

-- Pilot 3 configuration (Jacob Lake / Twix Bar, generation 31855, hash
-- ROhJd7fGooqEiL9) was imported into the legacy organization, so OrgScope
-- hid it from every other organization, including signature hash lookup.
-- Flag it as a template so all organizations can read it. Templates must be
-- owned by the template-eligible organization (AIRA, is_template_org = 1;
-- see OrgScope.requireTemplateEligible), so ownership moves there too.
-- Keyed on the hash, not the row id.
UPDATE configuration
   SET is_template = 1,
       organization_id = (SELECT organization_id FROM organization WHERE is_template_org = 1
                           ORDER BY organization_id LIMIT 1)
 WHERE hash_for_signature = 'ROhJd7fGooqEiL9'
   AND world_name = 'Jacob Lake'
   AND island_name = 'Twix Bar'
   AND generation = 31855;

-- Select Configuration page (WeightSetServlet): remembers which
-- configurations each user has loaded, so the page can offer the last few
-- as one-click choices and the most recent one is loaded again
-- automatically at the start of every new session. One row per (user,
-- configuration); selected_at is moved forward each time it is reselected.
CREATE TABLE user_configuration_recent (
    user_configuration_recent_id int NOT NULL AUTO_INCREMENT,
    user_id int NOT NULL,
    configuration_id int NOT NULL,
    selected_at datetime NOT NULL,
    PRIMARY KEY (user_configuration_recent_id),
    UNIQUE KEY uk_user_configuration_recent (user_id, configuration_id),
    KEY idx_user_configuration_recent_selected (user_id, selected_at),
    CONSTRAINT fk_user_configuration_recent_user FOREIGN KEY (user_id) REFERENCES user (user_id),
    CONSTRAINT fk_user_configuration_recent_configuration FOREIGN KEY (configuration_id)
        REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Corrected built-in default configuration (mismo-match's
-- mismo-matcher.yml, World "Alma" / Island "Alpha"). Its F-sameButOne node
-- was labelled "F-match"; the label is part of the hash, so fixing it moves
-- the default's hash from ehgQqWjs4Ax1QZc (stored as configuration 1, kept
-- so existing signatures still decode) to Qds4KpMKC78aDNQ. Added as an
-- AIRA-owned template so new default signatures decode too. The script is
-- exactly what mismo-match writes back out after reading the file.
INSERT INTO configuration (world_name, island_name, generation, generation_score, generated_date,
                           hash_for_signature, configuration_script, organization_id, is_template,
                           created_at)
SELECT 'Alma', 'Alpha', 1, 0.0, '2024-04-03 12:00:00', 'Qds4KpMKC78aDNQ',
       'worldName: Alma
islandName: Alpha
generation: 1
generationScore: 0.0
generatedDate: "2024-04-03 12:00:00"
scoringWeights: 
  shouldMatch_Matches: 12
  shouldMatch_Possible: 10
  shouldMatch_NoMatch: -10
  shouldPossible_Matches: 8
  shouldPossible_Possible: 10
  shouldPossible_NoMatch: -8
  shouldNoMatch_Matches: -10
  shouldNoMatch_Possible: -8
  shouldNoMatch_NoMatch: 10
Match:
  label: Match
  enabled: true
  minScore: 0.0
  maxScore: 1.0
  Household:
    label: Household
    enabled: true
    minScore: 0.0
    maxScore: 0.4
    Last Name:
      label: Last Name
      enabled: true
      minScore: 0.0
      maxScore: 0.4
      L-match:
        label: L-match
        detector: ExactMatchNode
        fieldName: nameLast
        enabled: true
        minScore: 0.0
        maxScore: 1.0
      L-similar:
        label: L-similar
        detector: SimilarMatchNode
        fieldName: nameLast
        enabled: true
        minScore: 0.0
        maxScore: 0.4
      L-hyphenated:
        label: L-hyphenated
        detector: HyphenMatchNode
        fieldName: nameLast
        enabled: true
        minScore: 0.0
        maxScore: 0.6
      L-first:
        label: L-first
        detector: ExactMatchNode
        fieldName: nameLast
        fieldNameOther: nameFirst
        enabled: true
        minScore: 0.0
        maxScore: 0.5
      L-middle:
        label: L-middle
        detector: ExactMatchNode
        fieldName: nameLast
        fieldNameOther: nameMiddle
        enabled: true
        minScore: 0.0
        maxScore: 0.5
    Guardian:
      label: Guardian
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      GF-match:
        label: GF-match
        detector: ExactMatchNode
        fieldName: guardianNameFirst
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      GL-match:
        label: GL-match
        detector: ExactMatchNode
        fieldName: guardianNameLast
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      GF-GL-match:
        label: GF-GL-match
        detector: ExactMatchNode
        fieldName: guardianNameFirst
        fieldNameOther: guardianNameLast
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      MM-match:
        label: MM-match
        detector: ExactMatchNode
        fieldName: motherMaidenName
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      GL-MM-match:
        label: GL-MM-match
        detector: ExactMatchNode
        fieldName: motherMaidenName
        fieldNameOther: guardianNameLast
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    Location:
      label: Location
      enabled: true
      minScore: 0.0
      maxScore: 0.3
      PN-match:
        label: PN-match
        detector: ExactMatchNode
        fieldName: phone
        enabled: true
        minScore: 0.0
        maxScore: 0.5
      AD-match:
        label: AD-match
        detector: AddressMatchNode
        enabled: true
        minScore: 0.0
        maxScore: 0.7
  Person:
    label: Person
    enabled: true
    minScore: 0.0
    maxScore: 0.6
    Patient Id:
      label: Patient Id
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      MRN-match:
        label: MRN-match
        detector: AtLeastOneExactMatchNode
        fieldName: mrns
        splitParameter: "|"
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      SSN-match:
        label: SSN-match
        detector: ExactMatchNode
        fieldName: ssn
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      MA-match:
        label: MA-match
        detector: ExactMatchNode
        fieldName: medicaid
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    First Name:
      label: First Name
      enabled: true
      minScore: 0.0
      maxScore: 0.3
      F-match:
        label: F-match
        detector: ExactMatchNode
        fieldName: nameFirst
        enabled: true
        minScore: 0.0
        maxScore: 0.9
      F-sameButOne:
        label: F-sameButOne
        detector: OneTypoMatchNode
        fieldName: nameFirst
        enabled: true
        minScore: 0.0
        maxScore: 0.9
      F-similar:
        label: F-similar
        detector: SimilarMatchNode
        fieldName: nameFirst
        enabled: true
        minScore: 0.0
        maxScore: 0.5
      F-middle:
        label: F-middle
        detector: ExactMatchNode
        fieldName: nameFirst
        fieldNameOther: nameMiddle
        enabled: true
        minScore: 0.0
        maxScore: 0.4
      A-match:
        label: A-match
        detector: ExactMatchNode
        fieldName: nameFirst
        fieldNameOther: nameAlias
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      G-match:
        label: G-match
        detector: ExactMatchNode
        fieldName: gender
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    Birth Order:
      label: Birth Order
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      BO-matches:
        label: BO-matches
        detector: BirthOrderMatchNode
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      MBS-no:
        label: MBS-no
        detector: BirthNotIndicatedMatchNode
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    DOB:
      label: DOB
      enabled: true
      minScore: 0.0
      maxScore: 0.4
      DOB-match:
        label: DOB-match
        detector: ExactMatchNode
        fieldName: birthDate
        enabled: true
        minScore: 0.0
        maxScore: 1.0
      DOB-similar:
        label: DOB-similar
        detector: SimilarDateMatchNode
        fieldName: birthDate
        enabled: true
        minScore: 0.0
        maxScore: 0.65
    Middle Name:
      label: Middle Name
      enabled: true
      minScore: 0.0
      maxScore: 0.3
      M-match:
        label: M-match
        detector: ExactMatchNode
        fieldName: nameMiddle
        enabled: true
        minScore: 0.0
        maxScore: 1.0
      M-initial:
        label: M-initial
        detector: SameInitialMatchNode
        fieldName: nameMiddle
        enabled: true
        minScore: 0.0
        maxScore: 0.6
      M-similar:
        label: M-similar
        detector: SimilarMatchNode
        fieldName: nameMiddle
        enabled: true
        minScore: 0.0
        maxScore: 0.6
      S-match:
        label: S-match
        detector: DoNotConflictMatchNode
        fieldName: nameSuffix
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    Shot History:
      label: Shot History
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      SH-match:
        label: SH-match
        detector: HistoryMatchNode
        fieldName: shotHistory
        enabled: false
        minScore: 0.0
        maxScore: 0.0
Not Match:
  label: Not Match
  enabled: true
  minScore: 0.0
  maxScore: 1.0
  Household:
    label: Household
    enabled: true
    minScore: 0.0
    maxScore: 0.4
    Last Name:
      label: Last Name
      enabled: true
      minScore: 0.0
      maxScore: 0.4
      L-not-match:
        label: L-not-match
        detector: ExactMatchNode
        fieldName: nameLast
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 1.0
      L-not-similar:
        label: L-not-similar
        detector: SimilarMatchNode
        fieldName: nameLast
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.4
      L-not-hyphenated:
        label: L-not-hyphenated
        detector: HyphenMatchNode
        fieldName: nameLast
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.6
      L-not-first:
        label: L-not-first
        detector: ExactMatchNode
        fieldName: nameLast
        fieldNameOther: nameFirst
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.5
      L-not-middle:
        label: L-not-middle
        detector: ExactMatchNode
        fieldName: nameLast
        fieldNameOther: nameMiddle
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.5
    Guardian:
      label: Guardian
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      GF-not-match:
        label: GF-not-match
        detector: ExactMatchNode
        fieldName: guardianNameFirst
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      GL-not-match:
        label: GL-not-match
        detector: ExactMatchNode
        fieldName: guardianNameLast
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      MM-not-match:
        label: MM-not-match
        detector: ExactMatchNode
        fieldName: motherMaidenName
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.5
      MM-not-similar:
        label: MM-not-similar
        detector: SimilarMatchNode
        fieldName: motherMaidenName
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.5
    Location:
      label: Location
      enabled: true
      minScore: 0.0
      maxScore: 0.3
      PN-not-match:
        label: PN-not-match
        detector: ExactMatchNode
        fieldName: phone
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.4
      AD-not-match:
        label: AD-not-match
        detector: AddressMatchNode
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.9
  Person:
    label: Person
    enabled: true
    minScore: 0.0
    maxScore: 0.6
    Patient Id:
      label: Patient Id
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      MRN-not-match:
        label: MRN-not-match
        detector: AtLeastOneExactMatchNode
        fieldName: mrns
        splitParameter: "|"
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      SSN-not-match:
        label: SSN-not-match
        detector: ExactMatchNode
        fieldName: ssn
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      MA-not-match:
        label: MA-not-match
        detector: ExactMatchNode
        fieldName: medicaid
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    First Name:
      label: First Name
      enabled: true
      minScore: 0.0
      maxScore: 0.3
      F-not-match:
        label: F-not-match
        detector: ExactMatchNode
        fieldName: nameFirst
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.3
      F-not-similar:
        label: F-not-similar
        detector: SimilarMatchNode
        fieldName: nameFirst
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.3
      F-not-middle:
        label: F-not-middle
        detector: ExactMatchNode
        fieldName: nameMiddle
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      A-not-match:
        label: A-not-match
        detector: ExactMatchNode
        fieldName: nameAlias
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    Gender:
      label: Gender
      enabled: true
      minScore: 0.0
      maxScore: 0.4
      G-not-match:
        label: G-not-match
        detector: ExactMatchNode
        fieldName: gender
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    DOB:
      label: DOB
      enabled: true
      minScore: 0.0
      maxScore: 0.4
      DOB-not-match:
        label: DOB-not-match
        detector: ExactMatchNode
        fieldName: birthDate
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 1.0
      DOB-not-similar:
        label: DOB-not-similar
        detector: SimilarDateMatchNode
        fieldName: birthDate
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.65
    Middle Name:
      label: Middle Name
      enabled: true
      minScore: 0.0
      maxScore: 0.3
      M-not-match:
        label: M-not-match
        detector: ExactMatchNode
        fieldName: nameMiddle
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 1.0
      M-not-initial:
        label: M-not-initial
        detector: SameInitialMatchNode
        fieldName: nameMiddle
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.6
      M-not-similar:
        label: M-not-similar
        detector: SimilarMatchNode
        fieldName: nameMiddle
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.6
      S-not-match:
        label: S-not-match
        detector: DoNotConflictMatchNode
        fieldName: nameSuffix
        not: true
        enabled: true
        minScore: 0.0
        maxScore: 0.1
    Shot History:
      label: Shot History
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      SH-not-match:
        label: SH-not-match
        detector: HistoryMatchNode
        fieldName: shotHistory
        not: true
        enabled: false
        minScore: 0.0
        maxScore: 0.0
Twin:
  label: Suspect Twin
  enabled: true
  minScore: 0.0
  maxScore: 1.0
  Name Different:
    label: Name Different
    enabled: true
    minScore: 0.0
    maxScore: 0.2
    F-not-match:
      label: F-not-match
      detector: ExactMatchNode
      fieldName: nameFirst
      not: true
      enabled: true
      minScore: 0.0
      maxScore: 0.5
    F-not-similar:
      label: F-not-similar
      detector: SimilarMatchNode
      fieldName: nameFirst
      not: true
      enabled: true
      minScore: 0.0
      maxScore: 0.6
    M-not-match:
      label: M-not-match
      detector: ExactMatchNode
      fieldName: nameMiddle
      not: true
      enabled: true
      minScore: 0.0
      maxScore: 0.5
    G-not-match:
      label: G-not-match
      detector: ExactMatchNode
      fieldName: gender
      not: true
      enabled: false
      minScore: 0.0
      maxScore: 0.0
  Birth Date:
    label: Birth Date
    enabled: true
    minScore: 0.0
    maxScore: 0.2
    DOB-match:
      label: DOB-match
      detector: ExactMatchNode
      not: true
      enabled: true
      minScore: 0.0
      maxScore: 1.0
  Birth Status:
    label: Birth Status
    enabled: false
    minScore: 0.0
    maxScore: 0.0
    MBS-maybe:
      label: MBS-maybe
      detector: BirthMayBeTwinMatchNode
      enabled: false
      minScore: 0.0
      maxScore: 0.0
    MBS-yes:
      label: MBS-yes
      detector: BirthHasTwinMatchNode
      enabled: false
      minScore: 0.0
      maxScore: 0.0
Missing:
  label: Missing
  enabled: true
  minScore: 0.0
  maxScore: 1.0
  Household:
    label: Household
    enabled: true
    minScore: 0.0
    maxScore: 0.4
    L-missing:
      label: L-missing
      detector: MissingMatchNode
      fieldName: nameLast
      enabled: true
      minScore: 0.0
      maxScore: 0.5
    Household:
      label: Household
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      GFN-missing:
        label: GFN-missing
        detector: MissingMatchNode
        fieldName: guardianNameFirst
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      GLN-missing:
        label: GLN-missing
        detector: MissingMatchNode
        fieldName: guardianNameLast
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      MMN-missing:
        label: MMN-missing
        detector: MissingMatchNode
        fieldName: motherMaidenName
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    Location:
      label: Location
      enabled: true
      minScore: 0.0
      maxScore: 0.4
      PN-missing:
        label: PN-missing
        detector: MissingMatchNode
        fieldName: phone
        enabled: true
        minScore: 0.0
        maxScore: 0.2
      AS1-missing:
        label: AS1-missing
        detector: MissingMatchNode
        fieldName: addressStreet1
        fieldName2: address2Street1
        fieldName3: address3Street1
        enabled: true
        minScore: 0.0
        maxScore: 0.4
      AS2-missing:
        label: AS2-missing
        detector: MissingMatchNode
        fieldName: addressStreet2
        fieldName2: address2Street2
        fieldName3: address3Street2
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      AC-missing:
        label: AC-missing
        detector: MissingMatchNode
        fieldName: addressCity
        fieldName2: address2City
        fieldName3: address3City
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      AS-missing:
        label: AS-missing
        detector: MissingMatchNode
        fieldName: addressState
        fieldName2: address2State
        fieldName3: address3State
        enabled: true
        minScore: 0.0
        maxScore: 0.05
      AZ-missing:
        label: AZ-missing
        detector: MissingMatchNode
        fieldName: addressZip
        fieldName2: address2Zip
        fieldName3: address3Zip
        enabled: false
        minScore: 0.0
        maxScore: 0.0
  Person:
    label: Person
    enabled: true
    minScore: 0.0
    maxScore: 0.6
    Patient Id:
      label: Patient Id
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      MRN-missing:
        label: MRN-missing
        detector: MissingMatchNode
        fieldName: mrns
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      SSN-missing:
        label: SSN-missing
        detector: MissingMatchNode
        fieldName: ssn
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      MA-missing:
        label: MA-missing
        detector: MissingMatchNode
        fieldName: medicaid
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    First Name:
      label: First Name
      enabled: true
      minScore: 0.0
      maxScore: 0.3
      F-missing:
        label: F-missing
        detector: MissingMatchNode
        fieldName: nameFirst
        enabled: true
        minScore: 0.0
        maxScore: 1.0
      A-missing:
        label: A-missing
        detector: MissingMatchNode
        fieldName: nameAlias
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      S-missing:
        label: S-missing
        detector: MissingMatchNode
        fieldName: nameSuffix
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      G-missing:
        label: G-missing
        detector: MissingMatchNode
        fieldName: gender
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    Birth Order:
      label: Birth Order
      enabled: false
      minScore: 0.0
      maxScore: 0.0
      MBS-missing:
        label: MBS-missing
        detector: MissingMatchNode
        fieldName: birthStatus
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      BM-missing:
        label: BM-missing
        detector: MissingMatchNode
        fieldName: birthType
        enabled: false
        minScore: 0.0
        maxScore: 0.0
      BO-missing:
        label: BO-missing
        detector: MissingMatchNode
        fieldName: birthOrder
        enabled: false
        minScore: 0.0
        maxScore: 0.0
    DOB-missing:
      label: DOB-missing
      detector: MissingMatchNode
      fieldName: birthDate
      enabled: true
      minScore: 0.0
      maxScore: 0.4
    M-missing:
      label: M-missing
      detector: MissingMatchNode
      fieldName: nameMiddle
      enabled: true
      minScore: 0.0
      maxScore: 0.2
    SH-missing:
      label: SH-missing
      detector: MissingMatchNode
      fieldName: shotHistory
      enabled: false
      minScore: 0.0
      maxScore: 0.0
',
       o.organization_id, 1, NOW()
  FROM organization o
 WHERE o.is_template_org = 1
   AND NOT EXISTS (SELECT 1 FROM configuration WHERE hash_for_signature = 'Qds4KpMKC78aDNQ')
 ORDER BY o.organization_id
 LIMIT 1;

-- ---------------------------------------------------------------------------
-- Island Runner, Slice 1 (docs/island-runner-implementation-plan.md §3).
-- Centrally managed local runners: pairing, assignments, Scoring Profiles,
-- immutable Test Set snapshots, and optimization runs. Replaces the legacy
-- Island / CentralServlet / island_credential stack removed in Slice 0.
-- ---------------------------------------------------------------------------

-- One installed copy of the Mismo Runner on one computer. Only hashes of the
-- pairing code and the durable credential are stored.
CREATE TABLE runner (
    runner_id int NOT NULL AUTO_INCREMENT,
    organization_id int NOT NULL,
    display_name varchar(100) NOT NULL,
    pairing_code_hash varchar(64) NULL,
    pairing_expires_at datetime NULL,
    credential_hash varchar(64) NULL,
    credential_created_at datetime NULL,
    credential_last_used_at datetime NULL,
    credential_revoked_at datetime NULL,
    client_version varchar(30) NULL,
    protocol_version varchar(10) NULL,
    runtime_info varchar(255) NULL,
    last_seen_at datetime NULL,
    created_at datetime NOT NULL,
    created_by_user_id int NOT NULL,
    PRIMARY KEY (runner_id),
    UNIQUE KEY uk_runner_credential (credential_hash),
    UNIQUE KEY uk_runner_pairing (pairing_code_hash),
    KEY idx_runner_organization (organization_id),
    CONSTRAINT fk_runner_organization FOREIGN KEY (organization_id) REFERENCES organization (organization_id),
    CONSTRAINT fk_runner_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A named, versioned fitness function (see
-- docs/optimization-objectives-and-scoring-profiles.md). Rows are never
-- updated: an edit inserts version + 1 with the same root id.
CREATE TABLE scoring_profile (
    scoring_profile_id int NOT NULL AUTO_INCREMENT,
    root_scoring_profile_id int NOT NULL,
    version int NOT NULL,
    name varchar(150) NOT NULL,
    purpose varchar(30) NOT NULL,
    description text NOT NULL,
    scoring_weights_json varchar(500) NOT NULL,
    organization_id int NOT NULL,
    is_template tinyint(1) NOT NULL DEFAULT 0,
    created_at datetime NOT NULL,
    created_by_user_id int NULL,
    PRIMARY KEY (scoring_profile_id),
    UNIQUE KEY uk_scoring_profile_version (root_scoring_profile_id, version),
    KEY idx_scoring_profile_organization (organization_id),
    CONSTRAINT fk_scoring_profile_organization FOREIGN KEY (organization_id) REFERENCES organization (organization_id),
    CONSTRAINT fk_scoring_profile_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- What a runner should use for its next run. Rows are never updated: saving
-- the assignment form inserts revision + 1. The current assignment is the
-- highest revision.
CREATE TABLE runner_assignment (
    runner_assignment_id int NOT NULL AUTO_INCREMENT,
    runner_id int NOT NULL,
    revision int NOT NULL,
    name varchar(150) NOT NULL,
    configuration_id int NOT NULL,
    match_set_id int NOT NULL,
    population_size int NOT NULL,
    scoring_profile_id int NOT NULL,
    created_at datetime NOT NULL,
    created_by_user_id int NOT NULL,
    PRIMARY KEY (runner_assignment_id),
    UNIQUE KEY uk_runner_assignment_rev (runner_id, revision),
    CONSTRAINT fk_runner_assignment_runner FOREIGN KEY (runner_id) REFERENCES runner (runner_id),
    CONSTRAINT fk_runner_assignment_configuration FOREIGN KEY (configuration_id) REFERENCES configuration (configuration_id),
    CONSTRAINT fk_runner_assignment_match_set FOREIGN KEY (match_set_id) REFERENCES match_set (match_set_id),
    CONSTRAINT fk_runner_assignment_scoring_profile FOREIGN KEY (scoring_profile_id) REFERENCES scoring_profile (scoring_profile_id),
    CONSTRAINT fk_runner_assignment_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- An immutable copy of one match_set's scoreable cases, stored as the exact
-- JSON bytes served to runners, with their SHA-256.
CREATE TABLE test_set_snapshot (
    test_set_snapshot_id int NOT NULL AUTO_INCREMENT,
    match_set_id int NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    case_count int NOT NULL,
    lifecycle_status varchar(20) NOT NULL,
    content_json longtext NOT NULL,
    created_at datetime NOT NULL,
    PRIMARY KEY (test_set_snapshot_id),
    UNIQUE KEY uk_snapshot_content (match_set_id, content_sha256),
    CONSTRAINT fk_test_set_snapshot_match_set FOREIGN KEY (match_set_id) REFERENCES match_set (match_set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One execution of one assignment revision, with every input frozen.
CREATE TABLE optimization_run (
    optimization_run_id int NOT NULL AUTO_INCREMENT,
    runner_id int NOT NULL,
    runner_assignment_id int NOT NULL,
    seed_configuration_id int NOT NULL,
    seed_configuration_sha256 varchar(64) NOT NULL,
    test_set_snapshot_id int NOT NULL,
    population_size int NOT NULL,
    scoring_profile_id int NOT NULL,
    scoring_weights_json varchar(500) NOT NULL,
    client_version varchar(30) NOT NULL,
    protocol_version varchar(10) NOT NULL,
    status varchar(20) NOT NULL,
    stop_reason varchar(255) NULL,
    started_at datetime NOT NULL,
    stopped_at datetime NULL,
    last_reported_at datetime NULL,
    last_generation int NULL,
    candidate_rate double NULL,
    best_score double NULL,
    best_configuration_id int NULL,
    PRIMARY KEY (optimization_run_id),
    KEY idx_run_runner_status (runner_id, status),
    CONSTRAINT fk_optimization_run_runner FOREIGN KEY (runner_id) REFERENCES runner (runner_id),
    CONSTRAINT fk_optimization_run_assignment FOREIGN KEY (runner_assignment_id) REFERENCES runner_assignment (runner_assignment_id),
    CONSTRAINT fk_optimization_run_seed_configuration FOREIGN KEY (seed_configuration_id) REFERENCES configuration (configuration_id),
    CONSTRAINT fk_optimization_run_snapshot FOREIGN KEY (test_set_snapshot_id) REFERENCES test_set_snapshot (test_set_snapshot_id),
    CONSTRAINT fk_optimization_run_scoring_profile FOREIGN KEY (scoring_profile_id) REFERENCES scoring_profile (scoring_profile_id),
    CONSTRAINT fk_optimization_run_best_configuration FOREIGN KEY (best_configuration_id) REFERENCES configuration (configuration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Candidates uploaded by a run are configuration rows tied to it.
-- (optimization_run_id, content_sha256) makes a re-sent candidate idempotent;
-- rows not from a run have a NULL run id, which the unique key ignores.
ALTER TABLE configuration
    ADD COLUMN content_sha256 varchar(64) NULL,
    ADD COLUMN optimization_run_id int NULL,
    ADD UNIQUE KEY uk_configuration_run_content (optimization_run_id, content_sha256),
    ADD CONSTRAINT fk_configuration_optimization_run FOREIGN KEY (optimization_run_id)
        REFERENCES optimization_run (optimization_run_id);
UPDATE configuration SET content_sha256 = SHA2(configuration_script, 256);

-- The legacy Island machine credential is gone (Slice 0 removed its code).
ALTER TABLE configuration
    DROP FOREIGN KEY fk_configuration_island_credential;
ALTER TABLE configuration
    DROP INDEX idx_configuration_island_credential_id,
    DROP COLUMN island_credential_id;
DROP TABLE island_credential;

-- Seed: the one template Scoring Profile, the national measurement objective,
-- owned by the template-eligible organization. Weights are the canonical
-- defaults from mismo-match's mismo-matcher.yml.
INSERT INTO scoring_profile (root_scoring_profile_id, version, name, purpose, description,
                             scoring_weights_json, organization_id, is_template, created_at)
SELECT 0, 1, 'National measurement (potential match)', 'MEASUREMENT',
       'Steers toward the Patient Matching at Rest measurement objective. Match and Possible Match are treated as one "potential match" signal, and the instrument deliberately leans toward over-identifying: a missed potential match can never be recovered, while an over-count can be tuned out later. This is not a production-calibration matrix. See docs/optimization-objectives-and-scoring-profiles.md.',
       '{"shouldMatch_Matches":12,"shouldMatch_Possible":10,"shouldMatch_NoMatch":-10,"shouldPossible_Matches":8,"shouldPossible_Possible":10,"shouldPossible_NoMatch":-8,"shouldNoMatch_Matches":-10,"shouldNoMatch_Possible":-8,"shouldNoMatch_NoMatch":10}',
       o.organization_id, 1, NOW()
  FROM organization o
 WHERE o.is_template_org = 1
 ORDER BY o.organization_id
 LIMIT 1;
UPDATE scoring_profile SET root_scoring_profile_id = scoring_profile_id WHERE root_scoring_profile_id = 0;

-- Island Runner, Slice 2: the oldest Mismo Runner version the Runner API
-- accepts (RunnerApi.SETTING_MIN_CLIENT_VERSION). Older clients get
-- CLIENT_UNSUPPORTED (HTTP 426) and are told to download the current runner.
-- Raise it when a release must not run alongside older runners.
INSERT INTO app_setting (setting_key, setting_value)
SELECT 'runner.min.client.version', '2.0'
 WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'runner.min.client.version');

-- Island Runner, Slice 4: where the Runners page sends people to download the
-- Mismo Runner (a GitHub Release asset), and the zip's SHA-256 shown next to
-- the link. Update both rows for each new runner release; no redeploy needed.
-- An empty SHA-256 hides the line. See docs/production-deployment-checklist.md.
INSERT INTO app_setting (setting_key, setting_value)
SELECT 'runner.download.url',
       'https://github.com/immregistries/mismo-trainer/releases/download/runner-v2.0/Mismo-Runner-2.0-windows.zip'
 WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'runner.download.url');
INSERT INTO app_setting (setting_key, setting_value)
SELECT 'runner.download.sha256', ''
 WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'runner.download.sha256');
