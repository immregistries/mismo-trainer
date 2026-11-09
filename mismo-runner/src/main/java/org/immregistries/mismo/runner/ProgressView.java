package org.immregistries.mismo.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Text views of a run's {@link ScoreHistory}: an area chart of the best score over time, a table
 * of the best score at log-spaced times, and the dashboard's two trend lines. Plain ASCII only, so
 * it reads the same in any Windows console code page.
 *
 * <p>The chart's vertical axis runs from the lowest to the highest score seen, not from 0 to 100%,
 * because the gains worth watching are often a fraction of a point.
 */
final class ProgressView {

  static final int CHART_WIDTH = 60;
  static final int CHART_HEIGHT = 10;
  static final long RECENT_WINDOW_MILLIS = 30 * 60_000L;

  private static final long MINUTE = 60_000L;
  private static final long HOUR = 60 * MINUTE;
  /** Table rows; after the last one, a row every 24 hours. */
  private static final long[] CHECKPOINTS = { MINUTE, 2 * MINUTE, 5 * MINUTE, 10 * MINUTE, 15 * MINUTE,
      30 * MINUTE, HOUR, 2 * HOUR, 4 * HOUR, 8 * HOUR, 12 * HOUR, 24 * HOUR };
  private static final String LABEL_FORMAT = "%8s |";
  private static final String NOT_ENOUGH = "Not enough history yet; scores are sampled every few seconds.";

  private ProgressView() {
  }

  /** The {@code progress} command's output: the chart, then the table, then the last improvement. */
  static String render(List<ScoreHistory.Sample> samples, ScoreHistory.Improvement lastImprovement) {
    if (samples.isEmpty()) {
      return "No generation has been scored yet.\n";
    }
    StringBuilder text = new StringBuilder();
    text.append(chart(samples, CHART_WIDTH, CHART_HEIGHT));
    text.append('\n');
    text.append(table(samples));
    ScoreHistory.Sample last = samples.get(samples.size() - 1);
    text.append(lastGainLine(lastImprovement, last.elapsedMillis())).append('\n');
    return text.toString();
  }

  static String chart(List<ScoreHistory.Sample> samples, int width, int height) {
    if (samples.size() < 2) {
      return NOT_ENOUGH + "\n";
    }
    ScoreHistory.Sample first = samples.get(0);
    ScoreHistory.Sample last = samples.get(samples.size() - 1);
    long span = last.elapsedMillis() - first.elapsedMillis();
    if (span <= 0) {
      return NOT_ENOUGH + "\n";
    }
    double low = Double.MAX_VALUE;
    double high = -Double.MAX_VALUE;
    for (ScoreHistory.Sample sample : samples) {
      low = Math.min(low, sample.score());
      high = Math.max(high, sample.score());
    }
    boolean flat = high - low < 1e-12;
    int rows = flat ? 1 : height;

    int[] levels = new int[width];
    for (int column = 0; column < width; column++) {
      long at = first.elapsedMillis() + Math.round((double) span * column / (width - 1));
      double score = ScoreHistory.at(samples, at).score();
      levels[column] = flat ? 0 : (int) Math.round((score - low) / (high - low) * (rows - 1));
    }

    StringBuilder text = new StringBuilder();
    text.append("Best score over ").append(longDuration(span)).append(" (generations ")
        .append(String.format("%,d", first.generation())).append(" to ")
        .append(String.format("%,d", last.generation())).append(")")
        .append(flat ? ", no change yet" : "").append('\n');
    for (int row = rows - 1; row >= 0; row--) {
      String label = "";
      if (row == rows - 1) {
        label = percent(high);
      } else if (row == 0) {
        label = percent(low);
      }
      StringBuilder line = new StringBuilder(String.format(LABEL_FORMAT, label));
      for (int column = 0; column < width; column++) {
        line.append(levels[column] >= row ? '#' : ' ');
      }
      text.append(stripTrailing(line)).append('\n');
    }
    text.append(String.format(LABEL_FORMAT, "").replace('|', '+')).append("-".repeat(width)).append('\n');
    text.append(axisLabels(width, span)).append('\n');
    return text.toString();
  }

  /** "0" under the left edge, the midpoint under the middle, and the total under the right edge. */
  private static String axisLabels(int width, long span) {
    int indent = String.format(LABEL_FORMAT, "").length();
    char[] line = new char[indent + width];
    Arrays.fill(line, ' ');
    place(line, indent, "0");
    String middle = shortDuration(span / 2);
    place(line, indent + (width - middle.length()) / 2, middle);
    String end = shortDuration(span);
    place(line, indent + width - end.length(), end);
    return stripTrailing(new StringBuilder().append(line));
  }

