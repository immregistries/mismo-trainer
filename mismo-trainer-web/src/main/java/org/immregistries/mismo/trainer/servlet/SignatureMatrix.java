package org.immregistries.mismo.trainer.servlet;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.trainer.model.Configuration;

/**
 * Decodes a list of signatures into a matrix: one row per signature, one column per node the
 * signature encodes (every enabled leaf, in tree order), each cell the node's score as stored in the
 * signature (0 to 1). Used by the Signature page's Compare Signatures section and by Batch Signature
 * Analysis's CSV export, so Shelby can line signatures up in Excel and look for patterns.
 */
final class SignatureMatrix {

  private SignatureMatrix() {
  }

  /** One pasted line: the signature, plus its count if one followed it. */
  record Line(String signature, Integer count) {
  }

  /** A node that has a position in the signature; {@code section} is Match / Not Match / Twin / Missing. */
  record Column(String section, String name) {
  }

  /**
   * One decoded signature. {@code scores} has one entry per {@link Column} ({@code null} where the
   * signature had no value); {@code note} explains anything that makes the row untrustworthy.
   */
  record Row(Line line, String classification, List<Double> scores, String note) {
  }

  /**
   * Reads one signature per line. Anything after the signature (comma, tab, or space separated) is
   * taken as a count if it is a whole number. Blank lines, quotes, and lines without a {@code :} (a
   * header row, for instance) are skipped.
   */
  static List<Line> parseLines(String text) {
    List<Line> lines = new ArrayList<Line>();
    if (text == null) {
      return lines;
    }
    for (String raw : text.split("\\r?\\n")) {
      String[] parts = raw.replace("\"", "").trim().split("[,\\t ]+");
      if (parts.length == 0 || parts[0].indexOf(':') < 1) {
        continue;
      }
      Integer count = null;
      if (parts.length > 1) {
        try {
          count = Integer.valueOf(parts[1]);
        } catch (NumberFormatException e) {
          // not a count; ignore whatever follows the signature
        }
      }
      lines.add(new Line(parts[0], count));
    }
    return lines;
  }

  /** The hash part (before the first {@code :}) of {@code signature}. */
  static String hashOf(String signature) {
    int colon = signature.indexOf(':');
    return colon < 0 ? signature : signature.substring(0, colon);
  }

