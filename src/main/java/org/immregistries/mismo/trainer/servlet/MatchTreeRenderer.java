package org.immregistries.mismo.trainer.servlet;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Set;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;

/**
 * Shared rendering helpers for the Match/Not-Match/Twin/Missing detector tree and per-field
 * patient comparison rows -- consolidated out of near-identical copies previously duplicated in
 * {@code TestSetServlet}, {@code MatchPatientServlet}, and {@code SignatureServlet}
 * (modernization-notes.md §8, folded in per v2-roadmap.md §5). Also fixes the recurring
 * missing-{@code >} bug on the min/max score inputs that existed in every copy.
 */
public final class MatchTreeRenderer {

  private MatchTreeRenderer() {
  }

  /** Renders a score cell, styled pass/fail at the same 0.5 threshold a network fires at. */
  public static String printScore(double score) {
    return printScore(score, false);
  }

  /** Renders a score cell, optionally emphasized (e.g. a non-zero Match/Missing contribution). */
  public static String printScore(double score, boolean emphasize) {
    DecimalFormat df = new DecimalFormat("0.00");
    String style = score > 0.5 ? "pass" : "fail";
    if (emphasize) {
      style += " score-emphasis";
    }
    return "<td class=\"" + style + "\" valign=\"top\">" + df.format(score) + "</td>";
  }

  /**
   * Renders a detector (sub)tree against a live patient pair, with editable min/max score
   * inputs -- the variant used where a {@code PatientCompare} is available (e.g.
   * {@code MatchPatientServlet}).
   */
  public static void printAggregateNode(PrintWriter out, Patient patientA, Patient patientB, MatchNode node,
      String name) {
    DecimalFormat df = new DecimalFormat("0.000");
    out.println("<td>");
    if (node instanceof AggregateMatchNode) {
      AggregateMatchNode amNode = (AggregateMatchNode) node;
      out.println("<table border=\"1\" cellspacing=\"0\">");
      out.println("<tr><th>" + amNode.getMatchName()
          + "</th><th>W Score</th><th>Min W</th><th>Max W</th><th>Score</th><th>&nbsp;</th></tr>");
      for (MatchNode childNode : amNode.getMatchNodeList()) {
        String childName = name + "." + childNode.getMatchName();
        out.println("<tr>");
        out.println("<td valign=\"top\">" + childNode.getMatchName() + "</td>");
        if (childNode.isEnabled()) {
          out.println("<td valign=\"top\">" + df.format(childNode.weightScore(patientA, patientB)) + "</td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"min_" + childName + "\" size=\"5\" value=\""
              + childNode.getMinScore() + "\"></td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"max_" + childName + "\" size=\"5\" value=\""
              + childNode.getMaxScore() + "\"></td>");
          out.println(printScore(childNode.score(patientA, patientB)));
          printAggregateNode(out, patientA, patientB, childNode, childName);
        } else {
          out.println("<td valign=\"top\" colspan=\"5\"><em>disabled</em></td>");
        }
        out.println("</tr>");
      }
      out.println("</table>");
    } else {
      String description = node.getDescription(patientA, patientB);
      out.println(description.equals("") ? "&nbsp;" : description);
    }
    out.println("</td>");
  }

