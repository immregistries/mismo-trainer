package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.MatchSet;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.match.model.User;
import org.immregistries.mismo.trainer.model.Scorer;

/**
 * This servlet tests a set of match test cases against a given script to give a
 * summary of how well the weights work.
 * 
 * @author Nathan Bunker
 * 
 */
public class TestSetServlet extends HomeServlet
{
  public static final String ACTION_LOAD_DATA = "Load Data";
  public static final String ACTION_CREATE_NEW_MATCH_SET = "Create New Match Set";
  public static final String ACTION_MATCH = "Match";
  public static final String ACTION_POSSIBLE_MATCH = "Possible Match";
  public static final String ACTION_RESEARCH = "Research";
  public static final String ACTION_NOT_SURE = "Not Sure";
  public static final String ACTION_NOT_A_MATCH = "Not a Match";
  public static final String ACTION_SELECT = "Select";
  public static final String ACTION_DOWNLOAD = "Download";

  public static final String PARAM_MATCH_ITEM_ID = "matchItemId";
  public static final String PARAM_MATCH_ITEM_ID_NEXT = "matchItemIdNext";
  public static final String PARAM_MATCH_SET_ID = "matchSetId";
  public static final String PARAM_LABEL = "label";
  public static final String PARAM_DATA_SOURCE = "dataSource";
  public static final String PARAM_DATA_FILE = "dataFile";
  public static final String PARAM_MESSAGE = "message";
  public static final String PARAM_SIGNATURE = "signature";
  public static final String PARAM_SIGNATURE_ALL = "signatureAll";
  public static final String PARAM_SUBLIST_NAME = "sublistName";

  public static final String ATTRIBUTE_MATCH_SET = "matchSet";
  public static final String ATTRIBUTE_MATCH_ITEM_LIST = "matchItemList";
  public static final String ATTRIBUTE_SIGNATURE_MAP = "signatureMap";
  public static final String ATTRIBUTE_SIGNATURE_1_MAP = "signature1Map";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

      Session dataSession = (Session) session.getAttribute(ATTRIUBTE_DATA_SESSION);
      String message = req.getParameter(PARAM_MESSAGE);
      String testScript = req.getParameter("testScript");
      if (testScript == null) {
        testScript = (String) session.getAttribute("testScript");
        if (testScript == null) {
          testScript = "";
        }
      }
      String creatureScript = (String) session.getAttribute(TestMatchingServlet.ATTRIBUTE_CREATURE_SCRIPT);

      MatchSet matchSetSelected = null;
      if (req.getParameter(PARAM_MATCH_SET_ID) != null) {
        matchSetSelected = (MatchSet) dataSession.get(MatchSet.class,
            Integer.parseInt(req.getParameter(PARAM_MATCH_SET_ID)));
      } else if (session.getAttribute(ATTRIBUTE_MATCH_SET) != null) {
        matchSetSelected = (MatchSet) session.getAttribute(ATTRIBUTE_MATCH_SET);
      }
      session.setAttribute(ATTRIBUTE_MATCH_SET, matchSetSelected);

      MatchItem matchItemSelected = null;
      if (req.getParameter(PARAM_MATCH_ITEM_ID) != null) {
        matchItemSelected = (MatchItem) dataSession.get(MatchItem.class,
            Integer.parseInt(req.getParameter(PARAM_MATCH_ITEM_ID)));
      }

