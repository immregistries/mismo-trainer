package org.immregistries.mismo.trainer.servlet;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Query;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.trainer.Island;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.Evaluation;
import org.immregistries.mismo.trainer.model.EvaluationResult;
import org.immregistries.mismo.trainer.model.IslandCredential;
import org.immregistries.mismo.trainer.model.MatchItem;
import org.immregistries.mismo.trainer.model.MatchItemReview;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.Organization;
import org.immregistries.mismo.trainer.model.Scorer;
import org.immregistries.mismo.trainer.model.User;

/**
 * Enforces database-schema-migration-plan.md §4 (Tenant Enforcement Rules): every read or write
 * of {@code match_set}, {@code match_item}, {@code configuration}, or {@code island_credential}
 * is filtered by the authenticated session's organization, never by a client-supplied id alone.
 * {@code match_item} is scoped through its parent {@code match_set}. The bundled MIIS- / AIRA-
 * .txt flat-file corpora are outside this class entirely (section 2.9) -- they stay
 * global/unscoped. Resolving an Island machine credential by its raw token (not by a logged-in
 * user's organization) is handled separately by {@link IslandCredentialSupport}.
 *
 * <p>§2.10/§4.1 widens this for templates: a {@code match_set}/{@code configuration} row with
 * {@code isTemplate() == true} is <em>readable</em> by every organization regardless of owner,
 * but remains <em>writable</em> only by its owning organization -- the {@code load*}/{@code
 * list*} methods below implement the read side, {@link #isEditable} implements the write-side
 * check every mutating call site must still apply on top of a {@code load*} result.
 */
public final class OrgScope {

  private OrgScope() {
  }

  private static boolean sameOrganization(Organization organization, User user) {
    return organization != null && user != null && user.getOrganization() != null
        && organization.getOrganizationId() == user.getOrganization().getOrganizationId();
  }

  /** True if {@code organization} or a template row is visible to {@code user} (read access). */
  private static boolean readable(Organization organization, boolean isTemplate, User user) {
    return isTemplate || sameOrganization(organization, user);
  }

  /**
   * True if {@code matchSet} belongs directly to {@code user}'s organization -- the write-access
   * check. A template row visible via {@link #loadMatchSet} is not necessarily editable; only
   * the owning organization may ever edit or delete a row, template or not (§4.1).
   */
  public static boolean isEditable(MatchSet matchSet, User user) {
    return matchSet != null && sameOrganization(matchSet.getOrganization(), user);
  }

  /**
   * True if {@code matchSet}'s cases (patient data, classifications) may be added, uploaded, or
   * reclassified right now -- {@link #isEditable} plus not currently {@code APPROVED}
   * (v2-roadmap.md §10: "an Approved match_set rejects case-level edits until moved out of that
   * state"). The lifecycle status itself may still be changed regardless of this check -- moving
   * *out* of Approved is exactly how a set becomes case-editable again. Notes and the
   * needs-review flag are review-process metadata, not case content, and are not gated by this.
   */
  public static boolean canEditCases(MatchSet matchSet, User user) {
    return isEditable(matchSet, user) && !MatchSet.LIFECYCLE_APPROVED.equals(matchSet.getLifecycleStatus());
  }

  /** Write-access check for a {@code MatchItem}, verified through its parent {@code MatchSet}. */
  public static boolean isEditable(MatchItem matchItem, User user) {
    return matchItem != null && isEditable(matchItem.getMatchSet(), user);
  }

  /** Write-access check for a {@code Configuration} -- same rule as {@link #isEditable(MatchSet, User)}. */
  public static boolean isEditable(Configuration configuration, User user) {
    return configuration != null && sameOrganization(configuration.getOrganization(), user);
  }

