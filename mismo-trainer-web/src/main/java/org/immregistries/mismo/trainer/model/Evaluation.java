package org.immregistries.mismo.trainer.model;

import java.util.Date;

/**
 * A persisted, durable record of scoring one {@link MatchSet} against one {@link Configuration}
 * (v2-roadmap.md §11; database-changes-for-functional-model.md §5). Previously this information
 * was computed live by {@code TestMatchingServlet} and discarded after rendering -- this makes it
 * a permanent, queryable row. Running the same Test Set + Configuration combination again creates
 * a new row, it does not overwrite a prior one.
 *
 * <p>{@link #getOrganization()} is the <em>running</em> user's organization -- not necessarily the
 * same as {@link #getMatchSet()}'s or {@link #getConfiguration()}'s owning organization, since
 * either can be a readable template row owned by another organization.
 *
 * <p>The confusion matrix is deliberately not stored on this row -- it's derived from
 * {@link EvaluationResult} by aggregation (see {@code OrgScope#confusionMatrix}), avoiding a
 * second place for the same data to go stale.
 */
public class Evaluation {

  private int evaluationId;
  private Organization organization;
  private MatchSet matchSet;
  private Configuration configuration;
  private User runByUser;
  private Date runAt;
  private int totalCases;
  private int scorableCases;
  private int notSureCases;
  private int agreeCount;
  private int disagreeCount;
  private Double score;

  public int getEvaluationId() {
    return evaluationId;
  }

  public void setEvaluationId(int evaluationId) {
    this.evaluationId = evaluationId;
  }

  public Organization getOrganization() {
    return organization;
  }

  public void setOrganization(Organization organization) {
    this.organization = organization;
  }

  public MatchSet getMatchSet() {
    return matchSet;
  }

  public void setMatchSet(MatchSet matchSet) {
    this.matchSet = matchSet;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  /** Nullable -- who ran this evaluation, if known. */
  public User getRunByUser() {
    return runByUser;
  }

  public void setRunByUser(User runByUser) {
    this.runByUser = runByUser;
  }

  public Date getRunAt() {
    return runAt;
  }

  public void setRunAt(Date runAt) {
    this.runAt = runAt;
  }

  /** Every {@code match_item} in {@link #getMatchSet()} at the time this evaluation ran. */
  public int getTotalCases() {
    return totalCases;
  }

  public void setTotalCases(int totalCases) {
    this.totalCases = totalCases;
  }

  /** {@link #getTotalCases()} minus {@link #getNotSureCases()} -- the cases that count toward scoring. */
  public int getScorableCases() {
    return scorableCases;
  }

  public void setScorableCases(int scorableCases) {
    this.scorableCases = scorableCases;
  }

  /**
   * Cases whose snapshotted expected classification was "Not Sure" -- a valid analyst
   * classification, but not scorable, so tracked separately rather than folded into
   * {@link #getDisagreeCount()}.
   */
  public int getNotSureCases() {
    return notSureCases;
  }

  public void setNotSureCases(int notSureCases) {
    this.notSureCases = notSureCases;
  }

  public int getAgreeCount() {
    return agreeCount;
  }

  public void setAgreeCount(int agreeCount) {
    this.agreeCount = agreeCount;
  }

  public int getDisagreeCount() {
    return disagreeCount;
  }

  public void setDisagreeCount(int disagreeCount) {
    this.disagreeCount = disagreeCount;
  }

  /** Overall weighted score (0.0-1.0), or {@code null} if there were no scorable cases. */
  public Double getScore() {
    return score;
  }

  public void setScore(Double score) {
    this.score = score;
  }
}
