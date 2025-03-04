package org.immregistries.mismo.trainer.model;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;

/**
 * A convenience class that supports scoring the matches. Currently this scoring
 * table is hard coded
 * but can be modified during run time.
 * <p>
 * These weights are used to "steer" the optimization towards a better fit.
 * Creatures will be receive
 * a total score that indicates how well their solution fits. Ideally a creature
 * would always
 * get positive scores and never negative scores.
 * 
 * @author Nathan Bunker
 *
 */
public class Scorer {
  private int[][] countTable = new int[3][3];
  /* Match */
  private static int[][] weights = { /* Should Match */ { 20, 0, -5 },
      /* Possible */ { -20, 10, 0 },
      /* Not Match */ { -40, -10, 10 } };

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
   * Returns an array, 3 x 3, of weights.
   * <table>
   * <tr>
   * <th></th>
   * <th>Matched [0]</th>
   * <th>Possible [1]</th>
   * <th>Not Matched [2]</th>
   * </tr>
   * <tr>
   * <th>Should Match [0]</th>
   * <td>20</td>
   * <td>-5</td>
   * <td>-20</td>
   * </tr>
   * <tr>
   * <th>Possible [1]</th>
   * <td>-5</td>
   * <td>20</td>
   * <td>-5</td>
   * </tr>
   * <tr>
   * <th>Not Match [2]</th>
   * <td>-10</td>
   * <td>-5</td>
   * <td>10</td>
   * </tr>
   * </table>
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