  /**
   * Enforces §2.10/§4.1's rule that {@code isTemplate} may only be set {@code true} on a row
   * owned by an {@code is_template_org} organization -- an application-level check, since it is
   * deliberately not a database constraint.
   *
   * @throws IllegalStateException if {@code organization} is not template-eligible
   */
  public static void requireTemplateEligible(Organization organization) {
    if (organization == null || !organization.isTemplateOrg()) {
      throw new IllegalStateException(
          "Only a template-eligible organization (organization.is_template_org = true) may have"
              + " is_template set true on one of its rows.");
    }
  }

  /**
   * Loads a {@code MatchSet} by id, returning {@code null} unless it belongs to {@code user}'s
   * organization or is a template (§4.1) -- never another organization's private row.
   */
  public static MatchSet loadMatchSet(Session dataSession, int matchSetId, User user) {
    MatchSet matchSet = (MatchSet) dataSession.get(MatchSet.class, matchSetId);
    if (matchSet == null || !readable(matchSet.getOrganization(), matchSet.isTemplate(), user)) {
      return null;
    }
    return matchSet;
  }

  /**
   * Loads a {@code MatchItem} by id, verified through its parent {@code MatchSet}'s organization
   * or template status.
   */
  public static MatchItem loadMatchItem(Session dataSession, int matchItemId, User user) {
    MatchItem matchItem = (MatchItem) dataSession.get(MatchItem.class, matchItemId);
    if (matchItem == null || matchItem.getMatchSet() == null
        || !readable(matchItem.getMatchSet().getOrganization(), matchItem.getMatchSet().isTemplate(), user)) {
      return null;
    }
    return matchItem;
  }

  /**
   * Loads a {@code Configuration} by id, returning {@code null} unless it belongs to
   * {@code user}'s organization or is a template (§4.1).
   */
  public static Configuration loadConfiguration(Session dataSession, int configurationId, User user) {
    Configuration configuration = (Configuration) dataSession.get(Configuration.class, configurationId);
    if (configuration == null || !readable(configuration.getOrganization(), configuration.isTemplate(), user)) {
      return null;
    }
    return configuration;
  }

  /**
   * Lists every {@code MatchSet} belonging to {@code user}'s organization, plus every template,
   * excluding archived sets.
   */
  public static List<MatchSet> listMatchSets(Session dataSession, User user) {
    return listMatchSets(dataSession, user, false);
  }

  /**
   * Lists every {@code MatchSet} belonging to {@code user}'s organization, plus every template.
   *
   * @param includeArchived when {@code false} (the default view), sets with a non-null
   *     {@code archivedAt} are left out
   */
  @SuppressWarnings("unchecked")
  public static List<MatchSet> listMatchSets(Session dataSession, User user, boolean includeArchived) {
    String hql = "from MatchSet where (organization = ? or template = true)";
    if (!includeArchived) {
      hql += " and archivedAt is null";
    }
    hql += " order by updatedAt desc";
    Query query = dataSession.createQuery(hql);
    query.setParameter(0, user.getOrganization());
    return query.list();
  }

  /**
   * Lists every readable {@code MatchSet} sharing {@code rootMatchSetId} -- "all versions of
   * this Test Set" (v2-roadmap.md §10), oldest first.
   */
  @SuppressWarnings("unchecked")
  public static List<MatchSet> listVersionFamily(Session dataSession, int rootMatchSetId, User user) {
    Query query = dataSession.createQuery(
        "from MatchSet where rootMatchSetId = ? and (organization = ? or template = true) order by createdAt");
    query.setParameter(0, rootMatchSetId);
    query.setParameter(1, user.getOrganization());
    return query.list();
  }

  /** Lists every {@code Configuration} belonging to {@code user}'s organization, plus every template. */
  @SuppressWarnings("unchecked")
  public static List<Configuration> listConfigurations(Session dataSession, User user) {
    Query query = dataSession.createQuery(
        "from Configuration where organization = ? or template = true"
            + " order by worldName, islandName, generation desc");
    query.setParameter(0, user.getOrganization());
    return query.list();
  }

