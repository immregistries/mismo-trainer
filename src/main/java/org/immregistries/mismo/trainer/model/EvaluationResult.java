package org.immregistries.mismo.trainer.model;

/**
 * One scored {@link MatchItem} within an {@link Evaluation} run (database-changes-for-functional-
 * model.md §5). Every case in the evaluated {@code match_set} gets a row, including ones whose
 * expected classification is "Not Sure" -- those are just excluded from the parent
 * {@link Evaluation}'s scorable/agree/disagree/score aggregates.
 *
 * <p>{@link #getExpectedClassification()} is a one-time snapshot taken when the evaluation ran --
 * it never re-reads {@link MatchItem#getExpectStatus()} later, so this row can't silently drift if
 * the case is reclassified after the fact.
 */
public class EvaluationResult {

  private int evaluationResultId;
  private Evaluation evaluation;
  private MatchItem matchItem;
  private String expectedClassification;
  private String calculatedClassification;
  private String signature;
  private boolean agrees;

  public int getEvaluationResultId() {
    return evaluationResultId;
  }

  public void setEvaluationResultId(int evaluationResultId) {
    this.evaluationResultId = evaluationResultId;
  }

  public Evaluation getEvaluation() {
    return evaluation;
  }

  public void setEvaluation(Evaluation evaluation) {
    this.evaluation = evaluation;
  }

  public MatchItem getMatchItem() {
    return matchItem;
  }

  public void setMatchItem(MatchItem matchItem) {
    this.matchItem = matchItem;
  }

  /** Snapshot of {@code match_item.expect_status} taken at evaluation run time -- never re-read later. */
  public String getExpectedClassification() {
    return expectedClassification;
  }

  public void setExpectedClassification(String expectedClassification) {
    this.expectedClassification = expectedClassification;
  }

  /** What {@code PatientCompare} actually computed for this pair against the evaluated configuration. */
  public String getCalculatedClassification() {
    return calculatedClassification;
  }

  public void setCalculatedClassification(String calculatedClassification) {
    this.calculatedClassification = calculatedClassification;
  }

  /** The match signature produced when this case was scored, or {@code null} if unavailable. */
  public String getSignature() {
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

  /** {@code true} if {@link #getExpectedClassification()} equals {@link #getCalculatedClassification()}. */
  public boolean isAgrees() {
    return agrees;
  }

  public void setAgrees(boolean agrees) {
    this.agrees = agrees;
  }
}
