package org.immregistries.mismo.optimizer.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.immregistries.mismo.match.model.MatchItem;
import org.junit.Test;

public class ScorerTest {

  private static final int[][] WEIGHTS_A = { { 10, 0, -10 }, { 0, 10, 0 }, { -10, 0, 10 } };
  private static final int[][] WEIGHTS_B = { { 4, 4, -4 }, { 4, 4, -4 }, { -4, -4, 4 } };

  private static MatchItem item(String expected, String actual) {
    MatchItem matchItem = new MatchItem();
    matchItem.setExpectStatus(expected);
    matchItem.setActualStatus(actual);
    return matchItem;
  }

  private static void registerSample(Scorer scorer) {
    scorer.registerMatch(item(MatchItem.MATCH, MatchItem.MATCH));
    scorer.registerMatch(item(MatchItem.MATCH, MatchItem.POSSIBLE_MATCH));
    scorer.registerMatch(item(MatchItem.POSSIBLE_MATCH, MatchItem.NOT_A_MATCH));
    scorer.registerMatch(item(MatchItem.NOT_A_MATCH, MatchItem.NOT_A_MATCH));
  }

  /** Before D5, constructing a second Scorer overwrote a static weights array shared by all. */
  @Test
  public void scorersWithDifferentWeightsDoNotInterfere() {
    Scorer a = new Scorer(WEIGHTS_A);
    Scorer b = new Scorer(WEIGHTS_B);
    registerSample(a);
    registerSample(b);

    // A: (10 + 0 + 0 + 10) / (10 + 10 + 10 + 10)
    assertEquals(20.0 / 40.0, a.getScore(), 1e-9);
    // B: (4 + 4 - 4 + 4) / (4 + 4 + 4 + 4)
    assertEquals(8.0 / 16.0, b.getScore(), 1e-9);

    // Scoring a again after b was built still uses a's weights.
    Scorer a2 = new Scorer(WEIGHTS_A);
    registerSample(a2);
    assertEquals(a.getScore(), a2.getScore(), 1e-9);
  }

  @Test
  public void weightsAreCopiedOnConstruction() {
    int[][] weights = { { 10, 0, -10 }, { 0, 10, 0 }, { -10, 0, 10 } };
    Scorer scorer = new Scorer(weights);
    weights[0][0] = 1000;
    registerSample(scorer);
    assertEquals(20.0 / 40.0, scorer.getScore(), 1e-9);
  }

  @Test
  public void registerMatchIgnoresUnscoreableStatuses() {
    Scorer scorer = new Scorer(WEIGHTS_A);
    scorer.registerMatch(item(MatchItem.RESEARCH, MatchItem.NOT_A_MATCH));
    scorer.registerMatch(item(MatchItem.NOT_SURE, MatchItem.MATCH));
    scorer.registerMatch(item("", MatchItem.MATCH));
    scorer.registerMatch(item(null, MatchItem.MATCH));
    assertArrayEquals(new int[3][3], scorer.getCountTable());
    assertEquals(0.0, scorer.getScore(), 0.0);
  }

  /** The canonical measurement matrix; see docs/optimization-objectives-and-scoring-profiles.md. */
  @Test
  public void defaultWeightsAreTheCanonicalMeasurementMatrix() {
    int[][] expected = { { 12, 10, -10 }, { 8, 10, -8 }, { -10, -8, 10 } };
    assertArrayEquals(expected, Scorer.defaultWeights());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonSquareWeights() {
    new Scorer(new int[][] { { 1, 2, 3 }, { 1, 2 }, { 1, 2, 3 } });
  }
}