  /** Creates and saves a new, organization-owned {@code MatchSet}, attributed to {@code user}. */
  public static MatchSet createMatchSet(Session dataSession, String label, User user) {
    Date now = new Date();
    MatchSet matchSet = new MatchSet();
    matchSet.setLabel(label);
    matchSet.setOrganization(user.getOrganization());
    matchSet.setCreatedByUser(user);
    matchSet.setUpdatedByUser(user);
    matchSet.setCreatedAt(now);
    matchSet.setUpdatedAt(now);
    dataSession.save(matchSet);
    // rootMatchSetId is NOT NULL and self-referential: a freshly-created (non-copied) set is the
    // root of its own family, but its id isn't known until after the INSERT above. Correcting it
    // on the still-managed instance lets Hibernate's own dirty-checking issue the follow-up
    // UPDATE at flush time (v2-roadmap.md §10; database-changes-for-functional-model.md §2).
    matchSet.setRootMatchSetId(matchSet.getMatchSetId());
    return matchSet;
  }

  /**
   * Copies a template (or any readable) {@code MatchSet} -- including every one of its
   * {@code MatchItem} rows -- into a brand-new row owned by {@code user}'s organization (§2.10's
   * "copy" action, and v2-roadmap.md §10's "create new version"). The copy is never itself a
   * template; it starts out as an ordinary, {@code DRAFT}, fully-editable row owned by the
   * copying organization. Deep-copy behavior (database-changes-for-functional-model.md §4,
   * decided): every copied {@code MatchItem} gets a completely clean review history -- no
   * {@code MatchItemReview} rows are carried over, {@code isReviewed}/{@code needsReview} both
   * reset to {@code false}, and {@code originalExpectStatus} snapshots the source item's
   * <em>current</em> {@code expectStatus} at copy time (not the source's own original).
   *
   * @param versionLabel the new copy's {@link MatchSet#getVersion()}, or {@code null} to leave
   *     it unset (used by the plain "Copy to My Organization" template-adoption action, as
   *     opposed to an explicit "Create New Version" action which does supply one)
   */
  public static MatchSet copyMatchSet(Session dataSession, MatchSet source, User user, String versionLabel) {
    Date now = new Date();
    MatchSet copy = new MatchSet();
    copy.setLabel(source.getLabel());
    copy.setOrganization(user.getOrganization());
    copy.setTemplate(false);
    copy.setCreatedByUser(user);
    copy.setUpdatedByUser(user);
    copy.setCreatedAt(now);
    copy.setUpdatedAt(now);
    copy.setLifecycleStatus(MatchSet.LIFECYCLE_DRAFT);
    copy.setVersion(versionLabel);
    copy.setCopiedFromMatchSetId(source.getMatchSetId());
    copy.setRootMatchSetId(source.getRootMatchSetId());
    dataSession.save(copy);

    Query query = dataSession.createQuery("from MatchItem where matchSet = ? order by matchItemId");
    query.setParameter(0, source);
    @SuppressWarnings("unchecked")
    List<MatchItem> sourceItems = query.list();
    for (MatchItem sourceItem : sourceItems) {
      MatchItem itemCopy = new MatchItem();
      itemCopy.setMatchSet(copy);
      itemCopy.setLabel(sourceItem.getLabel());
      itemCopy.setDescription(sourceItem.getDescription());
      itemCopy.setPatientDataA(sourceItem.getPatientDataA());
      itemCopy.setPatientDataB(sourceItem.getPatientDataB());
      itemCopy.setExpectStatus(sourceItem.getExpectStatus());
      itemCopy.setDataSource(sourceItem.getDataSource());
      itemCopy.setCreatedByUser(user);
      itemCopy.setUpdatedByUser(user);
      itemCopy.setCreatedAt(now);
      itemCopy.setUpdatedAt(now);
      itemCopy.setOriginalExpectStatus(sourceItem.getExpectStatus());
      itemCopy.setReviewed(false);
      itemCopy.setNeedsReview(false);
      itemCopy.setProvenanceType(MatchItem.PROVENANCE_COPIED);
      itemCopy.setCopiedFromMatchItemId(sourceItem.getMatchItemId());
      dataSession.save(itemCopy);
    }
    return copy;
  }

