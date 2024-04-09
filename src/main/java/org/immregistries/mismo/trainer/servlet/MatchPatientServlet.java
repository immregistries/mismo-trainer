package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.matchers.AggregateMatchNode;
import org.immregistries.mismo.match.matchers.MatchNode;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;

/**
 * This was the original servlet that demonstrated how the matching worked for
 * one example.
 * 
 * @author Nathan Bunker
 * 
 */
public class MatchPatientServlet extends HomeServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    Session dataSession = (Session) session.getAttribute(ATTRIUBTE_DATA_SESSION);
    try {
      @SuppressWarnings("unchecked")
      List<MatchItem> matchTestCaseList = (List<MatchItem>) session
          .getAttribute(TestMatchingServlet.ATTRIBUTE_MATCH_TEST_CASE_LIST);
      PatientCompare patientCompare = (PatientCompare) session.getAttribute("patientCompare");

      String testId = req.getParameter("testId");
      if (testId == null) {
        testId = "";
      }
      String description = "";
      if (req.getParameter("testId") != null && matchTestCaseList != null) {
        for (MatchItem matchItem : matchTestCaseList) {
          if (matchItem.getLabel().equals(testId)) {
            patientCompare.setMatchItem(matchItem);
            description = matchItem.getDescription();
            break;
          }
        }
      }
      String patientAValues;
      String patientBValues;
      MatchItem matchItemSelected = null;
      if (req.getParameter(TestSetServlet.PARAM_MATCH_ITEM_ID) != null) {
        matchItemSelected = (MatchItem) dataSession.get(MatchItem.class,
            Integer.parseInt(req.getParameter(TestSetServlet.PARAM_MATCH_ITEM_ID)));
        patientCompare.setMatchItem(matchItemSelected);
        patientAValues = matchItemSelected.getPatientDataA();
        patientBValues = matchItemSelected.getPatientDataB();
        testId = matchItemSelected.getLabel();
      } else {
        patientAValues = req.getParameter("patientAValues");
        if (patientAValues == null && patientCompare.getPatientA() != null) {
          patientAValues = patientCompare.getPatientA().getValues();
        } else {
          patientCompare.clear();
          patientCompare.setPatientA(new Patient(patientAValues));
        }
        patientBValues = req.getParameter("patientBValues");
        if (patientBValues == null && patientCompare.getPatientB() != null) {
          patientBValues = patientCompare.getPatientB().getValues();
        } else {
          patientCompare.setPatientB(new Patient(patientBValues));
        }
      }
      HomeServlet.doHeader(out, user, null);
      out.println("    <h1>Match Patient</h1>");
      out.println("    <form action=\"MatchPatientServlet\" method=\"POST\"> ");
      out.println("    <table>");
      if (!testId.equals("")) {
        out.println("      <tr><td>Label</td><td>" + testId + "</td></tr>");
      }
      if (!description.equals("")) {
        out.println("      <tr><td>Description</td><td>" + description + "</td></tr>");
      }
      out.println(
          "      <tr><td>Patient A</td><td><input type=\"text\" name=\"patientAValues\" value=\""
              + (patientAValues == null ? "" : patientAValues) + "\" size=\"60\"></td></tr>");
      out.println(
          "      <tr><td>Patient B</td><td><input type=\"text\" name=\"patientBValues\" value=\""
              + (patientBValues == null ? "" : patientBValues) + "\" size=\"60\"></td></tr>");
      out.println(
          "      <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"submit\" value=\"Submit\"></td></tr>");
      out.println("    </table>");

      Set<String> patientFieldSet = null;
      if (patientCompare != null && patientCompare.getConfiguration() != null) {
        patientFieldSet = patientCompare.getConfiguration().getPatientFieldSet();
      }
    Set<String> allFieldsSet = new HashSet<String>();
      allFieldsSet.addAll(patientCompare.getPatientA().getValueMap().keySet());
      allFieldsSet.addAll(patientCompare.getPatientB().getValueMap().keySet());
      List<String> allFieldsList = new ArrayList<String>(allFieldsSet);
      Collections.sort(allFieldsList);
      out.println("    <br>");
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("     <tr><th>Field</th><th>Patient A</th><th>Patient B</th></tr>");
      for (String fieldName : allFieldsList) {
        if (patientFieldSet != null && !patientFieldSet.contains(fieldName)) {
          continue;
        }
        String valueA = patientCompare.getPatientA().getValue(fieldName);
        String valueB = patientCompare.getPatientB().getValue(fieldName);
        String style = "";
        if (!valueA.equals("") && valueA.equalsIgnoreCase(valueB)) {
          style = "pass";
        } else if (!valueA.equals("")) {
          style = "fail";
        }
        if (valueA.equals("")) {
          valueA = "&nbsp;";
        }
        if (valueB.equals("")) {
          valueB = "&nbsp;";
        }
        out.println("      <tr>");
        out.println("        <td class=\"" + style + "\">" + fieldName + "</td>");
        out.println("        <td class=\"" + style + "\">" + valueA + "</td>");
        out.println("        <td class=\"" + style + "\">" + valueB + "</td>");
        out.println("      </tr>");
      }
      out.println("    </table>");

      MatchNode match = patientCompare.getMatch();
      setMinMax(req, match, "match");
      MatchNode notMatch = patientCompare.getNotMatch();
      setMinMax(req, notMatch, "notmatch");
      MatchNode twin = patientCompare.getTwin();
      setMinMax(req, twin, "twin");
      MatchNode missing = patientCompare.getMissing();
      setMinMax(req, missing, "missing");

      out.println("    <br>");
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr><th>Node</th><th>Signal</th><th>Score</th></tr>");
      out.println("      <tr><td>Match</td><td>" + match.hasSignal(patientCompare) + "</td>"
          + printScore(match.weightScore(patientCompare)) + "</tr>");
      out.println("      <tr><td>Not Match</td><td>" + notMatch.hasSignal(patientCompare) + "</td>"
          + printScore(notMatch.weightScore(patientCompare)) + "</tr>");
      out.println("      <tr><td>Suspect Twin</td><td>" + twin.hasSignal(patientCompare) + "</td>"
          + printScore(twin.weightScore(patientCompare)) + "</tr>");
      out.println("      <tr><td>Missing Data</td><td>" + missing.hasSignal(patientCompare)
          + "</td>" + printScore(missing.weightScore(patientCompare)) + "</tr>");
      if (patientCompare.getResult().equals("Match")) {
        out.println("      <tr><td>Result</td><td class=\"pass\" colspan=\"2\">"
            + patientCompare.getResult() + "</td></tr>");
      } else {
        out.println("      <tr><td>Result</td><td class=\"fail\" colspan=\"2\">"
            + patientCompare.getResult() + "</td></tr>");
      }
      out.println("    </table>");
      out.println("    <br>");
      {
        out.println("<table border=\"1\" cellspacing=\"0\">");
        out.println("<tr><td valign=\"top\">Match</td>");
        printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(), match,
            "match");
        out.println("    </tr>");
        out.println("<tr><td valign=\"top\">Not a Match</td>");
        printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(),
            notMatch, "notmatch");
        out.println("    </tr>");
        out.println("<tr><td valign=\"top\">Twin</td>");
        printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(), twin,
            "twin");
        out.println("    </tr>");
        out.println("<tr><td valign=\"top\">Missing</td>");
        printAggregateNode(out, patientCompare.getPatientA(), patientCompare.getPatientB(), missing,
            "missing");
        out.println("    </tr>");
        out.println("    </table>");
      }
      out.println("    </form>");
      if (patientCompare != null) {
        out.println("    <h2>Signature</h2>");
        List<MatchNode> matchNodeList = new ArrayList<MatchNode>();
        Map<MatchNode, Double> scoreMap = new HashMap<MatchNode, Double>(); 
        List<Double> scoreList = patientCompare.getScoreList();
        patientCompare.populateMatchNodeListAndScoreMap(matchNodeList, scoreMap);
        out.println("    <hp>Match Node List size = " + matchNodeList.size() + "</p2>");
        out.println("    <hp>Score Map size = " + scoreMap.size() + "</p2>");
        out.println("    <hp>Score List size = " + scoreList.size() + "</p2>");
        out.println("    <table border=\"1\" cellspacing=\"0\">");
        out.println("      <tr>");
        out.println("        <th>Detector</th>");
        out.println("        <th>Score</th>");
        out.println("        <th>0..15</th>");
        out.println("        <th>B1</th>");
        out.println("        <th>B2</th>");
        out.println("        <th>B3</th>");
        out.println("        <th>B4</th>");
        out.println("        <th>C1</th>");
        out.println("        <th>C2</th>");
        out.println("        <th>C3</th>");
        out.println("        <th>C4</th>");
        out.println("      </tr>");
        String c1 = "";
        String c2 = "";
        String c3 = "";
        String c4 = "";
        for (MatchNode matchNode : matchNodeList) {
          out.println("      <tr>");
          out.println("        <td>" + matchNode.getMatchLabel() + "</td>");
          double score = scoreMap.get(matchNode);
          DecimalFormat decimalFormat = new DecimalFormat("0.00");
          out.println("        <td>" + decimalFormat.format(score) + "</td>");
          int hex = (int) (score * 15);
          out.println("        <td>" + hex + "</td>");
          int b1 = hex / 8 % 2;
          int b2 = hex / 4 % 2;
          int b3 = hex / 2 % 2;
          int b4 = hex % 2;
          out.println("        <td>" + b1 + "</td>");
          out.println("        <td>" + b2 + "</td>");
          out.println("        <td>" + b3 + "</td>");
          out.println("        <td>" + b4 + "</td>");
          c1 += b1;
          c2 += b2;
          c3 += b3;
          c4 += b4;
          if (c1.length() == 6) {
            out.println("        <td>" + c1 + "</td>");
            out.println("        <td>" + c2 + "</td>");
            out.println("        <td>" + c3 + "</td>");
            out.println("        <td>" + c4 + "</td>");
            c1 = "";
            c2 = "";
            c3 = "";
            c4 = "";
          }
          else {
            out.println("         <td></td>");
            out.println("         <td></td>");
            out.println("         <td></td>");
            out.println("         <td></td>");
          }
          out.println("      </tr>");
        }
        while (c1.length() > 0 && c1.length() < 6)
        {
          c1 += 0;
          c2 += 0;
          c3 += 0;
          c4 += 0;
          out.println("      <tr>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          out.println("        <td></td>");
          if (c1.length() == 6) {
            out.println("        <td>" + c1 + "</td>");
            out.println("        <td>" + c2 + "</td>");
            out.println("        <td>" + c3 + "</td>");
            out.println("        <td>" + c4 + "</td>");
          } else { 
            out.println("        <td></td>");
            out.println("        <td></td>");
            out.println("        <td></td>");
            out.println("        <td></td>");
          }
          out.println("      </tr>");
        }
        out.println("    </table>");
      }
      
      HomeServlet.doFooter(out, req);
    } catch (Exception e) {
      e.printStackTrace(out);
    } finally {
      teardown(req, resp);
      out.close();
    }
  }

  private void setMinMax(HttpServletRequest req, MatchNode node, String name) {
    {
      String min = req.getParameter("min_" + name);
      String max = req.getParameter("max_" + name);
      if (min != null) {
        node.setMinScore(Double.parseDouble(min));
      }
      if (max != null) {
        node.setMaxScore(Double.parseDouble(max));
      }

    }
    if (node instanceof AggregateMatchNode) {
      AggregateMatchNode amNode = (AggregateMatchNode) node;
      for (MatchNode childNode : amNode.getMatchNodeList()) {
        String childName = name + "." + childNode.getMatchName();
        setMinMax(req, childNode, childName);
      }
    }
  }

  private void printAggregateNode(PrintWriter out, Patient patientA, Patient patientB,
      MatchNode node, String name) {
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
          out.println("<td valign=\"top\">" + df.format(childNode.weightScore(patientA, patientB))
              + "</td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"min_" + childName
              + "\" size=\"3\" value=\"" + childNode.getMinScore() + "\"</td>");
          out.println("<td valign=\"top\"><input type=\"text\" name=\"max_" + childName
              + "\" size=\"3\" value=\"" + childNode.getMaxScore() + "\"</td>");
          out.println("" + printScore(childNode.score(patientA, patientB)) + "</td>");
          printAggregateNode(out, patientA, patientB, childNode, childName);
        } else {
          out.println("<td valign=\"top\" colspan=\"5\"><em>disabled</em></td>");
        }
        out.println("</tr>");
      }
      out.println("</table>");
    } else {
      String description = node.getDescription(patientA, patientB);
      if (description.equals("")) {
        description = "&nbsp;";
      }
      out.println(description);
    }
    out.println("</td>");
  }

  private static String printScore(double d) {
    DecimalFormat df = new DecimalFormat("0.00");
    if (d > 0.5) {
      return "<td class=\"pass\" valign=\"top\">" + df.format(d) + "</td>";
    }
    return "<td class=\"fail\" valign=\"top\">" + df.format(d) + "</td>";
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }
}
