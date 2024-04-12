package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Query;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.Configuration;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.MatchSet;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.model.Scorer;

/**
 * This servlet tests a set of match test cases against a given script to give a
 * summary of how well the weights work.
 * 
 * @author Nathan Bunker
 * 
 */
public class TestMatchingServlet extends HomeServlet
{
  protected static final String[] TEST_SCRIPTS = { "MIIS-B", "MIIS-C", "MIIS-D", "MIIS-E", "MIIS-E2", "MIIS-E3", "MIIS-F1", "MIIS-F2" };


  public static final String PARAM_CONFIGURATION_SCRIPT = "configurationScript";

  public static final String PARAM_CONFIGURATION_ID = "configurationId";

  public static final String PARAM_TEST_SCRIPT = "testScript";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    Session dataSession = (Session) session.getAttribute(TestSetServlet.ATTRIUBTE_DATA_SESSION);
    try {
      MatchSet matchSetSelected = null;
      String testScript = null;
      if (user != null) {
        if (req.getParameter(TestSetServlet.PARAM_MATCH_SET_ID) != null) {
          matchSetSelected = (MatchSet) dataSession.get(MatchSet.class,
              Integer.parseInt(req.getParameter(TestSetServlet.PARAM_MATCH_SET_ID)));
        } else if (session.getAttribute(TestSetServlet.ATTRIBUTE_MATCH_SET) != null) {
          matchSetSelected = (MatchSet) session.getAttribute(TestSetServlet.ATTRIBUTE_MATCH_SET);
        }
        session.setAttribute(TestSetServlet.ATTRIBUTE_MATCH_SET, matchSetSelected);
        testScript = "";
      } else {
        testScript = req.getParameter(PARAM_TEST_SCRIPT);
        if (testScript == null) {
          testScript = (String) session.getAttribute(PARAM_TEST_SCRIPT);
          if (testScript == null) {
            testScript = "";
          }
        }
      }
      PatientCompare patientCompare = (PatientCompare) session.getAttribute(ATTRIBUTE_PATIENT_COMPARE);
      
      List<MatchItem> matchItemList = new ArrayList<MatchItem>();
      session.setAttribute(ATTRIBUTE_MATCH_TEST_CASE_LIST, matchItemList);
      if (matchSetSelected != null) {
        Query query = dataSession.createQuery("from MatchItem where matchSet = ? order by dataSource, updateDate");
        query.setParameter(0, matchSetSelected);
        matchItemList = query.list();
      } else if (!testScript.equals("")) {
        MatchItem matchItem = null;
        BufferedReader in = null;
        for (String possibleScript : TEST_SCRIPTS) {
          if (testScript.equals(possibleScript)) {
            in = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(possibleScript + ".txt")));
            break;
          }
        }
        String line = "";
        while ((line = in.readLine()) != null) {
          if (line.startsWith("TEST:")) {
            if (matchItem != null) {
              matchItemList.add(matchItem);
            }
            matchItem = new MatchItem();
            matchItem.setLabel(readValue(line));
          } else if (matchItem != null) {
            if (line.startsWith("EXPECT:")) {
              matchItem.setExpectStatus(readValue(line));
            } else if (line.startsWith("PATIENT A:")) {
              matchItem.setPatientDataA(readValue(line));
            } else if (line.startsWith("PATIENT B:")) {
              matchItem.setPatientDataB(readValue(line));
            } else if (line.startsWith("DESCRIPTION:")) {
              matchItem.setDescription(readValue(line));
            }
          }
        }
        if (matchItem != null) {
          matchItemList.add(matchItem);
        }
      }

      HomeServlet.doHeader(out, user, null);
      out.println("    <h1>Test Matching</h1>");
      out.println("    <form action=\"TestMatchingServlet\" method=\"POST\"> ");
      out.println("    <table>");
      out.println("      <tr>");
      if (user == null) {
        out.println("        <td valign=\"top\">Test Script</td>");
        out.println("        <td><select name=\"" + PARAM_TEST_SCRIPT + "\">");
        for (String possibleScript : TEST_SCRIPTS) {
          out.println("          <option value=\"" + possibleScript + "\">" + possibleScript + "</option>");
        }
        out.println("           </select>");
      } else {
        out.println("        <td valign=\"top\">Match Set</td>");
        out.println("        <td><select name=\"" + TestSetServlet.PARAM_MATCH_SET_ID + "\">");
        Query query = dataSession.createQuery("from MatchSet order by updateDate");
        List<MatchSet> matchSetList = query.list();
        for (MatchSet matchSet : matchSetList) {
          if (matchSetSelected != null && matchSetSelected.equals(matchSet)) {
            out.println("          <option value=\"" + matchSet.getMatchSetId() + "\" selected=\"true\">"
                + matchSet.getLabel() + "</option>");
          } else {
            out.println("          <option value=\"" + matchSet.getMatchSetId() + "\">" + matchSet.getLabel()
                + "</option>");
          }
        }
        out.println("           </select>");

      }
      out.println("        </td>");
      out.println("      </tr>");
      out.println("      <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"submit\" value=\"Submit\"></td></tr>");
      out.println("    </table>");
      if (matchItemList.size() > 0) {
        Scorer scorer = new Scorer();
        Set<MatchItem> failSet = new HashSet<MatchItem>();
        Map<String, List<MatchItem>> signatureMap = new HashMap<String, List<MatchItem>>();
        out.println("    <table border=\"1\" cellspacing=\"0\">");
        out.println("      <tr><th>Test Case</th><th>Status</th><th>Description</th><th>Expected</th><th>Actual</th></tr>");
        for (MatchItem matchTestCase : matchItemList) {
          String link = "MatchPatientServlet?" + TestSetServlet.PARAM_MATCH_ITEM_ID + "=" + URLEncoder.encode(matchTestCase.getLabel(), "UTF-8");
          String style = "";
          if (matchTestCase.isExpectedStatusSet()) {
            patientCompare.setMatchItem(matchTestCase);
            boolean passed = patientCompare.getResult().equals(matchTestCase.getExpectStatus());
            if (!passed) {
              failSet.add(matchTestCase);
            }
            style = passed ? "pass" : "fail";
            out.println("      <tr>");
            out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchTestCase.getLabel()
                + "</a></td>");
            out.println("        <td class=\"" + style + "\">" + (passed ? "Passed" : "Fail") + "</td>");
            if (!matchTestCase.getDescription().equals("")) {
              out.println("        <td class=\"" + style + "\">" + matchTestCase.getDescription() + "</td>");
            } else {
              out.println("        <td class=\"" + style + "\">&nbsp;</td>");
            }
            out.println("        <td class=\"" + style + "\">" + matchTestCase.getExpectStatus() + "</td>");
            out.println("        <td class=\"" + style + "\">" + patientCompare.getResult() + "</td>");
            String signature = patientCompare.getSignature();
            List<MatchItem> signatureList = signatureMap.get(signature);
            if (signatureList == null) {
              signatureList = new ArrayList<MatchItem>();
              signatureMap.put(signature, signatureList);
            }
            signatureList.add(matchTestCase);
            out.println("      </tr>");
            scorer.registerMatch(matchTestCase, patientCompare);
          } else {
            out.println("      <tr>");
            out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchTestCase.getLabel()
                + "</a></td>");
            out.println("        <td class=\"" + style + "\">Not run</td>");
            if (!matchTestCase.getDescription().equals("")) {
              out.println("        <td class=\"" + style + "\">" + matchTestCase.getDescription() + "</td>");
            } else {
              out.println("        <td class=\"" + style + "\">&nbsp;</td>");
            }
            out.println("        <td class=\"" + style + "\">" + matchTestCase.getExpectStatus() + "</td>");
            out.println("        <td class=\"" + style + "\">&nbsp;</td>");
            out.println("      </tr>");

          }
        }
        out.println("    </table>");
        out.println("    <br>");
        out.println("    <table border=\"1\" cellspacing=\"0\">");
        out.println("      <tr><th>&nbsp;</th><th>Matched</th><th>Possible</th><th>Not Matched</th></tr>");
        int[][] c = scorer.getCountTable();
        out.println("      <tr><th>Should Match</th><td>" + c[0][0] + "</td><td>" + c[0][1] + "<td>" + c[0][2]
            + "</td></td>");
        out.println("      <tr><th>Should Possible</th><td>" + c[1][0] + "</td><td>" + c[1][1] + "<td>" + c[1][2]
            + "</td></td>");
        out.println("      <tr><th>Should Not Match</th><td>" + c[2][0] + "</td><td>" + c[2][1] + "<td>" + c[2][2]
            + "</td></td>");
        out.println("      </tr>");
        out.println("    </table>");
        DecimalFormat decimalFormat = new DecimalFormat("#0.00");
        out.println("    <p>Overall Score: " + decimalFormat.format((scorer.getScore() * 100.0)) + "%</p>");
        out.println("    </form>");

        for (String signature : signatureMap.keySet()) {
          boolean hasFailed = false;
          boolean hasPassed = false;
          for (MatchItem matchItem : signatureMap.get(signature)) {
            if (failSet.contains(matchItem)) {
              hasFailed = true;
            } else {
              hasPassed = true;
            }
          }
          if (hasFailed && hasPassed) {
            out.println("<h2>Signature " + signature + "</h2>");
            out.println("    <table border=\"1\" cellspacing=\"0\">");
            out.println("      <tr><th>Test Case</th><th>Status</th><th>Description</th><th>Expected</th><th>Actual</th></tr>");
            for (MatchItem matchTestCase : signatureMap.get(signature)) {
              String link = "MatchPatientServlet?testId=" + URLEncoder.encode(matchTestCase.getLabel(), "UTF-8");
              String style = "";
              if (matchTestCase.isExpectedStatusSet()) {
                patientCompare.setMatchItem(matchTestCase);
                boolean passed = patientCompare.getResult().equals(matchTestCase.getExpectStatus());
                style = passed ? "pass" : "fail";
                out.println("      <tr>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchTestCase.getLabel()
                    + "</a></td>");
                out.println("        <td class=\"" + style + "\">" + (passed ? "Passed" : "Fail") + "</td>");
                if (!matchTestCase.getDescription().equals("")) {
                  out.println("        <td class=\"" + style + "\">" + matchTestCase.getDescription() + "</td>");
                } else {
                  out.println("        <td class=\"" + style + "\">&nbsp;</td>");
                }
                out.println("        <td class=\"" + style + "\">" + matchTestCase.getExpectStatus() + "</td>");
                out.println("        <td class=\"" + style + "\">" + patientCompare.getResult() + "</td>");
                out.println("      </tr>");
                scorer.registerMatch(matchTestCase, patientCompare);
              } else {
                out.println("      <tr>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchTestCase.getLabel()
                    + "</a></td>");
                out.println("        <td class=\"" + style + "\">Not run</td>");
                if (!matchTestCase.getDescription().equals("")) {
                  out.println("        <td class=\"" + style + "\">" + matchTestCase.getDescription() + "</td>");
                } else {
                  out.println("        <td class=\"" + style + "\">&nbsp;</td>");
                }
                out.println("        <td class=\"" + style + "\">" + matchTestCase.getExpectStatus() + "</td>");
                out.println("        <td class=\"" + style + "\">&nbsp;</td>");
                out.println("      </tr>");

              }
            }
            out.println("    </table>");
            out.println("    <br>");
          }
        }
      }
      HomeServlet.doFooter(out, req);

    } catch (Exception e) {
      e.printStackTrace(out);
    }
    out.close();
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