  /**
   * Changes {@code matchSet}'s lifecycle status (Draft/Reviewed/Approved, reversible either
   * direction). Unlike case-level edits, this is never gated by the current status -- moving
   * *out* of Approved is how a set becomes case-editable again.
   *
   * @return {@code true} if {@code matchSet} is owned by {@code user}'s organization and the
   *     status was set, {@code false} if it belongs to another organization
   */
  public static boolean setLifecycleStatus(Session dataSession, MatchSet matchSet, String lifecycleStatus,
      User user) {
    if (!isEditable(matchSet, user)) {
      return false;
    }
    matchSet.setLifecycleStatus(lifecycleStatus);
    matchSet.setUpdatedByUser(user);
    matchSet.setUpdatedAt(new Date());
    dataSession.update(matchSet);
    return true;
  }

  /**
   * Archives or restores {@code matchSet} (independent of {@link MatchSet#getLifecycleStatus()}
   * -- an Approved set doesn't have to pass back through Draft to be archived).
   *
   * @return {@code true} if {@code matchSet} is owned by {@code user}'s organization and the
   *     archive state was set, {@code false} if it belongs to another organization
   */
  public static boolean setArchived(Session dataSession, MatchSet matchSet, boolean archived, User user) {
    if (!isEditable(matchSet, user)) {
      return false;
    }
    matchSet.setArchivedAt(archived ? new Date() : null);
    matchSet.setUpdatedByUser(user);
    matchSet.setUpdatedAt(new Date());
    dataSession.update(matchSet);
    return true;
  }

  /**
   * Copies a template (or any readable) {@code Configuration} into a brand-new row owned by
   * {@code user}'s organization (§2.10's "copy" action). The copy is never itself a template.
   */
  public static Configuration copyConfiguration(Session dataSession, Configuration source, User user) {
    Configuration copy = new Configuration();
    copy.setWorldName(source.getWorldName());
    copy.setIslandName(source.getIslandName());
    copy.setGeneration(source.getGeneration());
    copy.setGenerationScore(source.getGenerationScore());
    copy.setGeneratedDate(source.getGeneratedDate());
    copy.setHashForSignature(source.getHashForSignature());
    copy.setConfigurationScript(source.getConfigurationScript());
    copy.setOrganization(user.getOrganization());
    copy.setTemplate(false);
    copy.setCreatedByUser(user);
    copy.setCreatedAt(new Date());
    dataSession.save(copy);
    return copy;
  }

  /**
   * Records a review event for {@code matchItem}: inserts an immutable {@code MatchItemReview}
   * history row, and updates the live {@code expectStatus}/{@code isReviewed} on the item itself
   * (database-changes-for-functional-model.md §4 -- "the most recent accepted review becomes the
   * current expectation"). {@code isReviewed} is set {@code true} unconditionally, even if
   * {@code classification} equals the item's current {@code expectStatus} -- confirming an
   * already-correct classification still counts as reviewed.
   *
   * @return {@code false} without writing anything if {@code matchItem} isn't case-editable
   *     right now ({@link #canEditCases}), {@code true} once the review is recorded
   */
  public static boolean recordReview(Session dataSession, MatchItem matchItem, User user, String classification,
      String notes) {
    if (!canEditCases(matchItem.getMatchSet(), user)) {
      return false;
    }
    Date now = new Date();
    MatchItemReview review = new MatchItemReview();
    review.setMatchItem(matchItem);
    review.setReviewerUser(user);
    review.setClassification(classification);
    review.setNotes(notes);
    review.setReviewedAt(now);
    dataSession.save(review);

    matchItem.setExpectStatus(classification);
    matchItem.setReviewed(true);
    matchItem.setUpdatedByUser(user);
    matchItem.setUpdatedAt(now);
    dataSession.update(matchItem);
    return true;
  }

