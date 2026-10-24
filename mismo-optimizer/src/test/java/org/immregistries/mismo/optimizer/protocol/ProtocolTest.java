package org.immregistries.mismo.optimizer.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.optimizer.engine.Scorer;
import org.immregistries.mismo.optimizer.engine.TestCaseLoader;
import org.junit.Test;

public class ProtocolTest {

  @Test
  public void sha256MatchesKnownVector() {
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashes.sha256Hex("abc"));
  }

  @Test
  public void testSetContentRoundTripsThroughJson() {
    List<Messages.TestCase> cases = new ArrayList<Messages.TestCase>();
    cases.add(new Messages.TestCase("S-1", null, MatchItem.MATCH, "nameFirst=Ann,nameLast=Lee,",
        "nameFirst=Ann,nameLast=Lee,"));
    cases.add(new Messages.TestCase("D-1", "different", MatchItem.NOT_A_MATCH, "nameFirst=Bo,", "nameFirst=Cy,"));
    Messages.TestSetContent content = new Messages.TestSetContent(Protocol.TEST_SET_FORMAT_VERSION, 12, "AIRA-D",
        cases);

    String json = Protocol.gson().toJson(content);
    assertFalse("'=' should not be escaped", json.contains("\\u003d"));
    Messages.TestSetContent back = Protocol.gson().fromJson(json, Messages.TestSetContent.class);
    assertEquals(content, back);
    // Serializing again gives identical bytes, so a server-side hash stays stable.
    assertEquals(Hashes.sha256Hex(json), Hashes.sha256Hex(Protocol.gson().toJson(back)));

    List<MatchItem> runtime = TestCaseLoader.fromTestSetContent(back);
    assertEquals(2, runtime.size());
    assertEquals("", runtime.get(0).getDescription());
    assertEquals("Lee", runtime.get(0).getPatientA().getValue("nameLast"));
  }

  @Test
  public void scoringWeightsRoundTrip() {
    int[][] weights = Scorer.defaultWeights();
    String json = ScoringWeights.toJson(weights);
    assertTrue(json.startsWith("{\"shouldMatch_Matches\":12,"));
    assertArrayEquals(weights, ScoringWeights.fromJson(json));
  }

  @Test
  public void stampReplacesOnlyTheWeights() {
    org.immregistries.mismo.match.model.Configuration seed = new org.immregistries.mismo.match.model.Configuration();
    seed.setup();
    String script = seed.getConfigurationScript();
    int[][] steered = { { 1, 2, 3 }, { 4, 5, 6 }, { 7, 8, 9 } };

    String stamped = ScoringWeights.stamp(script, steered);
    assertArrayEquals(steered, ScoringWeights.readFrom(stamped));
    assertEquals(script.substring(script.indexOf("\nMatch:")), stamped.substring(stamped.indexOf("\nMatch:")));
  }
}
