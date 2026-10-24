package org.immregistries.mismo.trainer.runner;

import java.util.List;

import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.optimizer.engine.Scorer;
import org.immregistries.mismo.optimizer.engine.TestCaseLoader;
import org.immregistries.mismo.optimizer.protocol.Messages;
import org.immregistries.mismo.optimizer.protocol.Protocol;
import org.immregistries.mismo.optimizer.protocol.ScoringWeights;
import org.immregistries.mismo.trainer.model.Configuration;
import org.immregistries.mismo.trainer.model.OptimizationRun;

/**
 * Scores a run's candidate against that run's own frozen inputs (its Test Set snapshot and scoring
 * weights), so the numbers shown on the run page are exactly what the runner optimized for. Also
 * collapses the 3 x 3 table to the 2 x 2 "potential match" view the national measurement program
 * uses (docs/island-runner-implementation-plan.md D6, item 8).
 */
public final class CandidateScoring {

  private CandidateScoring() {
  }

  /** @param counts rows expected Match/Possible/Not, columns actual Match/Possible/Not */
  public record Result(int[][] counts, double score) {

    /** Expected Match or Possible (row 0+1) vs Not (row 2), by actual potential (col 0+1) vs Not (col 2). */
    public int[][] potentialMatchTable() {
      int[][] table = new int[2][2];
      for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
          table[i < 2 ? 0 : 1][j < 2 ? 0 : 1] += counts[i][j];
        }
      }
      return table;
    }
  }

  public static Result score(OptimizationRun run, Configuration candidate) {
    Messages.TestSetContent content = Protocol.gson().fromJson(run.getTestSetSnapshot().getContentJson(),
        Messages.TestSetContent.class);
    List<MatchItem> cases = TestCaseLoader.fromTestSetContent(content);
    Scorer scorer = new Scorer(ScoringWeights.fromJson(run.getScoringWeightsJson()));
    PatientCompare patientCompare = new PatientCompare(candidate.getConfigurationScript());
    for (MatchItem matchItem : cases) {
      patientCompare.setMatchItem(matchItem);
      scorer.registerMatch(matchItem, patientCompare);
    }
    return new Result(scorer.getCountTable(), scorer.getScore());
  }
}
