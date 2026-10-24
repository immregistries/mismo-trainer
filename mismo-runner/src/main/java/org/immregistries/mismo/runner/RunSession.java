package org.immregistries.mismo.runner;

import java.util.List;

import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.optimizer.engine.Creature;
import org.immregistries.mismo.optimizer.engine.World;
import org.immregistries.mismo.optimizer.protocol.Hashes;
import org.immregistries.mismo.optimizer.protocol.Messages;

/**
 * One local optimization run: the {@link World} thread evolving the population, and a reporter
 * thread sending progress every minute and the best candidate when it changes (at most every five
 * minutes), plus immediately on {@link #sync} and {@link #stop}. Only the latest progress and
 * candidate are ever pending, so an outage doesn't build a queue. The run's inputs never change
 * after it starts (docs/centrally-managed-local-island-runner.md §5).
 */
final class RunSession {

  static final long PROGRESS_INTERVAL_MILLIS = 60_000L;
  static final long CANDIDATE_INTERVAL_MILLIS = 5 * 60_000L;
  private static final long REPORTER_TICK_MILLIS = 5_000L;
  private static final long FIRST_RETRY_MILLIS = 10_000L;
  private static final long MAX_RETRY_MILLIS = 5 * 60_000L;

  /** The best creature at one moment, captured so its script and score agree. */
  record Best(int generation, double score, String script, String sha256) {
  }

  private final ApiClient api;
  private final Messages.Assignment assignment;
  private final int runId;
  private final int[][] weights;
  private final int caseCount;
  private final World world;
  private final Thread reporter;
  private final long startedMillis = System.currentTimeMillis();

  private volatile boolean reporting = true;
  private volatile boolean serverAcceptsReports = true;
  private volatile String connection = "Connected";
  private volatile long lastReportMillis;
  private long lastProgressMillis;
  private long lastCandidateMillis;
  private String lastUploadedSha256;
  private long retryDelayMillis;
  private long nextAttemptMillis;

  RunSession(ApiClient api, Messages.Assignment assignment, int runId, String runnerName, String seedScript,
      int[][] weights, List<MatchItem> cases) {
    this.api = api;
    this.assignment = assignment;
    this.runId = runId;
    this.weights = weights;
    this.caseCount = cases.size();
    this.world = new World(assignment.populationSize(), assignment.name(), runnerName, seedScript, weights);
    this.world.setMatchItemList(cases);
    this.world.setName("mismo-world-" + runId);
    this.world.setDaemon(true);
    this.reporter = new Thread(this::reportLoop, "mismo-reporter-" + runId);
    this.reporter.setDaemon(true);
    this.lastReportMillis = startedMillis;
  }

  void start() {
    world.start();
    reporter.start();
  }

  int runId() {
    return runId;
  }

  Messages.Assignment assignment() {
    return assignment;
  }

  int[][] weights() {
    return weights;
  }

  int caseCount() {
    return caseCount;
  }

  World world() {
    return world;
  }

  long startedMillis() {
    return startedMillis;
  }

  String connection() {
    return connection;
  }

  long lastReportMillis() {
    return lastReportMillis;
  }

  /** @return the best creature from the last completed generation, or {@code null} before the first */
  Best best() {
    Creature[] copy = world.getCreaturesCopy();
    if (copy == null || copy.length == 0 || copy[0] == null) {
      return null;
    }
    Creature creature = copy[0];
    String script = creature.makeScript();
    return new Best(creature.getGeneration(), creature.getScore(), script, Hashes.sha256Hex(script));
  }

  /** Sends progress and the current best candidate now. */
  synchronized void sync() {
    report(true, true);
  }

  /**
   * Stops the World at its next safe point, uploads the final best candidate, and marks the run
   * stopped on the server.
   *
   * @return a line to print about the outcome
   */
  String stop(String reason) {
    world.setKeepRunning(false);
    reporting = false;
    reporter.interrupt();
    try {
      world.join(30_000L);
      reporter.join(5_000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    synchronized (this) {
      report(true, true);
      if (!serverAcceptsReports) {
        return "Run " + runId + " stopped locally. Mismo Trainer had already ended it: " + connection;
      }
      try {
        api.stop(runId, reason);
        return "Run " + runId + " stopped. Its best candidate is saved in Mismo Trainer.";
      } catch (ApiClient.ApiError e) {
        return "Run " + runId + " stopped locally, but Mismo Trainer could not be told: " + e.getMessage();
      }
    }
  }

  private void reportLoop() {
    while (reporting) {
      try {
        Thread.sleep(REPORTER_TICK_MILLIS);
      } catch (InterruptedException e) {
        return;
      }
      long now = System.currentTimeMillis();
      synchronized (this) {
        if (!reporting) {
          return;
        }
        if (now < nextAttemptMillis) {
          continue;
        }
        report(now - lastProgressMillis >= PROGRESS_INTERVAL_MILLIS,
            now - lastCandidateMillis >= CANDIDATE_INTERVAL_MILLIS);
      }
    }
  }

  /** Caller holds the lock. */
  private void report(boolean sendProgress, boolean sendCandidate) {
    if (!serverAcceptsReports) {
      return;
    }
    Best best = best();
    try {
      if (sendCandidate && best != null && !best.sha256().equals(lastUploadedSha256)) {
        api.candidate(runId, new Messages.CandidateUpload(best.generation(), best.score(), best.script()));
        lastUploadedSha256 = best.sha256();
        lastCandidateMillis = System.currentTimeMillis();
        markConnected();
      }
      if (sendProgress) {
        api.progress(runId, new Messages.ProgressReport(world.getGeneration(), world.getScoreRate(),
            best == null ? 0.0 : best.score()));
        lastProgressMillis = System.currentTimeMillis();
        markConnected();
      }
    } catch (ApiClient.ApiError e) {
      if ("RUN_NOT_ACTIVE".equals(e.code())) {
        serverAcceptsReports = false;
        connection = e.getMessage() + " Optimization continues locally.";
      } else {
        retryDelayMillis = retryDelayMillis == 0 ? FIRST_RETRY_MILLIS : Math.min(MAX_RETRY_MILLIS, retryDelayMillis * 2);
        nextAttemptMillis = System.currentTimeMillis() + retryDelayMillis;
        connection = "Disconnected: " + e.getMessage() + " Retrying in " + (retryDelayMillis / 1000) + " s.";
      }
    }
  }

  private void markConnected() {
    retryDelayMillis = 0;
    nextAttemptMillis = 0;
    connection = "Connected";
    lastReportMillis = System.currentTimeMillis();
  }
}