  /** Every {@code MatchItemReview} for {@code matchItem}, newest first. */
  @SuppressWarnings("unchecked")
  public static List<MatchItemReview> listMatchItemReviews(Session dataSession, MatchItem matchItem) {
    Query query = dataSession.createQuery("from MatchItemReview where matchItem = ? order by reviewedAt desc");
    query.setParameter(0, matchItem);
    return query.list();
  }

  /**
   * Updates {@code matchItem}'s free-text review notes -- independently of any classify action.
   *
   * @return {@code false} without writing anything if {@code matchItem} isn't case-editable
   *     right now ({@link #canEditCases}), {@code true} once saved
   */
  public static boolean setReviewNotes(Session dataSession, MatchItem matchItem, String notes, User user) {
    if (!canEditCases(matchItem.getMatchSet(), user)) {
      return false;
    }
    matchItem.setReviewNotes(notes);
    matchItem.setUpdatedByUser(user);
    matchItem.setUpdatedAt(new Date());
    dataSession.update(matchItem);
    return true;
  }

  /**
   * Toggles {@code matchItem}'s "needs further review" flag -- independently of
   * {@link MatchItem#isReviewed()} and of any classify action.
   *
   * @return {@code false} without writing anything if {@code matchItem} isn't case-editable
   *     right now ({@link #canEditCases}), {@code true} once saved
   */
  public static boolean setNeedsReview(Session dataSession, MatchItem matchItem, boolean needsReview, User user) {
    if (!canEditCases(matchItem.getMatchSet(), user)) {
      return false;
    }
    matchItem.setNeedsReview(needsReview);
    matchItem.setUpdatedByUser(user);
    matchItem.setUpdatedAt(new Date());
    dataSession.update(matchItem);
    return true;
  }

  @SuppressWarnings("unchecked")
  public static List<IslandCredential> listIslandCredentials(Session dataSession, User user) {
    Query query = dataSession.createQuery("from IslandCredential where organization = ? order by createdAt desc");
    query.setParameter(0, user.getOrganization());
    return query.list();
  }

  /** Creates and saves a new, organization-owned {@code IslandCredential}, attributed to {@code user}. */
  public static IslandCredential createIslandCredential(Session dataSession, String name, String credentialHash,
      User user) {
    IslandCredential credential = new IslandCredential();
    credential.setName(name);
    credential.setCredentialHash(credentialHash);
    credential.setOrganization(user.getOrganization());
    credential.setCreatedByUser(user);
    credential.setCreatedAt(new Date());
    dataSession.save(credential);
    return credential;
  }

  /**
   * Revokes an {@code IslandCredential} if it belongs to {@code user}'s organization.
   *
   * @return {@code true} if the credential was found (in this organization) and is now revoked
   *         (whether just now or already), {@code false} if it doesn't exist or belongs to
   *         another organization
   */
  public static boolean revokeIslandCredential(Session dataSession, int islandCredentialId, User user) {
    IslandCredential credential = (IslandCredential) dataSession.get(IslandCredential.class, islandCredentialId);
    if (!sameOrganization(credential == null ? null : credential.getOrganization(), user)) {
      return false;
    }
    if (credential.getRevokedAt() == null) {
      credential.setRevokedAt(new Date());
      dataSession.update(credential);
    }
    return true;
  }

