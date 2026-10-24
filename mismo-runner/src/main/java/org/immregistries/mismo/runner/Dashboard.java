package org.immregistries.mismo.runner;

import java.io.PrintStream;

import org.immregistries.mismo.optimizer.engine.World;
import org.immregistries.mismo.optimizer.protocol.Messages;

/**
 * The roll-and-scroll status block printed while a run is going (docs/centrally-managed-local-island-runner.md
 * §7). Pressing Enter prints it again; nothing is redrawn in place.
 */
final class Dashboard {

  private Dashboard() {
  }

  static void print(PrintStream out, IdentityStore.Identity identity, RunSession session) {
    Messages.Assignment assignment = session.assignment();
    World world = session.world();
    RunSession.Best best = session.best();
    out.println();
    out.println("Mismo Runner " + RunnerMain.VERSION);
    out.println("Runner:      " + identity.runnerName() + " (" + identity.organizationName() + ")");
    out.println("Run:         " + session.runId());
    out.println("Assignment:  " + assignment.name() + ", revision " + assignment.revision());
    out.println("Test Set:    " + assignment.testSet().label() + " (snapshot " + assignment.testSet().snapshotId()
        + ", " + String.format("%,d", session.caseCount()) + " cases)");
    out.println("Profile:     " + assignment.scoringProfile().name() + " (" + assignment.scoringProfile().purpose()
        + ")");
    out.println("Connection:  " + session.connection());
    out.println("Last report: " + ago(session.lastReportMillis()));
    out.println();
    out.println("Generation:  " + String.format("%,d", world.getGeneration()));
    out.println("Rate:        " + String.format("%,.1f", world.getScoreRate()) + " candidates/second");
    out.println("Best score:  " + (best == null ? "(scoring first generation)"
        : String.format("%.2f%%", best.score() * 100.0)));
    out.println("Status:      " + world.getLastMessage());
    out.println("Running for: " + ago(session.startedMillis()).replace(" ago", ""));
    out.println();
  }

  static String ago(long millis) {
    long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
    if (seconds < 60) {
      return seconds + " seconds ago";
    }
    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
    }
    long hours = minutes / 60;
    return hours + (hours == 1 ? " hour " : " hours ") + (minutes % 60) + " minutes ago";
  }
}
