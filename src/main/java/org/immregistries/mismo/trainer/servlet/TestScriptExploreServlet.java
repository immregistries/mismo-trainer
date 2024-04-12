package org.immregistries.mismo.trainer.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;

/**
 * This servlet tests a set of match test cases against a given script to give a
 * summary of how well the weights work.
 * 
 * @author Nathan Bunker
 * 
 */
public class TestScriptExploreServlet extends HomeServlet
{
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    HttpSession session = req.getSession(true);
    User user = (User) session.getAttribute(TestSetServlet.ATTRIBUTE_USER);
    try {
      String testScript = req.getParameter("testScript");
      if (testScript == null) {
        testScript = (String) session.getAttribute("testScript");
        if (testScript == null) {
          testScript = "";
        }
      }
      List<MatchItem> matchItemList = new ArrayList<MatchItem>();
      session.setAttribute(TestMatchingServlet.ATTRIBUTE_MATCH_TEST_CASE_LIST, matchItemList);
      if (!testScript.equals("")) {
        MatchItem matchTestCase = null;

        BufferedReader in = null;
        for (String possibleScript : TestMatchingServlet.TEST_SCRIPTS) {
          if (testScript.equals(possibleScript)) {
            in = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(possibleScript + ".txt")));
            break;
          }
        }

        String line = "";
        while ((line = in.readLine()) != null) {
          if (line.startsWith("TEST:")) {
            if (matchTestCase != null) {
              matchItemList.add(matchTestCase);
            }
            matchTestCase = new MatchItem();
            matchTestCase.setLabel(readValue(line));
          } else if (matchTestCase != null) {
            if (line.startsWith("EXPECT:")) {
              matchTestCase.setExpectStatus(readValue(line));
            } else if (line.startsWith("PATIENT A:")) {
              matchTestCase.setPatientDataA(readValue(line));
            } else if (line.startsWith("PATIENT B:")) {
              matchTestCase.setPatientDataB(readValue(line));
            } else if (line.startsWith("DESCRIPTION:")) {
              matchTestCase.setDescription(readValue(line));
            }
          }
        }
        if (matchTestCase != null) {
          matchItemList.add(matchTestCase);
        }

      }
      HomeServlet.doHeader(out, user, null);
      out.println("    <h1>Test Script Explore</h1>");
      out.println("    <form action=\"TestScriptExploreServlet\" method=\"POST\"> ");
      out.println("    <table>");
      out.println("      <tr>");
      out.println("        <td valign=\"top\">Test Script</td>");
      out.println("        <td><select name=\"testScript\">");
      for (String possibleScript : TestMatchingServlet.TEST_SCRIPTS) {
        out.println("          <option value=\"" + possibleScript + "\">" + possibleScript + "</option>");
      }
      out.println("           </select>");
      out.println("        </td>");
      out.println("      </tr>");
      out.println("      <tr><td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"submit\" value=\"Submit\"></td></tr>");
      out.println("    </table>");
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr><th>Test Case</th><th>Status</th><th>A Last Name</th><th>B Last Name</th></tr>");
      for (MatchItem matchItem : matchItemList) {
        Patient patientA = new Patient(matchItem.getPatientDataA());
        Patient patientB = new Patient(matchItem.getPatientDataB());

        if (matchItem.getExpectStatus().equals("Match")
            && (!patientA.getNameFirst().equals(patientB.getNameFirst())
                || !patientA.getNameLast().equals(patientB.getNameLast()) || !patientA.getBirthDate().equals(
                patientB.getBirthDate()))) {
          String link = "MatchPatientServlet?testId=" + URLEncoder.encode(matchItem.getLabel(), "UTF-8");
          out.println("      <tr>");
          out.println("        <td><a href=\"" + link + "\">" + matchItem.getLabel() + "</a></td>");
          out.println("        <td>" + matchItem.getExpectStatus() + "</td>");
          out.println("        <td>" + patientA.getNameLast() + "</td>");
          out.println("        <td>" + patientB.getNameLast() + "</td>");
          out.println("      </tr>");
          matchItem.setExpectStatus("Possible Match");
        }
      }
      out.println("    </table>");
      out.println("<pre>");
      for (MatchItem matchItem : matchItemList) {
        out.println("TEST: " + matchItem.getLabel());
        out.println("EXPECT: " + matchItem.getExpectStatus());
        out.println("PATIENT A: " + matchItem.getPatientDataA());
        out.println("PATIENT B: " + matchItem.getPatientDataB());
      }
      out.println("</pre>");
      HomeServlet.doFooter(out, req);

    } catch (Exception e) {
      e.printStackTrace(out);
    }
    finally {
      teardown(req, resp);
      out.close();
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