      String action = req.getParameter(PARAM_ACTION);
      if (action != null) {
        if (action.equals(ACTION_CREATE_NEW_MATCH_SET)) {
          String label = req.getParameter(PARAM_LABEL);
          MatchSet matchSet = new MatchSet();
          matchSet.setLabel(label);
          matchSet.setUpdateDate(new Date());
          Transaction transaction = dataSession.beginTransaction();
          dataSession.save(matchSet);
          transaction.commit();
        } else if (action.equals(ACTION_MATCH) || action.equals(ACTION_POSSIBLE_MATCH)
            || action.equals(ACTION_NOT_A_MATCH) || action.equals(ACTION_RESEARCH) || action.equals(ACTION_NOT_SURE)) {
          Transaction transaction = dataSession.beginTransaction();
          if (action.equals(ACTION_MATCH)) {
            matchItemSelected.setExpectStatus(MatchItem.MATCH);
          } else if (action.equals(ACTION_POSSIBLE_MATCH)) {
            matchItemSelected.setExpectStatus(MatchItem.POSSIBLE_MATCH);
          } else if (action.equals(ACTION_NOT_A_MATCH)) {
            matchItemSelected.setExpectStatus(MatchItem.NOT_A_MATCH);
          } else if (action.equals(ACTION_RESEARCH)) {
            matchItemSelected.setExpectStatus(MatchItem.RESEARCH);
          } else if (action.equals(ACTION_NOT_SURE)) {
            matchItemSelected.setExpectStatus(MatchItem.NOT_SURE);
          }

          if (creatureScript != null && creatureScript.length() > 0) {
            PatientCompare patientCompare = new PatientCompare();
            patientCompare.readScript(creatureScript);
            updatePassStatus(matchItemSelected, patientCompare);
          }
          matchItemSelected.setUser(user);
          matchItemSelected.setUpdateDate(new Date());
          dataSession.update(matchItemSelected);
          transaction.commit();
          if (req.getParameter(PARAM_MATCH_ITEM_ID_NEXT) != null
              && !req.getParameter(PARAM_MATCH_ITEM_ID_NEXT).equals("")) {
            matchItemSelected = (MatchItem) dataSession.get(MatchItem.class,
                Integer.parseInt(req.getParameter(PARAM_MATCH_ITEM_ID_NEXT)));
          }
        } else if (action.equals(ACTION_SELECT)) {
          Query query = dataSession.createQuery("from MatchItem where matchSet = ? order by label");
          query.setParameter(0, matchSetSelected);
          List<MatchItem> matchItemList = query.list();
          session.setAttribute(ATTRIBUTE_MATCH_ITEM_LIST, matchItemList);
          session.setAttribute(ATTRIBUTE_SIGNATURE_MAP, new HashMap<String, List<MatchItem>>());
          session.setAttribute(ATTRIBUTE_SIGNATURE_1_MAP, new HashMap<String, List<MatchItem>>());
        } else if (action.equals(ACTION_DOWNLOAD)) {
          resp.setContentType("text/plain");
          resp.setHeader("Content-Disposition", "attachment; filename=" + matchSetSelected.getLabel() + ".txt;");
          Query query = dataSession.createQuery("from MatchItem where matchSet = ? order by label");
          query.setParameter(0, matchSetSelected);
          List<MatchItem> matchItemList = query.list();
          for (MatchItem matchItem : matchItemList) {
            out.println("TEST: " + matchItem.getLabel());
            out.println("EXPECT: " + matchItem.getExpectStatus());
            out.println("PATIENT A: " + matchItem.getPatientDataA());
            out.println("PATIENT B: " + matchItem.getPatientDataB());
          }
          return;
        }
      }

      out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ");
      HomeServlet.doHeader(out, user, null);
            out.println("    <h1>Test Set</h1>");
      if (message != null) {
        out.println("<p>" + message + "</p>");
      }

      List<MatchItem> matchItemList = (List<MatchItem>) session.getAttribute(ATTRIBUTE_MATCH_ITEM_LIST);

      String signature = req.getParameter(PARAM_SIGNATURE);
      if (signature != null) {
        Map<String, List<MatchItem>> signatureMap = (Map<String, List<MatchItem>>) session
        .getAttribute(TestSetServlet.ATTRIBUTE_SIGNATURE_MAP);
        matchItemList = signatureMap.get(signature);
      }
      String signatureAll = req.getParameter(PARAM_SIGNATURE_ALL);
      if (signatureAll != null) {
        Map<String, List<MatchItem>> signature1Map = (Map<String, List<MatchItem>>) session
        .getAttribute(TestSetServlet.ATTRIBUTE_SIGNATURE_1_MAP);
        matchItemList = signature1Map.get(signature);
      }
      String sublistName = req.getParameter(PARAM_SUBLIST_NAME);
      if (sublistName != null) {
        matchItemList = (List<MatchItem>) session.getAttribute(sublistName);
      }

      SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
      if (matchItemSelected != null) {
        out.println("<h2>" + matchItemSelected.getLabel() + "</h2>");
        int matchItemIdPrevious = 0;
        int matchItemIdNext = 0;
        int matchItemIdCurrent = 0;
        int matchItemIdNextNotSet = 0;
        int matchItemIdNextFail = 0;
        if (matchItemList != null) {
          int pos = 0;
          int posCurrent = 0;
          for (MatchItem mi : matchItemList) {
            pos++;
            if (mi.equals(matchItemSelected)) {
              matchItemIdCurrent = mi.getMatchItemId();
              posCurrent = pos;
            } else if (matchItemIdCurrent == 0) {
              matchItemIdPrevious = mi.getMatchItemId();
            } else if (matchItemIdNext == 0) {
              matchItemIdNext = mi.getMatchItemId();
            }
            if (matchItemIdNext > 0) {
              if (matchItemIdNextNotSet == 0 && !mi.isExpectedStatusSet()) {
                matchItemIdNextNotSet = mi.getMatchItemId();
              }
              if (matchItemIdNextFail == 0 && mi.isTested() && !mi.isPass()) {
                matchItemIdNextFail = mi.getMatchItemId();
              }
            }
            if (matchItemIdNextNotSet > 0 && matchItemIdNextFail > 0) {
              break;
            }
          }
          String link = "TestSetServlet?" + PARAM_MATCH_SET_ID + "=" + matchSetSelected.getMatchSetId();
          if (signature != null) {
            link += "&" + PARAM_SIGNATURE + "=" + signature;
          }
          if (signatureAll != null) {
            link += "&" + PARAM_SIGNATURE_ALL + "=" + signatureAll;
          }
          if (sublistName != null)
          {
            link += "&" + PARAM_SUBLIST_NAME + "=" + sublistName;
          }
          link += "&" + PARAM_MATCH_ITEM_ID + "=";
          out.println("<div class=\"navMenu\">");
          if (matchItemIdPrevious > 0) {
            out.println("<a class=\"navMenuLink\" href=\"" + link + matchItemIdPrevious + "\">Previous</a>");
          }
          if (signature == null) {
            out.println("Test Set " + posCurrent + " of " + matchItemList.size());
          } else if (sublistName == null) {
            out.println("Sub Fail List Set " + posCurrent + " of " + matchItemList.size());
          } else {
            out.println("Review Set " + posCurrent + " of " + matchItemList.size());
          }
          if (matchItemIdNext > 0) {
            out.println("<a class=\"navMenuLink\" href=\"" + link + matchItemIdNext + "\">Next</a>");
          }
          if (matchItemIdNextNotSet > 0 || matchItemIdNextFail > 0) {
            out.println("Jump To");
          }
          if (matchItemIdNextNotSet > 0) {
            out.println("<a class=\"navMenuLink\" href=\"" + link + matchItemIdNextNotSet + "\">Next Not Set</a>");
          }
          if (matchItemIdNextFail > 0) {
            out.println("<a class=\"navMenuLink\" href=\"" + link + matchItemIdNextFail + "\">Next Fail</a>");
          }
          out.println("</div>");
        }
        out.println("   <table border=\"1\" cellspacing=\"0\">");
        out.println("      <tr>");
        out.println("        <th>Field</th>");
        out.println("        <th>Patient A</th>");
        out.println("        <th>Patient B</th>");
        out.println("      </tr>");
        printMatchRow(out, matchItemSelected, "Birth Date", Patient.BIRTH_DATE);
        printMatchRow(out, matchItemSelected, "Name First", Patient.NAME_FIRST);
        printMatchRow(out, matchItemSelected, "Name Middle", Patient.NAME_MIDDLE);
        printMatchRow(out, matchItemSelected, "Name Last", Patient.NAME_LAST);
        printMatchRow(out, matchItemSelected, "Name Suffix", Patient.NAME_SUFFIX);
        printMatchRow(out, matchItemSelected, "Name Alias", Patient.NAME_ALIAS);
        printMatchRow(out, matchItemSelected, "Guardian Name Last", Patient.GUARDIAN_NAME_LAST);
        printMatchRow(out, matchItemSelected, "Guardian Name First", Patient.GUARDIAN_NAME_FIRST);
        printMatchRow(out, matchItemSelected, "Mother Maiden Name", Patient.MOTHER_MAIDEN_NAME);
        printMatchRow(out, matchItemSelected, "Address Street 1", Patient.ADDRESS_STREET1);
        printMatchRow(out, matchItemSelected, "Address Street 2", Patient.ADDRESS_STREET2);
        printMatchRow(out, matchItemSelected, "Address City", Patient.ADDRESS_CITY);
        printMatchRow(out, matchItemSelected, "Address State", Patient.ADDRESS_STATE);
        printMatchRow(out, matchItemSelected, "Address Zip", Patient.ADDRESS_ZIP);
        printMatchRow(out, matchItemSelected, "2nd Address Street 1", Patient.ADDRESS_2_STREET1);
        printMatchRow(out, matchItemSelected, "2nd Address Street 2", Patient.ADDRESS_2_STREET2);
        printMatchRow(out, matchItemSelected, "2nd Address City", Patient.ADDRESS_2_CITY);
        printMatchRow(out, matchItemSelected, "2nd Address State", Patient.ADDRESS_2_STATE);
        printMatchRow(out, matchItemSelected, "2nd Address Zip", Patient.ADDRESS_2_ZIP);
        printMatchRow(out, matchItemSelected, "Phone", Patient.PHONE);
        printMatchRow(out, matchItemSelected, "Gender", Patient.GENDER);
        printMatchRow(out, matchItemSelected, "MRNs", Patient.MRNS);
        printMatchRow(out, matchItemSelected, "Birth Type", Patient.BIRTH_TYPE);
        printMatchRow(out, matchItemSelected, "Birth Order", Patient.BIRTH_ORDER);
        printMatchRow(out, matchItemSelected, "Birth Status", Patient.BIRTH_STATUS);
        printMatchRow(out, matchItemSelected, "Shot History", Patient.SHOT_HISTORY);
        printMatchRow(out, matchItemSelected, "SSN", Patient.SSN);
        printMatchRow(out, matchItemSelected, "Medicaid #", Patient.MEDICAID);

        out.println("    </table>");
        String link = "MatchPatientServlet?" + PARAM_MATCH_ITEM_ID + "=" + matchItemSelected.getMatchItemId();
        out.println("    <p><a href=\"" + link + "\">Matching Diagnostics</a></p>");

        out.println("    <h3>Review Item</h3>");
        out.println("    <form action=\"TestSetServlet\" method=\"POST\"> ");
        out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
            + matchSetSelected.getMatchSetId() + "\"/>");
        out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_ITEM_ID + "\" value=\""
            + matchItemSelected.getMatchItemId() + "\"/>");
        if (signature != null) {
          out.println("    <input type=\"hidden\" name=\"" + PARAM_SIGNATURE + "\" value=\"" + signature + "\"/>");
        }
        if (signatureAll != null) {
          out.println("    <input type=\"hidden\" name=\"" + PARAM_SIGNATURE_ALL + "\" value=\"" + signatureAll + "\"/>");
        }
        if (sublistName != null) {
          out.println("    <input type=\"hidden\" name=\"" + PARAM_SUBLIST_NAME + "\" value=\"" + sublistName + "\"/>");
        }
        out.println("    <table border=\"1\" cellspacing=\"0\">");
        out.println("      <tr>");
        out.println("        <th>Expected Result</th>");
        out.println("        <td>" + matchItemSelected.getExpectStatus() + "</td>");
        out.println("      </tr>");
        if (matchItemSelected.isTested()) {
          out.println("      <tr>");
          out.println("        <th>Actual Result</th>");
          out.println("        <td>" + matchItemSelected.getActualStatus() + "</td>");
          out.println("      </tr>");
          String style = matchItemSelected.isPass() ? "pass" : "fail";
          out.println("      <tr>");
          out.println("        <th>Pass/Fail</th>");
          out.println("        <td class=\"" + style + "\">" + (matchItemSelected.isPass() ? "Pass" : "Fail") + "</td>");
          out.println("      </tr>");
        } else {
          out.println("      <tr>");
          out.println("        <th>Actual Result</th>");
          out.println("        <td>not tested</td>");
          out.println("      </tr>");
        }
        out.println("      <tr>");
        out.println("        <th>Last Updated By</th>");
        out.println("        <td>" + matchItemSelected.getUser().getName() + "</td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <th>Last Updated</th>");
        out.println("        <td>" + sdf.format(matchItemSelected.getUpdateDate()) + "</td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <th>Advance To</th>");
        out.println("        <td>");
        out.println("          <select name=\"" + PARAM_MATCH_ITEM_ID_NEXT + "\">");
        if (matchItemIdNext > 0) {
          out.println("            <option value=\"" + matchItemIdNext + "\">Next</option>");
        }
        if (matchItemIdNextNotSet > 0) {
          out.println("            <option value=\"" + matchItemIdNextNotSet + "\">Next Not Set</option>");
        }
        if (matchItemIdNextFail > 0) {
          out.println("            <option value=\"" + matchItemIdNextFail + "\">Next Fail</option>");
        }
        if (matchItemIdPrevious > 0) {
          out.println("            <option value=\"" + matchItemIdPrevious + "\">Previous</option>");
        }
        out.println("          </select>");
        out.println("        </td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <td colspan=\"2\" align=\"right\">");
        out.println("          <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_MATCH + "\"/>");
        out.println("          <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_POSSIBLE_MATCH
            + "\"/>");
        out.println("          <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_NOT_A_MATCH
            + "\"/>");
        out.println("          <br/>");
        out.println("          <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_RESEARCH
            + "\"/>");
        out.println("          <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_NOT_SURE
            + "\"/>");
        out.println("        </td>");
        out.println("      </tr>");
        out.println("    </table>");
        out.println("    </form>");

      } else if (matchSetSelected != null && matchItemList != null) {

        Map<String, List<MatchItem>> signatureMap = (Map<String, List<MatchItem>>) session
            .getAttribute(ATTRIBUTE_SIGNATURE_MAP);
        Map<String, List<MatchItem>> signature1Map = (Map<String, List<MatchItem>>) session
            .getAttribute(ATTRIBUTE_SIGNATURE_1_MAP);
            

        out.println("<h2>" + matchSetSelected.getLabel() + "</h2>");
        Scorer scorer = null;

        if (matchItemList.size() > 0) {

          if (creatureScript != null && creatureScript.length() > 0) {
            PatientCompare patientCompare = null;

            out.println("   <table border=\"1\" cellspacing=\"0\">");
            out.println("      <tr>");
            out.println("        <th>#</th>");
            out.println("        <th>Status</th>");
            out.println("        <th>Test Case</th>");
            out.println("        <th>Expected</th>");
            out.println("        <th>Actual</th>");
            out.println("        <th>Signature</th>");
            out.println("      </tr>");
            int pos = 0;
            for (MatchItem matchItem : matchItemList) {
              pos++;
              String link = "TestSetServlet?" + PARAM_MATCH_SET_ID + "=" + matchSetSelected.getMatchSetId() + "&"
                  + PARAM_MATCH_ITEM_ID + "=" + matchItem.getMatchItemId();
              String style = "";
              if (matchItem.isExpectedStatusSet() && !matchItem.isTested()) {
                if (patientCompare == null) {
                  patientCompare = new PatientCompare();
                  patientCompare.readScript(creatureScript);
                }
                updatePassStatus(matchItem, patientCompare);
                signature = patientCompare.getSignature();

                List<MatchItem> signatureList = signatureMap.get(signature);
                if (signatureList == null) {
                  signatureList = new ArrayList<MatchItem>();
                  signatureMap.put(signature, signatureList);
                }
                signatureList.add(matchItem);

                signatureAll = patientCompare.getScoreListSignature();
                String signature1 = signatureAll;
                int posOfColon = signature1.indexOf(":");
                if (posOfColon > 0) {
                  posOfColon = signature1.indexOf(":", posOfColon + 1);
                  if (posOfColon > 0) {
                    signature1 = signature1.substring(0, posOfColon);
                  }
                }
                List<MatchItem> signature1List = signatureMap.get(signature1);
                if (signature1List == null) {
                  signature1List = new ArrayList<MatchItem>();
                  signature1Map.put(signature1, signature1List);
                }
                signature1List.add(matchItem);

              }
              if (matchItem.isTested()) {
                if (scorer == null) {
                  scorer = new Scorer();
                }
                scorer.registerMatch(matchItem);
                style = matchItem.isPass() ? "pass" : "fail";
                out.println("      <tr>");
                out.println("        <td class=\"" + style + "\">" + pos + "</td>");
                out.println("        <td class=\"" + style + "\">" + (matchItem.isPass() ? "Passed" : "Fail") + "</td>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchItem.getLabel()
                    + "</a></td>");
                out.println("        <td class=\"" + style + "\">" + matchItem.getExpectStatus() + "</td>");
                out.println("        <td class=\"" + style + "\">&nbsp;</td>");
                out.println("        <td class=\"" + style + "\">" + signatureAll + "</td>");
                out.println("      </tr>");
              } else {
                out.println("      <tr>");
                out.println("        <td class=\"" + style + "\">" + pos + "</td>");
                out.println("        <td class=\"" + style + "\">not tested</td>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchItem.getLabel()
                    + "</a></td>");
                out.println("        <td class=\"" + style + "\">" + matchItem.getExpectStatus() + "</td>");
                out.println("        <td class=\"" + style + "\">&nbsp;</td>");
                out.println("        <td class=\"" + style + "\">" + signatureAll + "</td>");
                out.println("      </tr>");
              }
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
              String link = "TestSetServlet?" + PARAM_MATCH_SET_ID + "=" + matchSetSelected.getMatchSetId() + "&"
                  + PARAM_MATCH_ITEM_ID + "=" + matchItem.getMatchItemId();
              out.println("      <tr>");
              out.println("        <td>" + pos + "</td>");
              out.println("        <td><a href=\"" + link + "\">" + matchItem.getLabel() + "</a></td>");
              out.println("        <td><a href=\"" + link + "\">" + matchItem.getExpectStatus() + "</a></td>");
              out.println("      </tr>");
            }
          }
          out.println("    </table><br/>");

        }
        out.println("<h3>Load Data</h3>");
        out.println("    <form action=\"TestSetUploadServlet\" enctype=\"multipart/form-data\" method=\"POST\"> ");
        out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
            + matchSetSelected.getMatchSetId() + "\"/>");
        out.println("    <table>");
        out.println("      <tr>");
        out.println("        <td>Data Source</td>");
        out.println("        <td><input type=\"text\" size=\"20\" name=\"" + PARAM_DATA_SOURCE + "\" value=\"\"/></td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <td>Data</td>");
        out.println("        <td><input type=\"file\" name=\"" + PARAM_DATA_FILE + "\"></textarea></td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"" + PARAM_ACTION
            + "\" value=\"" + ACTION_LOAD_DATA + "\"/></td>");
        out.println("      </tr>");
        out.println("    </table>");
        out.println("    </form>");

      }

      Query query = dataSession.createQuery("from MatchSet order by updateDate");
      List<MatchSet> matchSetList = query.list();
      if (matchSetList.size() > 0) {
        out.println("<h3>All Match Sets</h3>");
        out.println("   <table border=\"1\" cellspacing=\"0\">");
        out.println("      <tr>");
        out.println("        <th>Label</th>");
        out.println("        <th>Last Updated</th>");
        out.println("        <th>Action</th>");
        out.println("      </tr>");
        for (MatchSet matchSet : matchSetList) {
          out.println("      <tr>");
          out.println("        <td>" + matchSet.getLabel() + "</td>");
          out.println("        <td>" + sdf.format(matchSet.getUpdateDate()) + "</td>");
          out.println("        <td>");
          out.println("          <form action=\"TestSetServlet\" method=\"POST\"> ");
          out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
              + matchSet.getMatchSetId() + "\"/>");
          out.println("            <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_SELECT
              + "\"/>");
          out.println("            <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_DOWNLOAD
              + "\"/>");
          out.println("          </form>");
          out.println("        </td>");
          out.println("      </tr>");
        }
        out.println("    </table><br/>");
      }

      out.println("    <form action=\"TestSetServlet\" method=\"POST\"> ");
      out.println("    <table>");
      out.println("      <tr>");
      out.println("        <td>Create New Match Set</td>");
      out.println("        <td><input type=\"text\" size=\"20\" name=\"" + PARAM_LABEL + "\" value=\"\"/></td>");
      out.println("      </tr>");
      out.println("      <tr>");
      out.println("        <td colspan=\"2\" align=\"right\"><input type=\"submit\" name=\"" + PARAM_ACTION
          + "\" value=\"" + ACTION_CREATE_NEW_MATCH_SET + "\"/></td>");
      out.println("      </tr>");
      out.println("    </table>");
      out.println("    </form>");

      HomeServlet.doFooter(out, user);

    } catch (Exception e) {
      out.println("<pre>");
      e.printStackTrace(out);
      out.println("</pre>");
    } finally {
      out.close();
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

  private void printMatchRow(PrintWriter out, MatchItem matchItemSelected, String fieldLabel, String fieldName) {
    Patient patientA = matchItemSelected.getPatientA();
    Patient patientB = matchItemSelected.getPatientB();
    String style = "";
    String valueA = patientA.getValue(fieldName);
    String valueB = patientB.getValue(fieldName);
    if (!valueA.equals("") && !valueB.equals("")) {
      boolean matches = valueA.equalsIgnoreCase(valueB);
      style = matches ? "pass" : "fail";
    }
    out.println("      <tr>");
    out.println("        <td class=\"" + style + "\">" + fieldLabel + "</td>");
    out.println("        <td class=\"" + style + "\">" + valueA + "</td>");
    out.println("        <td class=\"" + style + "\">" + valueB + "</td>");
    out.println("      </tr>");
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    // TODO Auto-generated method stub
    doGet(req, resp);
  }

}
