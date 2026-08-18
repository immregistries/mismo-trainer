package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Session;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.trainer.model.MatchSet;
import org.immregistries.mismo.trainer.model.Scorer;
import org.immregistries.mismo.trainer.model.User;


/**
 * This servlet tests a set of match test cases against a given script to give a
 * summary of how well the weights work.
 * 
 * @author Nathan Bunker
 * 
 */
public class TestSetOriginalServlet extends HomeServlet {


  protected static final String ORIGINAL_CONFIG = "Generation:0;World Name:MIIS-C;Island Name:Boston19;Score:0.8768426058012363;Born:2012.01.12 07:39:31 MST;Match:0.5976539440304252:0.0::{Household:0.5340047021220341:0.0::{Last Name:0.7394477915760619:0.16083169281725096::{L-match:0.8975774524536817:0.0::}{L-similar:0.5769135677880574:0.0::}{L-hyphenated:0.6:0.0::}}{Guardian:0.3:0.0::{GF-match:0.5:0.0::}{GL-match:0.46027300915789704:0.0::}{MM-match:0.17714551350571317:0.0::}}{Location:0.3:0.0::{PN-match:0.5:0.08589466214027962::}{SA-match:0.9673598834793466:0.4559152605261425::}{S-match:0.19119768322727826:0.0::}}}{Person:0.6:0.0::{Patient Id:0.0:0.0::{MRN-match:0.9:0.008616028169453616::}{SSN-match:0.7:0.4762401412904794:disabled:}{MA-match:0.3854594836970239:0.2912968817242621:disabled:}}{First Name:0.6539430754899886:0.022285108492698935::{F-match:0.9337334077507853:0.11714645904840293::}{F-similar:0.12639866792151896:0.0::}{F-middle:0.3461773101772462:0.007532771294044394::}{A-match:0.4:0.16664034418411822::}{G-match:0.1:0.0::}}{Birth Order:0.5488399547889631:0.0::{BO-matches:0.17056518914444302:0.012133046081512939::}{MBS-no:0.5:0.0::}}{DOB:0.4:0.16099709260030182::{DOB-match:1.0:0.36096395805526216::}{DOB-similar:0.1428016622436511:0.12050050021621805::}}{Middle Name:0.1097499668277081:0.0::{M-match:1.0:0.0::}{M-initial:0.11158025059378063:0.0::}{M-similar:0.05221669388457371:0.0::}{S-match:0.17339445105215423:0.0::}}{Shot History:0.2:0.0::{SH-match:0.8434711196989159:0.0::}}};Not Match:0.44421419906866344:0.12240703311770455::{Household:0.49680346080176185:0.20163533642328246::{Last Name:0.4:0.0::{L-not-match:1.0:0.0:not:}{L-not-similar:0.607962842245958:0.43248744317423493:not:}{L-not-hyphenated:0.6:0.0:not:}}{Guardian:0.3:0.12543614754336277::{GF-not-match:0.8772890703808285:0.0038207325234260736:not:}{GL-not-match:0.5:0.0:not:}{MM-not-match:0.6156030399580353:0.038992047203868174:not:}{MM-not-similar:0.530906191802011:0.2699050811578392:not:}}{Location:0.44207784318945:0.0::{PN-not-match:0.4:0.0:not:}{SA-not-match:0.6625441818813038:0.0920940731120864:not:}{S-not-match:0.3:0.2619251366713534:not:}}}{Person:0.1761757252522036:0.0::{Patient Id:0.4864546336460728:0.0::{MRN-not-match:0.05:0.0:not:}{SSN-not-match:0.9448590837670895:0.24338815020508475:not,disabled:}{MA-not-match:0.8879184240368448:0.0:not,disabled:}}{First Name:0.5803199159650172:0.010349465858667473::{F-not-match:0.3:0.2159008837549209:not:}{F-not-similar:0.3:0.05010846114037204:not:}{F-not-middle:0.2841023560998641:0.028499922506510345:not:}{A-not-match:0.0:0.0:not:}{G-not-match:0.9851122869818935:0.0:not:}}{Birth Order:0.01572755336230108:0.0::}{DOB:0.12408413338661316:0.008017702745740651::{DOB-not-match:0.7785390891446896:0.2683494770446756:not:}{DOB-not-similar:0.8043120733536863:0.035384086215674015:not:}}{Middle Name:0.9194439329366614:0.7527277958973195::{M-not-match:1.0:0.4750592342546145:not:}{M-not-initial:0.9979020506695768:0.0631956946954324:not:}{M-not-similar:0.6:0.04038705921822772:not:}{S-not-match:0.1:0.0:not:}}{Shot History:0.2:0.0::{SH-not-match:0.31664212470651:0.0:not:}}};Suspect Twin:1.0:0.0::{Name Different:0.2:0.0::{F-not-match:0.5:0.0:not:}{F-not-similar:0.6:0.0:not:}{M-not-match:0.5:0.0:not:}{G-not-match:0.5:0.0:not:}}{Birth Date:0.2:0.0::{DOB-match:1.0:0.0::}}{Birth Status:0.6:0.0::{MBS-maybe:0.5:0.0::}{MBS-yes:0.1:0.0::}};Missing:1.0:0.0::{Household:0.87705735766419:0.0::{L-missing:0.7064672667764026:0.0::}{Household:0.4:0.0::{GFN-missing:0.3:0.0::}{GLN-missing:0.19190311458033785:0.014397094425822488::}{MMN-missing:0.3:0.0::}}{Location:0.7208979096176452:0.0::{PN-missing:0.2:0.0::}{AS1-missing:0.4:0.0::}{AS2-missing:0.0:0.0::}{AC-missing:0.0:0.0::}{AS-missing:0.05:0.0::}{AZ-missing:0.0:0.0::}}}{Person:0.6:0.026859283296303826::{Patient Id:0.3:0.28262972979105583::{MRN-missing:0.9103716296607758:0.007178047110102764::}{SSN-missing:0.4:0.0:disabled:}{MA-missing:0.4:0.0:disabled:}}{First Name:0.3:0.0::{F-missing:1.0:0.0::}{A-missing:0.0543372636540701:0.0::}{S-missing:0.0:0.0::}{G-missing:0.4464521217969287:0.0::}}{Birth Order:0.1:0.0::{MBS-missing:1.0:0.0::}{BM-missing:0.5:0.0::}{BO-missing:0.6017422488947954:0.0::}}{DOB-missing:0.4:0.0::}{M-missing:0.2:0.0::}{SH-missing:0.2:0.0::}}";

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

        org.immregistries.pm.PatientMatcher patientMatcherOriginal = new org.immregistries.pm.PatientMatcher(ORIGINAL_CONFIG);

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
