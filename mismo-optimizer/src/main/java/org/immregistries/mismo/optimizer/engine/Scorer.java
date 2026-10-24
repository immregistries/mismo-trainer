package org.immregistries.mismo.optimizer.engine;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;

/**
 * A convenience class that supports scoring the matches. These weights are used to "steer" the
 * optimization towards a better fit. Creatures will receive a total score that indicates how well
 * their solution fits. Ideally a creature would always get positive scores and never negative
 * scores.
 * <p>
 * <b>Read docs/optimization-objectives-and-scoring-profiles.md before changing the weights or
 * what they reward.</b> The default matrix is a deliberate national measurement objective that
 * treats Match and Possible Match as one "potential match" signal; it is not a production
 * calibration matrix.
 * <p>
 * Each {@code Scorer} holds its own copy of the weights, so concurrent scorers (different
 * creatures, different web requests) never affect each other. The default weights come from the
 * {@code scoringWeights} block of mismo-match's {@code mismo-matcher.yml}, read through
 * {@link #defaultWeights()} rather than a separate hardcoded copy here -- see
 * docs/known-issues.md and docs/optimization-and-islands.md §3 for the history of the four
 * previously-disagreeing copies of these numbers.
 *
 * @author Nathan Bunker
 *
 */
public class Scorer {
  private int[][] countTable = new int[3][3];
  private final int[][] weights;

  /**
   * @param weights
   *          3 x 3 array: rows are the expected status (Should Match/Possible/Not Match), columns
   *          are the actual status (Matched/Possible/Not Matched). The array is copied.
   */
  public Scorer(int[][] weights) {
    if (weights == null || weights.length != 3) {
      throw new IllegalArgumentException("Weights must be a 3 x 3 array");
    }
    this.weights = new int[3][];
    for (int i = 0; i < 3; i++) {
      if (weights[i] == null || weights[i].length != 3) {
        throw new IllegalArgumentException("Weights must be a 3 x 3 array");
      }
      this.weights[i] = weights[i].clone();
    }
  }

  /**
   * Builds a default {@code Configuration} (no explicit script) purely to read its
   * {@code scoringWeights} -- this is the same defaulting path {@code World}/{@code Creature} use
   * when no seed script is available, so this class never maintains its own separate copy of the
   * default numbers.
   *
   * @return a new 3 x 3 array of the default weights
   */
  public static int[][] defaultWeights() {
    org.immregistries.mismo.match.model.Configuration configuration =
        new org.immregistries.mismo.match.model.Configuration();
    configuration.setup();
    return configuration.getScoringWeights();
  }

  /**
   * Increments the score counts based on the expected answer in the matchTestCase
   * and the actual answer in the patientCompare. Cases whose expected status isn't scoreable
   * (Research, Not Sure, blank) are ignored.
   *
   * @param matchItem      the test cases containing the expected answer
   * @param patientCompare the actual data containing the actual answer
   */
  public void registerMatch(MatchItem matchItem, PatientCompare patientCompare) {
    if (!matchItem.isExpectedStatusSet()) {
      return;
    }
    countTable[statusIndex(matchItem.getExpectStatus())][statusIndex(patientCompare.getResult())]++;
  }

  /**
   * Same as {@link #registerMatch(MatchItem, PatientCompare)} but reads the actual answer from
   * {@code matchItem.getActualStatus()}.
   */
  public void registerMatch(MatchItem matchItem) {
    if (!matchItem.isExpectedStatusSet()) {
      return;
    }
    countTable[statusIndex(matchItem.getExpectStatus())][statusIndex(matchItem.getActualStatus())]++;
  }

  private static int statusIndex(String status) {
    return MatchItem.MATCH.equals(status) ? 0 : (MatchItem.POSSIBLE_MATCH.equals(status) ? 1 : 2);
  }

  /**
   * The count table holds the number of times a particular expected/actual
   * combination occurs. This will
   * be used in the end to generate an overall score. This table is helpful to see
   * how well a solution is fitting.
   *
   * @return an array, 3 x 3
   */
  public int[][] getCountTable() {
    return countTable;
  }

  /**
   * Calculates and returns the overall score.
   *
   * @return
   */
  public double getScore() {
    int totalScorePossible = 0;
    int actualScore = 0;
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        actualScore += countTable[i][j] * weights[i][j];
        totalScorePossible += countTable[i][j] * weights[i][i];
      }
    }
    if (totalScorePossible == 0) {
      return 0;
    }
    return (double) actualScore / (double) totalScorePossible;

  }
}