  /**
   * Renders a detector (sub)tree from a stored match signature rather than a live patient pair
   * -- the variant used by {@code SignatureServlet}, where only the signature is available.
   *
   * @param showAllFields when false, nodes that are disabled or contribute no non-zero score
   *     anywhere in their subtree are omitted, so analysts see only what actually fired
   * @param emphasizeNonZero when true, non-zero scores are visually emphasized -- used for the
   *     Match and Missing portions of the network, since those are what analysts care about most
   *     (proposed-functional-model-and-navigation.md §9.2)
   */
  public static void printAggregateNodeFromSignature(PrintWriter out, MatchNode node, String name,
      boolean showAllFields, boolean emphasizeNonZero) {
    DecimalFormat df = new DecimalFormat("0.000");
    out.println("<td>");
    if (node instanceof AggregateMatchNode) {
      AggregateMatchNode amNode = (AggregateMatchNode) node;
      List<MatchNode> children = amNode.getMatchNodeList();
      boolean anyVisible = false;
      for (MatchNode childNode : children) {
        if (showAllFields || hasPopulatedContent(childNode)) {
          anyVisible = true;
          break;
        }
      }
      if (!anyVisible) {
        out.println("<em>No populated fields</em>");
        out.println("</td>");
        return;
      }
      out.println("<table border=\"1\" cellspacing=\"0\">");
      out.println("<tr><th>" + amNode.getMatchName()
          + "</th><th>W Score</th><th>Min W</th><th>Max W</th><th>Score</th><th>&nbsp;</th></tr>");
      for (MatchNode childNode : children) {
        if (!showAllFields && !hasPopulatedContent(childNode)) {
          continue;
        }
        String childName = name + "." + childNode.getMatchName();
        out.println("<tr>");
        out.println("<td valign=\"top\">" + childNode.getMatchName() + "</td>");
        if (childNode.isEnabled()) {
          out.println("<td valign=\"top\">" + df.format(childNode.weightScoreFromSignaturer()) + "</td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"min_" + childName + "\" size=\"5\" value=\""
              + childNode.getMinScore() + "\"></td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"max_" + childName + "\" size=\"5\" value=\""
              + childNode.getMaxScore() + "\"></td>");
          out.println(printScore(childNode.getScoreFromSignature(),
              emphasizeNonZero && childNode.getScoreFromSignature() != 0));
          printAggregateNodeFromSignature(out, childNode, childName, showAllFields, emphasizeNonZero);
        } else {
          out.println("<td valign=\"top\" colspan=\"5\"><em>disabled</em></td>");
        }
        out.println("</tr>");
      }
      out.println("</table>");
    }
    out.println("</td>");
  }

  /**
   * True if {@code node} is an enabled leaf with a non-zero signature score, or an enabled
   * aggregate node with at least one such descendant -- i.e. it actually contributed to this
   * signature rather than just being structurally present in the configuration.
   */
  private static boolean hasPopulatedContent(MatchNode node) {
    if (!node.isEnabled()) {
      return false;
    }
    if (node instanceof AggregateMatchNode) {
      for (MatchNode child : ((AggregateMatchNode) node).getMatchNodeList()) {
        if (hasPopulatedContent(child)) {
          return true;
        }
      }
      return false;
    }
    return node.getScoreFromSignature() != 0;
  }

  /**
   * Renders one field-comparison row (Patient A vs Patient B) inside a test-case table, with
   * same/different/present-on-one-side/missing-on-both highlighting computed from the two raw
   * values (v2-roadmap.md §9, ui-changes-for-functional-model.md §3).
   */
  public static void printMatchRow(PrintWriter out, MatchItem matchItemSelected, String fieldLabel,
      String fieldName, Set<String> patientFieldSet) {
    if (patientFieldSet != null && !patientFieldSet.contains(fieldName)) {
      return;
    }
    Patient patientA = matchItemSelected.getPatientA();
    Patient patientB = matchItemSelected.getPatientB();
    String valueA = patientA.getValue(fieldName);
    String valueB = patientB.getValue(fieldName);
    boolean hasA = valueA != null && !valueA.equals("");
    boolean hasB = valueB != null && !valueB.equals("");
    String style;
    if (!hasA && !hasB) {
      style = "field-missing";
    } else if (hasA != hasB) {
      style = "field-partial";
    } else {
      style = valueA.equalsIgnoreCase(valueB) ? "field-same" : "field-diff";
    }
    out.println("      <tr>");
    out.println("        <td class=\"" + style + "\">" + fieldLabel + "</td>");
    out.println("        <td class=\"" + style + "\">" + (hasA ? valueA : "&mdash;") + "</td>");
    out.println("        <td class=\"" + style + "\">" + (hasB ? valueB : "&mdash;") + "</td>");
    out.println("      </tr>");
  }
}