  /**
   * Scores every {@code MatchItem} in {@code matchSet} against {@code configuration} -- reusing
   * the same {@code PatientCompare}/{@code Scorer} machinery {@code TestMatchingServlet} already
   * uses, not a reimplementation -- and persists the run as a new {@code Evaluation} plus one
   * {@code EvaluationResult} per case (v2-roadmap.md §11; database-changes-for-functional-
   * model.md §5). Caller owns the transaction, same as every other write in this class.
   *
   * <p>Every case gets a result row, including ones whose snapshotted expected classification is
   * "Not Sure" -- but those are excluded from {@code scorableCases}/{@code agreeCount}/
   * {@code disagreeCount}/{@code score} (functional model rule: "Not Sure is a valid analyst
   * classification but is not scorable").
   *
   * <p>{@code matchSet} and {@code configuration} are expected to already be readable (loaded via
   * {@link #loadMatchSet}/{@link #loadConfiguration}) -- this method does not re-check that, since
   * either may legitimately be a template owned by a different organization than {@code user}'s.
   * The evaluation itself is always owned by {@code user}'s own organization, whether or not the
   * inputs were.
   */
  @SuppressWarnings("unchecked")
  public static Evaluation runEvaluation(Session dataSession, MatchSet matchSet, Configuration configuration,
      User user) {
    PatientCompare patientCompare = new PatientCompare(configuration.getConfigurationScript());
    Scorer scorer = new Scorer(patientCompare.getConfiguration().getScoringWeights());

    Query query = dataSession.createQuery("from MatchItem where matchSet = ? order by matchItemId");
    query.setParameter(0, matchSet);
    List<MatchItem> rows = query.list();

    Evaluation evaluation = new Evaluation();
    evaluation.setOrganization(user.getOrganization());
    evaluation.setMatchSet(matchSet);
    evaluation.setConfiguration(configuration);
    evaluation.setRunByUser(user);
    evaluation.setRunAt(new Date());

    List<EvaluationResult> results = new ArrayList<EvaluationResult>();
    int notSureCases = 0;
    int agreeCount = 0;
    int disagreeCount = 0;
    for (MatchItem row : rows) {
      org.immregistries.mismo.match.model.MatchItem runtimeItem = Island.toRuntimeMatchItem(row);
      patientCompare.setMatchItem(runtimeItem);
      String calculated = patientCompare.getResult();
      String signature = patientCompare.getSignature();
      String expected = row.getExpectStatus();
      boolean agrees = calculated.equals(expected);
      boolean isNotSure = org.immregistries.mismo.match.model.MatchItem.NOT_SURE.equals(expected);
      if (isNotSure) {
        notSureCases++;
      } else {
        scorer.registerMatch(runtimeItem, patientCompare);
        if (agrees) {
          agreeCount++;
        } else {
          disagreeCount++;
        }
      }
      EvaluationResult result = new EvaluationResult();
      result.setEvaluation(evaluation);
      result.setMatchItem(row);
      result.setExpectedClassification(expected);
      result.setCalculatedClassification(calculated);
      result.setSignature(signature);
      result.setAgrees(agrees);
      results.add(result);
    }

    evaluation.setTotalCases(rows.size());
    evaluation.setScorableCases(rows.size() - notSureCases);
    evaluation.setNotSureCases(notSureCases);
    evaluation.setAgreeCount(agreeCount);
    evaluation.setDisagreeCount(disagreeCount);
    evaluation.setScore(evaluation.getScorableCases() > 0 ? scorer.getScore() : null);

    dataSession.save(evaluation);
    for (EvaluationResult result : results) {
      dataSession.save(result);
    }
    return evaluation;
  }

  /** Loads an {@code Evaluation} by id, returning {@code null} unless it belongs to {@code user}'s organization. */
  public static Evaluation loadEvaluation(Session dataSession, int evaluationId, User user) {
    Evaluation evaluation = (Evaluation) dataSession.get(Evaluation.class, evaluationId);
    if (!sameOrganization(evaluation == null ? null : evaluation.getOrganization(), user)) {
      return null;
    }
    return evaluation;
  }

  /** Every {@code Evaluation} run against {@code matchSet} by {@code user}'s organization, newest first. */
  @SuppressWarnings("unchecked")
  public static List<Evaluation> listEvaluationsForMatchSet(Session dataSession, MatchSet matchSet, User user) {
    Query query = dataSession.createQuery("from Evaluation where matchSet = ? and organization = ? order by runAt desc");
    query.setParameter(0, matchSet);
    query.setParameter(1, user.getOrganization());
    return query.list();
  }

