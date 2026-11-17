package org.immregistries.mismo.optimizer.engine;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.MatchItem;
import org.junit.Test;

public class CreatureCopyTest {
  private static List<MatchNode> nodes(PatientCompare compare) {
    List<MatchNode> nodes = new ArrayList<>();
    collect(compare.getMatch(), nodes);
    collect(compare.getNotMatch(), nodes);
    collect(compare.getTwin(), nodes);
    collect(compare.getMissing(), nodes);
    return nodes;
  }

  private static void collect(MatchNode node, List<MatchNode> nodes) {
    nodes.add(node);
    if (node instanceof AggregateMatchNode aggregate) {
      for (MatchNode child : aggregate.getMatchNodeList()) collect(child, nodes);
    }
  }

  private static void assertParameters(Creature expected, Creature actual) {
    List<MatchNode> a = nodes(expected.getPatientCompare());
    List<MatchNode> b = nodes(actual.getPatientCompare());
    assertEquals(a.size(), b.size());
    for (int i = 0; i < a.size(); i++) {
      assertNotSame(a.get(i), b.get(i));
      assertEquals(a.get(i).getMatchNameFull(), b.get(i).getMatchNameFull());
      assertEquals(a.get(i).isEnabled(), b.get(i).isEnabled());
      assertEquals(a.get(i).getMinScore(), b.get(i).getMinScore(), 0.0);
      assertEquals(a.get(i).getMaxScore(), b.get(i).getMaxScore(), 0.0);
    }
  }

  @Test
  public void cloneCopiesCorrespondingNodesAndRoundTripsScores() throws Exception {
    World world = new World(1, "copy-test", "island");
    world.setMatchItemList(TestCases.sample());
    Creature parent = world.getCreatures()[0];
    // Enabled roots with distinct child bounds and disabled groups AND leaves.
    List<MatchNode> original = nodes(parent.getPatientCompare());
    for (int i = 0; i < original.size(); i++) {
      MatchNode node = original.get(i);
      if (node.isEnabled()) {
        node.setMinScore(0.1 + (i % 7) * 0.03);
        node.setMaxScore(0.6 + (i % 5) * 0.04);
      }
    }
    assertTrue(original.stream().anyMatch(n -> n instanceof AggregateMatchNode && !n.isEnabled()));
    assertTrue(original.stream().anyMatch(n -> !(n instanceof AggregateMatchNode) && !n.isEnabled()));
    // Leave the parent's script stale: copying must use its live node values.
    Creature copy = new Creature(7, parent);
    assertParameters(parent, copy);
    assertEquals(7, copy.getGeneration());
    assertFalse(copy.isScored());
    parent.score();
    copy.score();
    assertEquals(parent.getScore(), copy.getScore(), 0.0);
    assertArrayEquals(parent.getScorer().getCountTable(), copy.getScorer().getCountTable());
    for (MatchItem item : world.getMatchItemList()) {
      parent.getPatientCompare().setMatchItem(item);
      copy.getPatientCompare().setMatchItem(item);
      assertEquals(parent.getPatientCompare().getResult(), copy.getPatientCompare().getResult());
    }
    Creature reloaded = new Creature(world, copy.makeScript());
    assertParameters(copy, reloaded);
    reloaded.score();
    assertEquals(copy.getScore(), reloaded.getScore(), 0.0);
    assertArrayEquals(copy.getScorer().getCountTable(), reloaded.getScorer().getCountTable());
    assertArrayEquals(world.getScoringWeights(), reloaded.getPatientCompare().getConfiguration().getScoringWeights());
    Creature before = new Creature(world, parent.makeScript());
    copy.tweak();
    assertParameters(before, parent);
  }

  private static void assertDisabledScope(List<MatchNode> seed, Creature candidate) {
    List<MatchNode> actual = nodes(candidate.getPatientCompare());
    assertEquals(seed.size(), actual.size());
    for (int i = 0; i < seed.size(); i++) {
      if (!seed.get(i).isEnabled()) assertFalse(seed.get(i).getMatchNameFull(), actual.get(i).isEnabled());
    }
  }

  @Test
  public void incompatibleDefinitionsAreRejectedBeforeAnyValuesAreCopied() {
    PatientCompare source = new PatientCompare();
    PatientCompare target = new PatientCompare();
    source.getMatch().setMinScore(0.123);
    double originalMinimum = target.getMatch().getMinScore();
    // A mismatch in the last network must not partially overwrite earlier networks.
    target.getMissing().getMatchNodeList().get(0).setFieldName("incompatible-field");
    try {
      MatchTreeCopy.copy(source, target);
      fail("Expected incompatible detector rejection");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("incompatible"));
    }
    assertEquals(originalMinimum, target.getMatch().getMinScore(), 0.0);
  }

  @Test
  public void incompatibleTopologyIsRejected() {
    PatientCompare source = new PatientCompare();
    PatientCompare target = new PatientCompare();
    target.getTwin().getMatchNodeList().remove(0);
    try {
      MatchTreeCopy.copy(source, target);
      fail("Expected incompatible topology rejection");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("incompatible"));
    }
  }

  @Test
  public void variationAndGenerationsPreserveSeedDisabledNodes() throws Exception {
    World world = new World(12, "scope-test", "island");
    world.setMatchItemList(TestCases.sample());
    List<MatchNode> seed = nodes(new PatientCompare(world.getCreatures()[0].makeScript()));
    for (Creature creature : world.getCreatures()) assertDisabledScope(seed, creature);
    Creature copy = new Creature(1, world.getCreatures()[0]);
    copy.tweak();
    assertDisabledScope(seed, copy);
    copy.mutate();
    assertDisabledScope(seed, copy);
    Creature child = new Creature(2, copy, world.getCreatures()[1]);
    assertDisabledScope(seed, child);
    world.setScoringThreads(2);
    try {
      for (int generation = 0; generation < 3; generation++) {
        world.scoreAndSort();
        world.makeNewGeneration();
        for (Creature creature : world.getCreatures()) assertDisabledScope(seed, creature);
      }
    } finally {
      world.shutdownScoringPool();
    }
  }
}
