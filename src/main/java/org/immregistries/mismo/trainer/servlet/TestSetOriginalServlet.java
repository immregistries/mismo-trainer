package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
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
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.model.Scorer;


/**
 * This servlet tests a set of match test cases against a given script to give a
 * summary of how well the weights work.
 * 
 * @author Nathan Bunker
 * 
 */
public class TestSetOriginalServlet extends HomeServlet {


  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    setup(req, resp);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      HttpSession session = req.getSession(true);
      User user = (User) session.getAttribute(ATTRIBUTE_USER);
      if (user == null) {
        RequestDispatcher dispatcher = req.getRequestDispatcher("HomeServlet");
        dispatcher.forward(req, resp);
        return;
      }

      Session dataSession = (Session) session.getAttribute(ATTRIBUTE_DATA_SESSION);
      String message = req.getParameter(PARAM_MESSAGE);

      MatchSet matchSetSelected = (MatchSet) session.getAttribute(TestSetServlet.ATTRIBUTE_MATCH_SET);
      List<MatchItem> matchItemList = (List<MatchItem>) session.getAttribute(TestSetServlet.ATTRIBUTE_MATCH_ITEM_LIST);
      
      

      PatientCompare patientCompare = (PatientCompare) session
          .getAttribute(TestMatchingServlet.ATTRIBUTE_PATIENT_COMPARE);
      String action = req.getParameter(PARAM_ACTION);
      if (action != null) {
        // nothing to do yet
      }

      out.println(
          "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ");
      HomeServlet.doHeader(out, user, null);
      out.println("    <h1>Test Set</h1>");
      if (message != null) {
        out.println("<p>" + message + "</p>");
      }

