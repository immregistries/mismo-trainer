package org.immregistries.mismo.trainer.servlet;

import java.io.PrintWriter;
import java.text.DecimalFormat;
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
    DecimalFormat df = new DecimalFormat("0.00");
    String style = score > 0.5 ? "pass" : "fail";
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
   */
  public static void printAggregateNodeFromSignature(PrintWriter out, MatchNode node, String name) {
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
          out.println("<td valign=\"top\">" + df.format(childNode.weightScoreFromSignaturer()) + "</td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"min_" + childName + "\" size=\"5\" value=\""
              + childNode.getMinScore() + "\"></td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"max_" + childName + "\" size=\"5\" value=\""
              + childNode.getMaxScore() + "\"></td>");
          out.println(printScore(childNode.getScoreFromSignature()));
          printAggregateNodeFromSignature(out, childNode, childName);
        } else {
          out.println("<td valign=\"top\" colspan=\"5\"><em>disabled</em></td>");
        }
        out.println("</tr>");
      }
      out.println("</table>");
    }
    out.println("</td>");
  }

  /** Renders one field-comparison row (Patient A vs Patient B) inside a test-case table. */
  public static void printMatchRow(PrintWriter out, MatchItem matchItemSelected, String fieldLabel,
      String fieldName, Set<String> patientFieldSet) {
    if (patientFieldSet != null && !patientFieldSet.contains(fieldName)) {
      return;
    }
    Patient patientA = matchItemSelected.getPatientA();
    Patient patientB = matchItemSelected.getPatientB();
    String valueA = patientA.getValue(fieldName);
    String valueB = patientB.getValue(fieldName);
    String style = "";
    if (!valueA.equals("") && !valueB.equals("")) {
      style = valueA.equalsIgnoreCase(valueB) ? "pass" : "fail";
    }
    out.println("      <tr>");
    out.println("        <td class=\"" + style + "\">" + fieldLabel + "</td>");
    out.println("        <td class=\"" + style + "\">" + valueA + "</td>");
    out.println("        <td class=\"" + style + "\">" + valueB + "</td>");
    out.println("      </tr>");
  }
}
