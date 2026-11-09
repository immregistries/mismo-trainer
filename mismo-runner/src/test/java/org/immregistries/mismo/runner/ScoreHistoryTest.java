package org.immregistries.mismo.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class ScoreHistoryTest {

  private static final long MINUTE = 60_000L;

  @Test
  public void tracksTheLastImprovement() {
    ScoreHistory history = new ScoreHistory();
    history.record(5_000, 1, 0.90);
    assertNull(history.lastImprovement());
    history.record(10_000, 2, 0.90);
    history.record(15_000, 3, 0.91);
    history.record(20_000, 4, 0.91);
    ScoreHistory.Improvement improvement = history.lastImprovement();
    assertEquals(15_000, improvement.elapsedMillis());
    assertEquals(3, improvement.generation());
    assertEquals(0.01, improvement.gain(), 1e-9);
  }

  @Test
  public void thinningKeepsFirstLastAndTheLastImprovement() {
    ScoreHistory history = new ScoreHistory(10);
    for (int i = 0; i <= 25; i++) {
      history.record(i * 1_000L, i, i == 25 ? 0.95 : 0.90);
    }
    List<ScoreHistory.Sample> samples = history.samples();
    assertTrue(samples.size() <= 10);
    assertEquals(0, samples.get(0).elapsedMillis());
    assertEquals(25_000, samples.get(samples.size() - 1).elapsedMillis());
    assertEquals(25_000, history.lastImprovement().elapsedMillis());
  }

  @Test
  public void atFindsTheLastSampleAtOrBefore() {
    ScoreHistory history = new ScoreHistory();
    history.record(10, 1, 0.1);
    history.record(20, 2, 0.2);
    history.record(30, 3, 0.3);
    List<ScoreHistory.Sample> samples = history.samples();
    assertEquals(1, ScoreHistory.at(samples, 5).generation());
    assertEquals(1, ScoreHistory.at(samples, 19).generation());
    assertEquals(2, ScoreHistory.at(samples, 20).generation());
    assertEquals(3, ScoreHistory.at(samples, 1_000).generation());
  }

  @Test
  public void chartIsScaledToTheScoreRange() {
    List<ScoreHistory.Sample> samples = plateauRun().samples();
    String chart = ProgressView.chart(samples, 60, 10);
    String[] lines = chart.split("\n");
    assertEquals(13, lines.length);
    assertTrue(lines[0], lines[0].startsWith("Best score over 2 hours 14 minutes"));
    assertTrue(lines[1], lines[1].startsWith("  93.41% |"));
    assertTrue(lines[10], lines[10].startsWith("  91.02% |#"));
    // The bottom row is filled across the whole width; the top row only once the plateau is reached.
    assertEquals(60, lines[10].substring(10).length());
    assertTrue(lines[1].endsWith("#"));
    assertTrue(lines[1].substring(10).startsWith(" "));
    assertTrue(lines[12], lines[12].trim().startsWith("0") && lines[12].endsWith("2h14m"));
  }

  @Test
  public void flatHistoryDrawsOneRow() {
    ScoreHistory history = new ScoreHistory();
    history.record(0, 1, 0.5);
    history.record(MINUTE, 10, 0.5);
    String chart = ProgressView.chart(history.samples(), 20, 10);
    assertTrue(chart, chart.contains("no change yet"));
    assertEquals(4, chart.split("\n").length);
  }

  @Test
  public void tableShowsCheckpointsAndGains() {
    String table = ProgressView.table(plateauRun().samples());
    String[] lines = table.split("\n");
    assertTrue(lines[0].contains("Gain (pts)"));
    assertTrue(lines[1], lines[1].trim().startsWith("first"));
    assertTrue(table, table.contains("  1m "));
    assertTrue(table, table.contains("  2h "));
    assertTrue(lines[lines.length - 1], lines[lines.length - 1].trim().startsWith("now"));
    assertTrue(lines[lines.length - 1], lines[lines.length - 1].endsWith("+0.000"));
  }

  @Test
  public void trendLines() {
    ScoreHistory history = plateauRun();
    List<ScoreHistory.Sample> samples = history.samples();
    long now = samples.get(samples.size() - 1).elapsedMillis();
    assertEquals("Last gain:   41 minutes ago at generation 13,960 (+0.020 pts)",
        ProgressView.lastGainLine(history.lastImprovement(), now));
    assertEquals("Recent gain: +0.000 pts in the last 30 minutes", ProgressView.recentGainLine(samples));
    assertEquals("Last gain:   none yet", ProgressView.lastGainLine(null, now));
  }

  @Test
  public void durations() {
    assertEquals("45s", ProgressView.shortDuration(45_000));
    assertEquals("12m", ProgressView.shortDuration(12 * MINUTE));
    assertEquals("2h", ProgressView.shortDuration(120 * MINUTE));
    assertEquals("2h05m", ProgressView.shortDuration(125 * MINUTE));
    assertEquals("1 hour 1 minute", ProgressView.longDuration(61 * MINUTE));
  }

  /** Prints a sample of the progress command, for eyeballing with {@code mvn test}. */
  @Test
  public void renderSample() {
    ScoreHistory history = plateauRun();
    String text = ProgressView.render(history.samples(), history.lastImprovement());
    System.out.println(text);
    assertTrue(text.contains("Last gain:"));
  }

  /** A 2 h 14 min run sampled every 5 seconds: a fast climb that levels off, last gain 41 minutes before the end. */
  private static ScoreHistory plateauRun() {
    ScoreHistory history = new ScoreHistory();
    long end = 134 * MINUTE;
    long lastGain = end - 41 * MINUTE;
    for (long t = 0; t <= end; t += 5_000) {
      int generation = t == lastGain ? 13_960 : (int) (t / 400);
      double score;
      if (t >= lastGain) {
        score = 0.9341;
      } else {
        double climb = 0.9102 + 0.0238 * (1 - Math.exp(-t / (12.0 * MINUTE)));
        score = Math.min(0.9340, Math.floor(climb * 10_000) / 10_000);
      }
      history.record(t, generation, score);
    }
    return history;
  }

}
