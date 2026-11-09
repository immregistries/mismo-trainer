package org.immregistries.mismo.runner;

import java.util.ArrayList;
import java.util.List;

/**
 * The best score over the life of one run, kept in memory only so the console can show how the
 * optimization is going (the {@code progress} command and the dashboard's trend lines). Nothing is
 * persisted or sent to the server; a restarted runner starts a fresh history.
 *
 * <p>The reporter samples it every few seconds. Samples are kept evenly in time: when the list
 * reaches {@link #MAX_SAMPLES}, every other one is dropped (keeping the first and last), so a run of
 * any length stays small while its overall shape is preserved. The most recent improvement is
 * tracked separately so it stays exact after thinning.
 */
final class ScoreHistory {

  static final int MAX_SAMPLES = 2_000;

  /** The best score at a moment, {@code elapsedMillis} after the run started. */
  record Sample(long elapsedMillis, int generation, double score) {
  }

  /** When the best score last went up, and by how much. */
  record Improvement(long elapsedMillis, int generation, double gain) {
  }

  private final int maxSamples;
  private final List<Sample> samples = new ArrayList<>();
  private Improvement lastImprovement;

  ScoreHistory() {
    this(MAX_SAMPLES);
  }

  ScoreHistory(int maxSamples) {
    if (maxSamples < 3) {
      throw new IllegalArgumentException("maxSamples must be at least 3");
    }
    this.maxSamples = maxSamples;
  }

  synchronized void record(long elapsedMillis, int generation, double score) {
    if (!samples.isEmpty()) {
      Sample last = samples.get(samples.size() - 1);
      if (elapsedMillis < last.elapsedMillis()) {
        return;
      }
      if (score > last.score()) {
        lastImprovement = new Improvement(elapsedMillis, generation, score - last.score());
      }
    }
    samples.add(new Sample(elapsedMillis, generation, score));
    if (samples.size() > maxSamples) {
      thin();
    }
  }

  /** Drops every other sample, keeping the first and the last. */
  private void thin() {
    List<Sample> kept = new ArrayList<>(samples.size() / 2 + 2);
    int last = samples.size() - 1;
    for (int i = 0; i < last; i += 2) {
      kept.add(samples.get(i));
    }
    kept.add(samples.get(last));
    samples.clear();
    samples.addAll(kept);
  }

  synchronized List<Sample> samples() {
    return List.copyOf(samples);
  }

  /** @return the latest improvement, or {@code null} if the score has not gone up since the first sample */
  synchronized Improvement lastImprovement() {
    return lastImprovement;
  }

  /**
   * @return the last sample at or before {@code elapsedMillis}; the first sample if all are later;
   *         {@code null} if there are none
   */
  static Sample at(List<Sample> samples, long elapsedMillis) {
    if (samples.isEmpty()) {
      return null;
    }
    int low = 0;
    int high = samples.size() - 1;
    if (samples.get(0).elapsedMillis() > elapsedMillis) {
      return samples.get(0);
    }
    while (low < high) {
      int mid = (low + high + 1) >>> 1;
      if (samples.get(mid).elapsedMillis() <= elapsedMillis) {
        low = mid;
      } else {
        high = mid - 1;
      }
    }
    return samples.get(low);
  }
}
