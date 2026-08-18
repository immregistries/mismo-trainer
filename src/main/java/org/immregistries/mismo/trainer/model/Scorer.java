package org.immregistries.mismo.trainer.model;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;

/**
 * A convenience class that supports scoring the matches. These weights are used to "steer" the
 * optimization towards a better fit. Creatures will receive a total score that indicates how well
 * their solution fits. Ideally a creature would always get positive scores and never negative
 * scores.
 * <p>
 * The default weights below are read from the loaded {@code Configuration} (the same 3x3
 * {@code scoringWeights} block documented in {@code Configuration.yml}, mismo-match's canonical
 * source) rather than a separate hardcoded copy here -- see docs/known-issues.md and
 * docs/optimization-and-islands.md §3 for the history of the four previously-disagreeing copies
 * of these numbers.
 *
 * @author Nathan Bunker
 *
 */
public class Scorer {
  private int[][] countTable = new int[3][3];
  private static int[][] weights = defaultWeights();

  public Scorer() {
    // default
  }

  public Scorer(int[][] weights) {
    Scorer.weights = weights;
    if (weights.length != 3 || weights[0].length != 3) {
      throw new IllegalArgumentException("Weights must be a 3 x 3 array");
    }
  }

  /**
   * Builds a default {@code Configuration} (no explicit script) purely to read its
   * {@code scoringWeights} -- this is the same defaulting path {@code World}/{@code Creature} use
   * when no seed script is available, so this class never maintains its own separate copy of the
   * default numbers.
   */
  private static int[][] defaultWeights() {
    org.immregistries.mismo.match.model.Configuration configuration =
        new org.immregistries.mismo.match.model.Configuration();
    configuration.setup();
    return configuration.getScoringWeights();
  }

  /**
   * Returns an array, 3 x 3, of weights: rows are the expected status (Should Match/Possible/Not
   * Match), columns are the actual status (Matched/Possible/Not Matched). See
   * {@code Configuration.yml}'s {@code scoringWeights} block for the canonical default values.
   *
   * @return
   */
  public static int[][] getWeights() {
    return weights;
  }

  /**
   * Increments the score counts based on the expected answer in the matchTestCase
   * and the actual answer in the patientCompare.
   * 
   * @param matchItem      the test cases containing the expected answer
   * @param patientCompare the actual data containing the actual answer
   */
  public void registerMatch(MatchItem matchItem, PatientCompare patientCompare) {
    int i = matchItem.getExpectStatus().equals(MatchItem.MATCH) ? 0
        : (matchItem.getExpectStatus().equals(
            MatchItem.POSSIBLE_MATCH) ? 1 : 2);
    int j = patientCompare.getResult().equals(MatchItem.MATCH) ? 0
        : (patientCompare.getResult().equals(MatchItem.POSSIBLE_MATCH) ? 1 : 2);
    countTable[i][j]++;
  }

  public void registerMatch(MatchItem matchItem) {
    int i = matchItem.getExpectStatus().equals(MatchItem.MATCH) ? 0
        : (matchItem.getExpectStatus().equals(
            MatchItem.POSSIBLE_MATCH) ? 1 : 2);
    int j = matchItem.getActualStatus().equals(MatchItem.MATCH) ? 0
        : (matchItem.getActualStatus().equals(MatchItem.POSSIBLE_MATCH) ? 1 : 2);
    countTable[i][j]++;
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
