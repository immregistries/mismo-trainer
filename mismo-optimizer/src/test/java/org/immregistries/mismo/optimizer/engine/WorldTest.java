package org.immregistries.mismo.optimizer.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.immregistries.mismo.match.model.MatchItem;
import org.junit.Test;

public class WorldTest {

  private static World world(int size, int[][] weights) throws Exception {
    World world = new World(size, "test-world", "test-island", null, weights);
    world.setMatchItemList(TestCases.sample());
    return world;
  }

  @Test
  public void sampleLoadsWithEmptyDefaultsForMissingFields() throws Exception {
    List<MatchItem> cases = TestCases.sample();
    assertEquals(60, cases.size());
    for (MatchItem matchItem : cases) {
      assertTrue(matchItem.isExpectedStatusSet());
      assertEquals("", matchItem.getDescription());
    }
  }

  @Test
  public void fixedCreatureScoresTheSameEveryTime() throws Exception {
    World world = world(4, null);
    String script = world.getCreatures()[0].makeScript();

    Creature creature = new Creature(world, script);
    creature.score();
    double first = creature.getScore();
    int[][] firstCounts = creature.getScorer().getCountTable();
    for (int i = 0; i < 3; i++) {
      creature.score();
      assertEquals(first, creature.getScore(), 0.0);
      assertArrayEquals(firstCounts, creature.getScorer().getCountTable());
    }

    Creature rebuilt = new Creature(world, script);
    rebuilt.score();
    assertEquals(first, rebuilt.getScore(), 0.0);
  }

  @Test
  public void creaturesUseTheirWorldsWeights() throws Exception {
    int[][] allPositive = { { 1, 1, 1 }, { 1, 1, 1 }, { 1, 1, 1 } };
    World measurement = world(4, null);
    World lenient = world(4, allPositive);
    assertArrayEquals(Scorer.defaultWeights(), measurement.getScoringWeights());

    String script = measurement.getCreatures()[0].makeScript();
    Creature a = new Creature(measurement, script);
    Creature b = new Creature(lenient, script);
    a.score();
    b.score();
    Creature aAgain = new Creature(measurement, script);
    aAgain.score();

    // Every case scores 1 of a possible 1 under all-ones weights.
    assertEquals(1.0, b.getScore(), 0.0);
    assertNotEquals(1.0, a.getScore(), 0.0);
    assertEquals(a.getScore(), aAgain.getScore(), 0.0);
  }

  /**
   * Guards the old {@code lowerCut ^ 2} (XOR) bug (docs/known-issues.md): with 100 creatures,
   * lowerCut is 7, so 100 - 7 * 7 = 51 is the first replaced slot and up to 49 creatures are
   * replaced. XOR gave 100 - (7 ^ 2) = 95, so only 5 were replaced.
   */
  @Test
  public void newGenerationReplacesTheExpectedSlots() throws Exception {
    int size = 100;
    int lowerCut = (int) Math.sqrt(0.5 * size);
    int lowerCutStart = size - lowerCut * lowerCut;
    assertEquals(51, lowerCutStart);

    World world = world(size, null);
    world.scoreAndSort();
    assertTrue("scoreRate should be measured below one second", world.getScoreRate() > 0);

    Creature[] before = world.getCreatures().clone();
    world.makeNewGeneration();
    Creature[] after = world.getCreatures();

    for (int i = 0; i < lowerCutStart; i++) {
      assertSame("survivor at " + i + " should be kept", before[i], after[i]);
    }
    int replaced = 0;
    for (int i = lowerCutStart; i < size; i++) {
      if (before[i] != after[i]) {
        replaced++;
        assertEquals(1, after[i].getGeneration());
      }
    }
    // A baby is discarded only if its DNA duplicates an existing creature, which is rare.
    assertTrue("replaced " + replaced + " of " + (size - lowerCutStart), replaced >= 45);
  }
}