  /** Every {@code Evaluation} run using {@code configuration} by {@code user}'s organization, newest first. */
  @SuppressWarnings("unchecked")
  public static List<Evaluation> listEvaluationsForConfiguration(Session dataSession, Configuration configuration,
      User user) {
    Query query = dataSession.createQuery(
        "from Evaluation where configuration = ? and organization = ? order by runAt desc");
    query.setParameter(0, configuration);
    query.setParameter(1, user.getOrganization());
    return query.list();
  }

  /**
   * The most recent {@code Evaluation} for this exact ({@code matchSet}, {@code configuration})
   * pair, owned by {@code user}'s organization, or {@code null} if none exists yet -- used to
   * offer reusing a recent run instead of always scoring fresh (database-changes-for-functional-
   * model.md §5's Configuration comparison).
   */
  @SuppressWarnings("unchecked")
  public static Evaluation findLatestEvaluation(Session dataSession, MatchSet matchSet, Configuration configuration,
      User user) {
    Query query = dataSession.createQuery(
        "from Evaluation where matchSet = ? and configuration = ? and organization = ? order by runAt desc");
    query.setParameter(0, matchSet);
    query.setParameter(1, configuration);
    query.setParameter(2, user.getOrganization());
    query.setMaxResults(1);
    List<Evaluation> list = query.list();
    return list.isEmpty() ? null : list.get(0);
  }

  /** Every {@code EvaluationResult} row for {@code evaluation}, in case order. */
  @SuppressWarnings("unchecked")
  public static List<EvaluationResult> listEvaluationResults(Session dataSession, Evaluation evaluation) {
    Query query = dataSession.createQuery("from EvaluationResult where evaluation = ? order by evaluationResultId");
    query.setParameter(0, evaluation);
    return query.list();
  }

  /**
   * Cases where the calculated classification disagreed with the snapshotted expectation --
   * excluding "Not Sure" cases, which are never scorable and so are never "disagreements" in this
   * sense (they're always {@code agrees = false} since the matcher can never calculate "Not
   * Sure," which would otherwise make every Not-Sure case look like a failure here).
   */
  @SuppressWarnings("unchecked")
  public static List<EvaluationResult> listDisagreements(Session dataSession, Evaluation evaluation) {
    Query query = dataSession.createQuery(
        "from EvaluationResult where evaluation = ? and agrees = false and expectedClassification <> ?"
            + " order by evaluationResultId");
    query.setParameter(0, evaluation);
    query.setParameter(1, org.immregistries.mismo.match.model.MatchItem.NOT_SURE);
    return query.list();
  }

  /**
   * The confusion matrix for {@code evaluation}, derived by aggregating {@code EvaluationResult}
   * rows (expected classification -&gt; calculated classification -&gt; count) rather than being
   * stored anywhere, so there is never a second place for this data to go stale.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Map<String, Long>> confusionMatrix(Session dataSession, Evaluation evaluation) {
    Query query = dataSession.createQuery(
        "select expectedClassification, calculatedClassification, count(*) from EvaluationResult"
            + " where evaluation = ? group by expectedClassification, calculatedClassification");
    query.setParameter(0, evaluation);
    List<Object[]> rows = query.list();
    Map<String, Map<String, Long>> matrix = new LinkedHashMap<String, Map<String, Long>>();
    for (Object[] row : rows) {
      String expected = (String) row[0];
      String calculated = (String) row[1];
      Long count = (Long) row[2];
      Map<String, Long> byCalculated = matrix.get(expected);
      if (byCalculated == null) {
        byCalculated = new LinkedHashMap<String, Long>();
        matrix.put(expected, byCalculated);
      }
      byCalculated.put(calculated, count);
    }
    return matrix;
  }

  /** One case-level pairing produced by {@link #compareEvaluations}: the same match item, scored by both runs. */
  public static final class EvaluationComparisonCase {
    private final EvaluationResult resultA;
    private final EvaluationResult resultB;