  /** The hashes in {@code lines}, most common first, with how many lines carry each. */
  static Map<String, Integer> countHashes(List<Line> lines) {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    for (Line line : lines) {
      counts.merge(hashOf(line.signature()), 1, Integer::sum);
    }
    List<Map.Entry<String, Integer>> sorted = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
    sorted.sort((a, b) -> b.getValue() - a.getValue());
    Map<String, Integer> result = new LinkedHashMap<String, Integer>();
    for (Map.Entry<String, Integer> entry : sorted) {
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  /**
   * The nodes a signature from {@code patientCompare}'s configuration encodes, in signature order --
   * the same walk as {@code AggregateMatchNode.populateScoreFromSignature}: enabled leaves only, and
   * a disabled branch drops everything under it.
   */
  static List<Column> columns(PatientCompare patientCompare) {
    List<Column> columns = new ArrayList<Column>();
    addColumns(columns, "Match", patientCompare.getMatch());
    addColumns(columns, "Not Match", patientCompare.getNotMatch());
    addColumns(columns, "Twin", patientCompare.getTwin());
    addColumns(columns, "Missing", patientCompare.getMissing());
    return columns;
  }

  private static void addColumns(List<Column> columns, String section, AggregateMatchNode node) {
    for (MatchNode child : node.getMatchNodeList()) {
      if (!child.isEnabled()) {
        continue;
      }
      if (child instanceof AggregateMatchNode) {
        addColumns(columns, section, (AggregateMatchNode) child);
      } else {
        columns.add(new Column(section, child.getMatchName()));
      }
    }
  }

  /**
   * Decodes every line against {@code configuration}. Scores come straight from the decoded list
   * rather than the nodes, because {@code PatientCompare.setSignature} leaves a node's previous value
   * in place when a signature runs short.
   */
  static List<Row> decode(Configuration configuration, PatientCompare patientCompare, List<Line> lines) {
    int columnCount = columns(patientCompare).size();
    List<Row> rows = new ArrayList<Row>();
    for (Line line : lines) {
      String note = null;
      if (!configuration.getHashForSignature().equals(hashOf(line.signature()))) {
        note = "Hash mismatch: made by " + hashOf(line.signature()) + ", not " + configuration.getHashForSignature();
      }
      List<Double> decoded;
      try {
        patientCompare.setSignature(line.signature());
        decoded = patientCompare.getScoreFromSignatureList();
      } catch (RuntimeException e) {
        rows.add(new Row(line, null, new ArrayList<Double>(), "Unreadable signature"));
        continue;
      }
      List<Double> scores = new ArrayList<Double>();
      for (int i = 0; i < columnCount; i++) {
        scores.add(i < decoded.size() ? decoded.get(i) : null);
      }
      String classification = null;
      // Each level is padded to a multiple of 6 positions, so up to 5 trailing zeros are normal; a
      // longer signature, or a non-zero value past the last node, came from a bigger configuration.
      boolean extra = decoded.size() >= columnCount + 6;
      for (int i = columnCount; i < decoded.size() && !extra; i++) {
        extra = decoded.get(i) != 0.0;
      }
      if (decoded.size() < columnCount) {
        note = (note == null ? "" : note + "; ") + "Too short for this configuration";
      } else if (extra) {
        note = (note == null ? "" : note + "; ") + "Has more values than this configuration has nodes";
        classification = SignatureClassifier.classify(patientCompare);
      } else {
        classification = SignatureClassifier.classify(patientCompare);
      }
      rows.add(new Row(line, classification, scores, note));
    }
    return rows;
  }

  static boolean anyCount(List<Row> rows) {
    for (Row row : rows) {
      if (row.line().count() != null) {
        return true;
      }
    }
    return false;
  }

  static boolean anyNote(List<Row> rows) {
    for (Row row : rows) {
      if (row.note() != null) {
        return true;
      }
    }
    return false;
  }

  static String formatScore(Double score) {
    return score == null ? "" : String.format(Locale.US, "%.2f", score);
  }

  /**
   * Writes the matrix as CSV for Excel: a UTF-8 byte-order mark, a first line naming the
   * configuration and hash used, then the header row and one row per signature.
   */
  static void writeCsv(PrintWriter out, Configuration configuration, List<Column> columns, List<Row> rows) {
    boolean showCount = anyCount(rows);
    boolean showNote = anyNote(rows);
    out.print('﻿');
    out.println(csv("Decoded with: " + configuration.getWorldName() + " / " + configuration.getIslandName()
        + " (generation " + configuration.getGeneration() + ")") + "," + csv("Hash: "
        + configuration.getHashForSignature()));
    StringBuilder header = new StringBuilder("Signature");
    if (showCount) {
      header.append(",Count");
    }
    header.append(",Classification");
    if (showNote) {
      header.append(",Note");
    }
    for (Column column : columns) {
      header.append(",").append(csv(column.name()));
    }
    out.println(header);
    for (Row row : rows) {
      StringBuilder line = new StringBuilder(csv(row.line().signature()));
      if (showCount) {
        line.append(",").append(row.line().count() == null ? "" : row.line().count());
      }
      line.append(",").append(csv(row.classification()));
      if (showNote) {
        line.append(",").append(csv(row.note()));
      }
      for (Double score : row.scores()) {
        line.append(",").append(formatScore(score));
      }
      for (int i = row.scores().size(); i < columns.size(); i++) {
        line.append(",");
      }
      out.println(line);
    }
  }

  /**
   * Quotes {@code value} for CSV. Excel reads a cell starting with {@code + - = @} as a formula, and
   * hashes can start with {@code +}, so those are written as {@code ="..."} to stay plain text.
   */
  static String csv(String value) {
    if (value == null) {
      return "";
    }
    if (!value.isEmpty() && "+-=@".indexOf(value.charAt(0)) >= 0) {
      value = "=\"" + value.replace("\"", "\"\"") + "\"";
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