  private static void place(char[] line, int start, String text) {
    for (int i = 0; i < text.length() && start + i < line.length; i++) {
      line[start + i] = text.charAt(i);
    }
  }

  /** The best score at the first sample, at each checkpoint passed, and now, with the gain between rows. */
  static String table(List<ScoreHistory.Sample> samples) {
    if (samples.isEmpty()) {
      return "";
    }
    ScoreHistory.Sample first = samples.get(0);
    ScoreHistory.Sample last = samples.get(samples.size() - 1);
    List<String> labels = new ArrayList<>();
    List<ScoreHistory.Sample> rows = new ArrayList<>();
    labels.add("first");
    rows.add(first);
    for (long checkpoint : checkpointsBefore(last.elapsedMillis())) {
      if (checkpoint > first.elapsedMillis()) {
        labels.add(shortDuration(checkpoint));
        rows.add(ScoreHistory.at(samples, checkpoint));
      }
    }
    if (last != first) {
      labels.add("now");
      rows.add(last);
    }

    StringBuilder text = new StringBuilder();
    text.append(String.format("  %-8s %12s %9s %12s%n", "Elapsed", "Generation", "Best", "Gain (pts)"));
    for (int i = 0; i < rows.size(); i++) {
      ScoreHistory.Sample row = rows.get(i);
      String gain = i == 0 ? "" : points(row.score() - rows.get(i - 1).score());
      text.append(stripTrailing(new StringBuilder(String.format("  %-8s %,12d %9s %12s", labels.get(i),
          row.generation(), percent(row.score()), gain)))).append('\n');
    }
    return text.toString();
  }

  private static List<Long> checkpointsBefore(long elapsedMillis) {
    List<Long> passed = new ArrayList<>();
    for (long checkpoint : CHECKPOINTS) {
      if (checkpoint < elapsedMillis) {
        passed.add(checkpoint);
      }
    }
    for (long checkpoint = 48 * HOUR; checkpoint < elapsedMillis; checkpoint += 24 * HOUR) {
      passed.add(checkpoint);
    }
    return passed;
  }

  /** e.g. "Last gain:   41 minutes ago at generation 13,960 (+0.012 pts)" */
  static String lastGainLine(ScoreHistory.Improvement improvement, long nowElapsedMillis) {
    if (improvement == null) {
      return "Last gain:   none yet";
    }
    return "Last gain:   " + longDuration(nowElapsedMillis - improvement.elapsedMillis()) + " ago at generation "
        + String.format("%,d", improvement.generation()) + " (" + points(improvement.gain()) + " pts)";
  }

  /** e.g. "Recent gain: +0.050 pts in the last 30 minutes" */
  static String recentGainLine(List<ScoreHistory.Sample> samples) {
    if (samples.size() < 2) {
      return "Recent gain: (not enough history yet)";
    }
    ScoreHistory.Sample first = samples.get(0);
    ScoreHistory.Sample last = samples.get(samples.size() - 1);
    long windowStart = last.elapsedMillis() - RECENT_WINDOW_MILLIS;
    if (windowStart <= first.elapsedMillis()) {
      return "Recent gain: " + points(last.score() - first.score()) + " pts since the first score";
    }
    return "Recent gain: " + points(last.score() - ScoreHistory.at(samples, windowStart).score())
        + " pts in the last " + longDuration(RECENT_WINDOW_MILLIS);
  }

  static String percent(double score) {
    return String.format("%.2f%%", score * 100.0);
  }

  /** A score difference in percentage points, e.g. "+0.012". */
  static String points(double scoreDifference) {
    return String.format("%+.3f", scoreDifference * 100.0);
  }

  /** e.g. "45s", "12m", "2h", "2h14m" */
  static String shortDuration(long millis) {
    long seconds = Math.max(0, millis / 1000);
    if (seconds < 60) {
      return seconds + "s";
    }
    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + "m";
    }
    long hours = minutes / 60;
    return hours + "h" + (minutes % 60 == 0 ? "" : String.format("%02dm", minutes % 60));
  }

  /** e.g. "45 seconds", "12 minutes", "2 hours 14 minutes" */
  static String longDuration(long millis) {
    long seconds = Math.max(0, millis / 1000);
    if (seconds < 60) {
      return seconds + (seconds == 1 ? " second" : " seconds");
    }
    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + (minutes == 1 ? " minute" : " minutes");
    }
    long hours = minutes / 60;
    long rest = minutes % 60;
    return hours + (hours == 1 ? " hour" : " hours") + (rest == 0 ? "" : " " + rest + (rest == 1 ? " minute" : " minutes"));
  }

  private static String stripTrailing(CharSequence line) {
    return line.toString().stripTrailing();
  }
}
