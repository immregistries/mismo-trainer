package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.MatchSet;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;

/**
 * This servlet tests a set of match test cases against a given script to give a
 * summary of how well the weights work.
 * 
 * @author Nathan Bunker
 * 
 */
public class ReviewServlet extends HomeServlet
{
  protected static final String[] TEST_SCIPTS = { "MIIS-B", "MIIS-C", "MIIS-D", "MIIS-E", "MIIS-E2", "MIIS-E3", "MIIS-F1", "MIIS-F2" };

  public static final String ATTRIBUTE_SHOULD_NOT_MATCH_BUT_MATCHED = "matchItemListShouldNotMatchButMatched";
  public static final String ATTRIBUTE_SHOULD_NOT_MATCH_BUT_POSSIBLE_MATCHED = "matchItemListShouldNotMatchButPossibleMatched";
  public static final String ATTRIBUTE_SHOULD_POSSIBLE_MATCH_BUT_MATCHED = "matchItemListShouldPossibleMatchButMatched";
  public static final String ATTRIBUTE_SHOULD_POSSIBLE_MATCH_BUT_NOT_MATCHED = "matchItemListShouldPossibleMatchButNotMatched";
  public static final String ATTRIBUTE_SHOULD_MATCH_BUT_POSSIBLE_MATCHED = "matchItemListShouldMatchButPossibleMatched";
  public static final String ATTRIBUTE_SHOULD_MATCH_BUT_NOT_MATCHED = "matchItemListShouldMatchButNotMatched";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    Session dataSession = (Session) session.getAttribute(TestSetServlet.ATTRIUBTE_DATA_SESSION);
    try {
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }
      MatchSet matchSetSelected = (MatchSet) session.getAttribute(TestSetServlet.ATTRIBUTE_MATCH_SET);