    EvaluationComparisonCase(EvaluationResult resultA, EvaluationResult resultB) {
      this.resultA = resultA;
      this.resultB = resultB;
    }

    public EvaluationResult getResultA() {
      return resultA;
    }

    public EvaluationResult getResultB() {
      return resultB;
    }
  }

  /**
   * Case-level Configuration-A-vs-B comparison result (database-changes-for-functional-model.md
   * §5's Configuration comparison): every case common to both evaluations, bucketed into the four
   * categories the functional model calls out. Improved/Regressed are the two the model
   * specifically names as the important analyst questions.
   */
  public static final class EvaluationComparison {
    private final List<EvaluationComparisonCase> improved = new ArrayList<EvaluationComparisonCase>();
    private final List<EvaluationComparisonCase> regressed = new ArrayList<EvaluationComparisonCase>();
    private final List<EvaluationComparisonCase> changed = new ArrayList<EvaluationComparisonCase>();
    private final List<EvaluationComparisonCase> unchanged = new ArrayList<EvaluationComparisonCase>();

    /** A disagreed, B agrees -- the candidate configuration fixed this case. */
    public List<EvaluationComparisonCase> getImproved() {
      return improved;
    }

    /** A agreed, B disagrees -- the candidate configuration broke this previously-correct case. */
    public List<EvaluationComparisonCase> getRegressed() {
      return regressed;
    }

    /** Both disagreed, but the calculated classification moved -- still wrong, but differently wrong. */
    public List<EvaluationComparisonCase> getChanged() {
      return changed;
    }

    public List<EvaluationComparisonCase> getUnchanged() {
      return unchanged;
    }
  }

  /**
   * Joins two evaluations' {@code EvaluationResult} rows on {@code match_item_id} and categorizes
   * every case common to both (database-changes-for-functional-model.md §5's Configuration
   * comparison -- needs no schema beyond {@code evaluation}/{@code evaluation_result}, purely a
   * query over two existing evaluations' results). A case present in only one of the two runs
   * (e.g. the Test Set changed between runs) is silently skipped -- there is nothing to compare it
   * against.
   */
  public static EvaluationComparison compareEvaluations(Session dataSession, Evaluation evaluationA,
      Evaluation evaluationB) {
    List<EvaluationResult> resultsA = listEvaluationResults(dataSession, evaluationA);
    List<EvaluationResult> resultsB = listEvaluationResults(dataSession, evaluationB);
    Map<Integer, EvaluationResult> resultsBByMatchItemId = new HashMap<Integer, EvaluationResult>();
    for (EvaluationResult resultB : resultsB) {
      resultsBByMatchItemId.put(resultB.getMatchItem().getMatchItemId(), resultB);
    }

    EvaluationComparison comparison = new EvaluationComparison();
    for (EvaluationResult resultA : resultsA) {
      EvaluationResult resultB = resultsBByMatchItemId.get(resultA.getMatchItem().getMatchItemId());
      if (resultB == null) {
        continue;
      }
      EvaluationComparisonCase comparisonCase = new EvaluationComparisonCase(resultA, resultB);
      if (!resultA.isAgrees() && resultB.isAgrees()) {
        comparison.getImproved().add(comparisonCase);
      } else if (resultA.isAgrees() && !resultB.isAgrees()) {
        comparison.getRegressed().add(comparisonCase);
      } else if (!resultA.isAgrees() && !resultB.isAgrees()
          && !resultA.getCalculatedClassification().equals(resultB.getCalculatedClassification())) {
        comparison.getChanged().add(comparisonCase);
      } else {
        comparison.getUnchanged().add(comparisonCase);
      }
    }
    return comparison;
  }
}