      if (matchSetSelected != null && matchItemList != null) {

        Scorer scorer;
        if (patientCompare != null && patientCompare.getConfiguration() != null) {
          scorer = new Scorer(patientCompare.getConfiguration().getScoringWeights());
        } else {
          scorer = new Scorer();
        }

        org.immregistries.pm.PatientMatcher patientMatcherOriginal = new org.immregistries.pm.PatientMatcher();

        out.println("<h2>" + matchSetSelected.getLabel() + "</h2>");

        if (matchItemList.size() > 0) {

          if (patientCompare != null) {

            out.println("   <table border=\"1\" cellspacing=\"0\">");
            out.println("      <tr>");
            out.println("        <th>#</th>");
            out.println("        <th>Status</th>");
            out.println("        <th>Test Case</th>");
            out.println("        <th>Expected</th>");
            out.println("        <th>Actual</th>");
            out.println("        <th>Actual Original</th>");
            out.println("      </tr>");
            int pos = 0;
            for (MatchItem matchItem : matchItemList) {
              pos++;
              String link = "MatchPatientServlet?"  + TestSetServlet.PARAM_MATCH_ITEM_ID + "=" + matchItem.getMatchItemId();
              String linkOriginal = "MatchPatientOriginalServlet?"  + TestSetServlet.PARAM_MATCH_ITEM_ID + "=" + matchItem.getMatchItemId();
              String style = "";
              String combo = "";
              if (matchItem.isExpectedStatusSet() && !matchItem.isTested()) {
                updatePassStatus(matchItem, patientCompare);
              }
              org.immregistries.pm.PatientMatchDetermination patientMatchDeterminationOriginal = null;
              {
                org.immregistries.pm.model.Patient patientA = new org.immregistries.pm.model.Patient(matchItem.getPatientA().getValues());
                org.immregistries.pm.model.Patient patientB = new org.immregistries.pm.model.Patient(matchItem.getPatientB().getValues());
                patientMatchDeterminationOriginal = patientMatcherOriginal.match(patientA, patientB);
                combo = matchItem.getActualStatus() + " " + patientMatchDeterminationOriginal;
                if (patientMatchDeterminationOriginal == org.immregistries.pm.PatientMatchDetermination.MATCH) {
                  if (matchItem.getActualStatus().equals(MatchItem.MATCH))
                  {
                    style = "pass";
                  }
                  else {
                    style = "fail";
                  }
                } else if (patientMatchDeterminationOriginal == org.immregistries.pm.PatientMatchDetermination.POSSIBLE_MATCH) {
                  if (matchItem.getActualStatus() == MatchItem.POSSIBLE_MATCH)
                  {
                    style = "pass";
                  }
                  else {
                    style = "fail";
                  }
                } else if (patientMatchDeterminationOriginal == org.immregistries.pm.PatientMatchDetermination.NO_MATCH) {
                  if (matchItem.getActualStatus() == MatchItem.NOT_A_MATCH)
                  {
                    style = "pass";
                  }
                  else {
                    style = "fail";
                  }
                }
              }
              if (style.equals("fail")) {
                style = "w3-note";
              }
                scorer.registerMatch(matchItem);
                out.println("      <tr>");
                out.println("        <td class=\"" + style + "\">" + pos + "</td>");
                if (style.equals("pass"))
                {
                  out.println("        <td class=\"" + style + "\">same</td>");
                } else {
                  out.println("        <td class=\"" + style + "\">DIFFERENT</td>");
                }
                out.println("        <td class=\"" + style + "\">" + matchItem.getLabel() + "</td>");
                out.println("        <td class=\"" + style + "\">" + matchItem.getExpectStatus() + "</td>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchItem.getActualStatus() + "</a></td>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + linkOriginal + "\">" + patientMatchDeterminationOriginal + "</a></td>");
                out.println("      </tr>");
            }
            out.println("    </table>");

            out.println("    <br/>");
            if (scorer != null) {
              out.println("    <table border=\"1\" cellspacing=\"0\">");
              out.println("      <tr><th>&nbsp;</th><th>Matched</th><th>Possible</th><th>Not Matched</th></tr>");
              int[][] c = scorer.getCountTable();
              out.println("      <tr><th>Should Match</th><td>" + c[0][0] + "</td><td>" + c[0][1] + "<td>" + c[0][2]
                  + "</td></td>");
              out.println("      <tr><th>Should Possible</th><td>" + c[1][0] + "</td><td>" + c[1][1] + "<td>" + c[1][2]
                  + "</td></td>");
              out.println("      <tr><th>Should Not Match</th><td>" + c[2][0] + "</td><td>" + c[2][1] + "<td>"
                  + c[2][2] + "</td></td>");
              out.println("      </tr>");
              out.println("    </table>");
              DecimalFormat decimalFormat = new DecimalFormat("#0.00");
              out.println("    <p>Overall Score: " + decimalFormat.format((scorer.getScore() * 100.0)) + "%</p>");
            }

          } else {
            out.println("   <table border=\"1\" cellspacing=\"0\">");
            out.println("      <tr>");
            out.println("        <th>#</th>");
            out.println("        <th>Test Case</th>");
            out.println("        <th>Expected</th>");
            out.println("      </tr>");
            int pos = 0;
            for (MatchItem matchItem : matchItemList) {
              pos++;
              String link = "TestSetServlet?" + TestSetServlet.PARAM_MATCH_SET_ID + "=" + matchSetSelected.getMatchSetId() + "&"
                  + TestSetServlet.PARAM_MATCH_ITEM_ID + "=" + matchItem.getMatchItemId();
              out.println("      <tr>");
              out.println("        <td>" + pos + "</td>");
              out.println("        <td><a href=\"" + link + "\">" + matchItem.getLabel() + "</a></td>");
              out.println("        <td><a href=\"" + link + "\">" + matchItem.getExpectStatus() + "</a></td>");
              out.println("      </tr>");
            }
          }
          out.println("    </table><br/>");

        }

      }

      HomeServlet.doFooter(out, req);

    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    } finally {
      out.close();
      teardown(req, resp);
    }
  }

  private void updatePassStatus(MatchItem matchItemSelected, PatientCompare patientCompare) {
    if (matchItemSelected.isExpectedStatusSet()) {
      patientCompare.setMatchItem(matchItemSelected);
      boolean passed = patientCompare.getResult().equals(matchItemSelected.getExpectStatus());
      matchItemSelected.setTested(true);
      matchItemSelected.setPass(passed);
      matchItemSelected.setActualStatus(patientCompare.getResult());
    } else {
      matchItemSelected.setTested(false);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    // TODO Auto-generated method stub
    doGet(req, resp);
  }

}