      HomeServlet.doHeader(out, user, null);      out.println("    <h1>Review</h1>");
      Map<String, List<MatchItem>> signatureMap = (Map<String, List<MatchItem>>) session
          .getAttribute(TestSetServlet.ATTRIBUTE_SIGNATURE_MAP);
      if (signatureMap == null) {
        out.println("<p>Unable to show review results. Before reviewing please load a Weight Script and select a Test Set</p>");
      } else {
        List<MatchItem> matchItemList = (List<MatchItem>) session
            .getAttribute(TestSetServlet.ATTRIBUTE_MATCH_ITEM_LIST);
        List<MatchItem> matchItemListShouldNotMatchButMatched = new ArrayList<MatchItem>();
        List<MatchItem> matchItemListShouldNotMatchButPossibleMatched = new ArrayList<MatchItem>();
        List<MatchItem> matchItemListShouldPossibleMatchButMatched = new ArrayList<MatchItem>();
        List<MatchItem> matchItemListShouldPossibleMatchButNotMatched = new ArrayList<MatchItem>();
        List<MatchItem> matchItemListShouldMatchButPossibleMatched = new ArrayList<MatchItem>();
        List<MatchItem> matchItemListShouldMatchButNotMatched = new ArrayList<MatchItem>();
        session.setAttribute(ATTRIBUTE_SHOULD_NOT_MATCH_BUT_MATCHED, matchItemListShouldNotMatchButMatched);
        session.setAttribute(ATTRIBUTE_SHOULD_NOT_MATCH_BUT_POSSIBLE_MATCHED,
            matchItemListShouldNotMatchButPossibleMatched);
        session.setAttribute(ATTRIBUTE_SHOULD_POSSIBLE_MATCH_BUT_MATCHED, matchItemListShouldPossibleMatchButMatched);
        session.setAttribute(ATTRIBUTE_SHOULD_POSSIBLE_MATCH_BUT_NOT_MATCHED,
            matchItemListShouldPossibleMatchButNotMatched);
        session.setAttribute(ATTRIBUTE_SHOULD_MATCH_BUT_POSSIBLE_MATCHED, matchItemListShouldMatchButPossibleMatched);
        session.setAttribute(ATTRIBUTE_SHOULD_MATCH_BUT_NOT_MATCHED, matchItemListShouldMatchButNotMatched);
        for (MatchItem matchItem : matchItemList) {
          if (matchItem.isTested() && !matchItem.isPass()) {
            if (matchItem.getExpectStatus().equals(MatchItem.NOT_A_MATCH)
                && matchItem.getActualStatus().equals(MatchItem.MATCH)) {
              matchItemListShouldNotMatchButMatched.add(matchItem);
            } else if (matchItem.getExpectStatus().equals(MatchItem.NOT_A_MATCH)
                && matchItem.getActualStatus().equals(MatchItem.POSSIBLE_MATCH)) {
              matchItemListShouldNotMatchButPossibleMatched.add(matchItem);
            } else if (matchItem.getExpectStatus().equals(MatchItem.POSSIBLE_MATCH)
                && matchItem.getActualStatus().equals(MatchItem.MATCH)) {
              matchItemListShouldPossibleMatchButMatched.add(matchItem);
            } else if (matchItem.getExpectStatus().equals(MatchItem.POSSIBLE_MATCH)
                && matchItem.getActualStatus().equals(MatchItem.NOT_A_MATCH)) {
              matchItemListShouldPossibleMatchButNotMatched.add(matchItem);
            } else if (matchItem.getExpectStatus().equals(MatchItem.MATCH)
                && matchItem.getActualStatus().equals(MatchItem.POSSIBLE_MATCH)) {
              matchItemListShouldMatchButPossibleMatched.add(matchItem);
            } else if (matchItem.getExpectStatus().equals(MatchItem.MATCH)
                && matchItem.getActualStatus().equals(MatchItem.NOT_A_MATCH)) {
              matchItemListShouldMatchButNotMatched.add(matchItem);
            }
          }
        }

        if (matchItemListShouldNotMatchButMatched.size() > 0) {
          out.println("    <h2>Should Not Match but was Matched</h2>");
          printSublist(out, ATTRIBUTE_SHOULD_NOT_MATCH_BUT_MATCHED, matchItemListShouldNotMatchButMatched);
        }
        if (matchItemListShouldNotMatchButPossibleMatched.size() > 0) {
          out.println("    <h2>Should Not Match but was Possible Matched</h2>");
          printSublist(out, ATTRIBUTE_SHOULD_NOT_MATCH_BUT_POSSIBLE_MATCHED,
              matchItemListShouldNotMatchButPossibleMatched);
        }
        if (matchItemListShouldPossibleMatchButMatched.size() > 0) {
          out.println("    <h2>Possible Match but was Matched</h2>");
          printSublist(out, ATTRIBUTE_SHOULD_POSSIBLE_MATCH_BUT_MATCHED, matchItemListShouldPossibleMatchButMatched);
        }
        if (matchItemListShouldPossibleMatchButNotMatched.size() > 0) {
          out.println("    <h2>Possible Match but was Not Matched</h2>");
          printSublist(out, ATTRIBUTE_SHOULD_POSSIBLE_MATCH_BUT_NOT_MATCHED,
              matchItemListShouldPossibleMatchButNotMatched);
        }
        if (matchItemListShouldMatchButPossibleMatched.size() > 0) {
          out.println("    <h2>Match but was Possible Matched</h2>");
          printSublist(out, ATTRIBUTE_SHOULD_MATCH_BUT_POSSIBLE_MATCHED, matchItemListShouldMatchButPossibleMatched);
        }
        if (matchItemListShouldMatchButNotMatched.size() > 0) {
          out.println("    <h2>Match but was Not Matched</h2>");
          printSublist(out, ATTRIBUTE_SHOULD_MATCH_BUT_NOT_MATCHED, matchItemListShouldMatchButNotMatched);
        }

        out.println("    <h2>Sets of Similar Test Cases with Different Expectations</h2>");

        int pos = 0;
        for (String signature : signatureMap.keySet()) {
          boolean hasFailed = false;
          boolean hasPassed = false;
          for (MatchItem matchItem : signatureMap.get(signature)) {
            if (matchItem.isTested()) {
              if (matchItem.isPass()) {
                hasPassed = true;
              } else {
                hasFailed = true;
              }
            }
          }
          if (hasFailed && hasPassed) {
            pos++;
            out.println("  <h2>Review Set #" + pos + "</h2>");
            out.println("  <p>Signature: " + signature + "</p>");
            out.println("    <table border=\"1\" cellspacing=\"0\">");
            out.println("      <tr><th>Test Case</th><th>Status</th><th>Description</th><th>Expected</th><th>Actual</th></tr>");
            for (MatchItem matchItem : signatureMap.get(signature)) {
              String link = "TestSetServlet?" + TestSetServlet.PARAM_MATCH_SET_ID + "="
                  + matchItem.getMatchSet().getMatchSetId() + "&" + TestSetServlet.PARAM_MATCH_ITEM_ID + "="
                  + matchItem.getMatchItemId() + "&" + TestSetServlet.PARAM_SIGNATURE + "=" + signature;
              printRow(out, matchItem, link);
            }
            out.println("    </table>");
            out.println("    <br/>");
            out.println("    <table border=\"1\" cellspacing=\"0\">");
            out.println("      <tr>");
            out.println("        <th>Field</th>");
            for (MatchItem matchItem : signatureMap.get(signature)) {
              String label = matchItem.getLabel();
              if (label == null ) {
                label = "";
              }
              else {
                int i = label.indexOf(":");
                if (i > 0) {
                  label = label.substring(0, i);
                }
              }
              out.println("        <th>" + label + "</th>");
              out.println("        <th>" + matchItem.getExpectStatus() + "</th>");
            }
            out.println("      </tr>");
            String[] fields = new String[] {Patient.BIRTH_DATE, Patient.NAME_FIRST, Patient.NAME_LAST, Patient.NAME_MIDDLE, 
              Patient.ADDRESS_STREET1, Patient.ADDRESS_CITY, Patient.ADDRESS_STATE, Patient.ADDRESS_ZIP, Patient.PHONE};
            for (String field : fields) {
              out.println("      <tr>");
              out.println("        <th>" + field + "</th>");
              for (MatchItem matchItem : signatureMap.get(signature)) {
                Patient patientA = matchItem.getPatientA();
                Patient patientB = matchItem.getPatientB();
                String style = "";
                String valueA = patientA.getValue(field);
                String valueB = patientB.getValue(field);
                if (!valueA.equals("") && !valueB.equals("")) {
                  boolean matches = valueA.equalsIgnoreCase(valueB);
                  style = matches ? "pass" : "fail";
                }
                out.println("        <td class=\"" + style + "\">" + valueA + "</td>");
                out.println("        <td class=\"" + style + "\">" + valueB + "</td>");
            
              }
              out.println("      </tr>");
            }
            out.println("    </table>");
          }
        }
        if (pos == 0) {
          out.println("<p>None found</p>");
        }    
      }
      HomeServlet.doFooter(out, req);

    } catch (Exception e) {
      e.printStackTrace(out);
    }
    finally {
      out.close();
      teardown(req, resp);
    }
  }

  private void printSublist(PrintWriter out, String sublistName, List<MatchItem> sublist) {
    out.println("    <table border=\"1\" cellspacing=\"0\">");
    out.println("      <tr><th>Test Case</th><th>Status</th><th>Description</th><th>Expected</th><th>Actual</th></tr>");
    for (MatchItem matchItem : sublist) {
      String link = "TestSetServlet?" + TestSetServlet.PARAM_MATCH_SET_ID + "="
          + matchItem.getMatchSet().getMatchSetId() + "&" + TestSetServlet.PARAM_MATCH_ITEM_ID + "="
          + matchItem.getMatchItemId() + "&" + TestSetServlet.PARAM_SUBLIST_NAME + "=" + sublistName;
      printRow(out, matchItem, link);
    }
    out.println("    </table>");
  }

  private void printRow(PrintWriter out, MatchItem matchItem, String link) {
    String style = "";
    if (matchItem.isExpectedStatusSet()) {
      boolean passed = matchItem.isPass();
      style = passed ? "pass" : "fail";
      out.println("      <tr>");
      out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchItem.getLabel() + "</a></td>");
      out.println("        <td class=\"" + style + "\">" + (passed ? "Passed" : "Fail") + "</td>");
      if (!matchItem.getDescription().equals("")) {
        out.println("        <td class=\"" + style + "\">" + matchItem.getDescription() + "</td>");
      } else {
        out.println("        <td class=\"" + style + "\">&nbsp;</td>");
      }
      out.println("        <td class=\"" + style + "\">" + matchItem.getExpectStatus() + "</td>");
      out.println("        <td class=\"" + style + "\">" + matchItem.getActualStatus() + "</td>");
      out.println("      </tr>");
    } else {
      out.println("      <tr>");
      out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchItem.getLabel() + "</a></td>");
      out.println("        <td class=\"" + style + "\">Not run</td>");
      if (!matchItem.getDescription().equals("")) {
        out.println("        <td class=\"" + style + "\">" + matchItem.getDescription() + "</td>");
      } else {
        out.println("        <td class=\"" + style + "\">&nbsp;</td>");
      }
      out.println("        <td class=\"" + style + "\">" + matchItem.getExpectStatus() + "</td>");
      out.println("        <td class=\"" + style + "\">&nbsp;</td>");
      out.println("      </tr>");

    }
  }

  private static String readValue(String s) {
    int pos = s.indexOf(":");
    if (pos == -1) {
      return "";
    } else {
      return s.substring(pos + 1).trim();
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    // TODO Auto-generated method stub
    doGet(req, resp);
  }
}
