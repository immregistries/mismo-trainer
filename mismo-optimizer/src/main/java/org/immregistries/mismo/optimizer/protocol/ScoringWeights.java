package org.immregistries.mismo.optimizer.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

import org.immregistries.mismo.match.model.Configuration;

import com.google.gson.reflect.TypeToken;

/**
 * Converts the 3 x 3 scoring weights between {@code int[][]} (what {@code Scorer} uses) and the
 * named form a configuration script's {@code scoringWeights} block uses, which is also how
 * {@code scoring_profile.scoring_weights_json} and the API store them.
 */
public final class ScoringWeights {

  /** Row-major: rows are expected Match/Possible/Not Match, columns actual Match/Possible/Not Match. */
  private static final String[][] KEYS = {
      { Configuration.SHOULD_MATCH_MATCHES, Configuration.SHOULD_MATCH_POSSIBLE, Configuration.SHOULD_MATCH_NO_MATCH },
      { Configuration.SHOULD_POSSIBLE_MATCHES, Configuration.SHOULD_POSSIBLE_POSSIBLE,
          Configuration.SHOULD_POSSIBLE_NO_MATCH },
      { Configuration.SHOULD_NO_MATCH_MATCHES, Configuration.SHOULD_NO_MATCH_POSSIBLE,
          Configuration.SHOULD_NO_MATCH_NO_MATCH } };

  private ScoringWeights() {
  }

  public static Map<String, Integer> toMap(int[][] weights) {
    Map<String, Integer> map = new LinkedHashMap<String, Integer>();
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        map.put(KEYS[i][j], weights[i][j]);
      }
    }
    return map;
  }

  /** @throws IllegalArgumentException if any of the nine keys is missing */
  public static int[][] fromMap(Map<String, Integer> map) {
    int[][] weights = new int[3][3];
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        Integer value = map == null ? null : map.get(KEYS[i][j]);
        if (value == null) {
          throw new IllegalArgumentException("Scoring weights are missing " + KEYS[i][j]);
        }
        weights[i][j] = value;
      }
    }
    return weights;
  }

  public static String toJson(int[][] weights) {
    return Protocol.gson().toJson(toMap(weights));
  }

  public static int[][] fromJson(String json) {
    Map<String, Integer> map = Protocol.gson().fromJson(json, new TypeToken<Map<String, Integer>>() {
    }.getType());
    return fromMap(map);
  }

  public static boolean same(int[][] a, int[][] b) {
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        if (a[i][j] != b[i][j]) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Returns {@code configurationScript} rewritten with {@code weights} as its {@code scoringWeights}
   * block, so a configuration records the fitness function it was trained under
   * (docs/island-runner-implementation-plan.md D6, item 5). Everything else is kept.
   */
  public static String stamp(String configurationScript, int[][] weights) {
    Configuration configuration = new Configuration(configurationScript);
    int[][] copy = new int[3][];
    for (int i = 0; i < 3; i++) {
      copy[i] = weights[i].clone();
    }
    configuration.setScoringWeights(copy);
    configuration.createConfigurationScript();
    return configuration.getConfigurationScript();
  }

  /** @return the weights embedded in {@code configurationScript} */
  public static int[][] readFrom(String configurationScript) {
    return new Configuration(configurationScript).getScoringWeights();
  }

  /** Three-line table for console and page display. */
  public static String format(int[][] weights) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%-16s %8s %8s %8s%n", "", "Match", "Possible", "No Match"));
    String[] rows = { "Should Match", "Should Possible", "Should Not Match" };
    for (int i = 0; i < 3; i++) {
      sb.append(String.format("%-16s %8d %8d %8d%n", rows[i], weights[i][0], weights[i][1], weights[i][2]));
    }
    return sb.toString();
  }
}
