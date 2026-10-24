package org.immregistries.mismo.optimizer.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.immregistries.mismo.match.model.MatchItem;

/** Loads the 60-case sample (the first 60 cases of legacy-test-data/AIRA-D.txt). */
final class TestCases {

  private TestCases() {
  }

  static List<MatchItem> sample() throws IOException {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(
        TestCases.class.getResourceAsStream("/sample-test-cases.txt"), StandardCharsets.UTF_8))) {
      return TestCaseLoader.readSourceFile(in);
    }
  }
}
