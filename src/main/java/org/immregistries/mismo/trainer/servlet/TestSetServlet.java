package org.immregistries.mismo.trainer.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.mismo.match.PatientCompare;
import org.immregistries.mismo.match.model.MatchItem;
import org.immregistries.mismo.match.model.Patient;
import org.immregistries.mismo.trainer.Island;
import org.immregistries.mismo.trainer.model.MatchItemReview;
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
public class TestSetServlet extends HomeServlet {
  public static final String ACTION_LOAD_DATA = "Load Data";
  public static final String ACTION_CREATE_NEW_MATCH_SET = "Create New Match Set";
  public static final String ACTION_MATCH = "Match";
  public static final String ACTION_POSSIBLE_MATCH = "Possible Match";
  public static final String ACTION_RESEARCH = "Research";
  public static final String ACTION_NOT_SURE = "Not Sure";
  public static final String ACTION_NOT_A_MATCH = "Not a Match";
  public static final String ACTION_SELECT = "Select";
  public static final String ACTION_DOWNLOAD = "Download";
  public static final String ACTION_COPY_TO_MY_ORGANIZATION = "Copy to My Organization";
  public static final String ACTION_CREATE_NEW_VERSION = "Create New Version";
  public static final String ACTION_SET_LIFECYCLE_STATUS = "Update Status";
  public static final String ACTION_ARCHIVE = "Archive";
  public static final String ACTION_RESTORE = "Restore";
  public static final String ACTION_SAVE_NOTES = "Save Notes";
  public static final String ACTION_TOGGLE_NEEDS_REVIEW = "Toggle Needs Review";

  public static final String PARAM_MATCH_ITEM_ID = "matchItemId";
  public static final String PARAM_MATCH_ITEM_ID_NEXT = "matchItemIdNext";
  public static final String PARAM_MATCH_SET_ID = "matchSetId";
  public static final String PARAM_LABEL = "label";
  public static final String PARAM_DATA_SOURCE = "dataSource";
  public static final String PARAM_DATA_FILE = "dataFile";
  public static final String PARAM_MESSAGE = "message";
  public static final String PARAM_SIGNATURE = "signature";
  public static final String PARAM_SUBLIST_NAME = "sublistName";
  public static final String PARAM_VERSION_LABEL = "versionLabel";
  public static final String PARAM_LIFECYCLE_STATUS = "lifecycleStatus";
  public static final String PARAM_REVIEW_NOTES = "reviewNotes";
  public static final String PARAM_NEEDS_REVIEW = "needsReview";
  public static final String PARAM_PROVENANCE_FILTER = "provenanceFilter";
  public static final String PARAM_SHOW_ARCHIVED = "showArchived";

  public static final String ATTRIBUTE_MATCH_SET = "matchSet";
  public static final String ATTRIBUTE_MATCH_ITEM_LIST = "matchItemList";
  public static final String ATTRIBUTE_SIGNATURE_MAP = "signatureMap";

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

      MatchSet matchSetSelected = null;
      if (req.getParameter(PARAM_MATCH_SET_ID) != null) {
        matchSetSelected = OrgScope.loadMatchSet(dataSession,
            Integer.parseInt(req.getParameter(PARAM_MATCH_SET_ID)), user);
      } else if (session.getAttribute(ATTRIBUTE_MATCH_SET) != null) {
        matchSetSelected = (MatchSet) session.getAttribute(ATTRIBUTE_MATCH_SET);
      }
      session.setAttribute(ATTRIBUTE_MATCH_SET, matchSetSelected);

      org.immregistries.mismo.trainer.model.MatchItem matchItemSelectedRow = null;
      MatchItem matchItemSelected = null;
      if (req.getParameter(PARAM_MATCH_ITEM_ID) != null) {
        matchItemSelectedRow = OrgScope.loadMatchItem(dataSession,
            Integer.parseInt(req.getParameter(PARAM_MATCH_ITEM_ID)), user);
        if (matchItemSelectedRow != null) {
          matchItemSelected = Island.toRuntimeMatchItem(matchItemSelectedRow);
        }
      }

      PatientCompare patientCompare = (PatientCompare) session
          .getAttribute(TestMatchingServlet.ATTRIBUTE_PATIENT_COMPARE);
      String action = req.getParameter(PARAM_ACTION);
      if (action != null) {
        if (action.equals(ACTION_CREATE_NEW_MATCH_SET)) {
          String label = req.getParameter(PARAM_LABEL);
          Transaction transaction = dataSession.beginTransaction();
          OrgScope.createMatchSet(dataSession, label, user);
          transaction.commit();
        } else if (matchItemSelectedRow != null && !OrgScope.canEditCases(matchItemSelectedRow.getMatchSet(), user)
            && (action.equals(ACTION_MATCH) || action.equals(ACTION_POSSIBLE_MATCH) || action.equals(ACTION_NOT_A_MATCH)
                || action.equals(ACTION_RESEARCH) || action.equals(ACTION_NOT_SURE))) {
          // matchItemSelectedRow is readable (it may be a template owned by another
          // organization, §4.1) but not editable -- classifying it would silently write into
          // another organization's data. Copy the match set to this organization first. It may
          // also be editable but currently Approved (v2-roadmap.md §10) -- move it out of that
          // status first.
          message = OrgScope.isEditable(matchItemSelectedRow, user)
              ? "Cannot classify a test case while its Test Set is Approved. Change its status first."
              : "Cannot classify a test case in a template match set you do not own. Copy it"
                  + " to your organization first.";
        } else if (matchItemSelectedRow != null && (action.equals(ACTION_MATCH) || action.equals(ACTION_POSSIBLE_MATCH)
            || action.equals(ACTION_NOT_A_MATCH) || action.equals(ACTION_RESEARCH) || action.equals(ACTION_NOT_SURE))) {
          Transaction transaction = dataSession.beginTransaction();
          String classification;
          if (action.equals(ACTION_MATCH)) {
            classification = MatchItem.MATCH;
          } else if (action.equals(ACTION_POSSIBLE_MATCH)) {
            classification = MatchItem.POSSIBLE_MATCH;
          } else if (action.equals(ACTION_NOT_A_MATCH)) {
            classification = MatchItem.NOT_A_MATCH;
          } else if (action.equals(ACTION_RESEARCH)) {
            classification = MatchItem.RESEARCH;
          } else {
            classification = MatchItem.NOT_SURE;
          }
          matchItemSelected.setExpectStatus(classification);
          // Records an immutable MatchItemReview history row and sets isReviewed = true
          // unconditionally -- even a re-classification to the same value still counts as
          // reviewed (v2-roadmap.md §10). The Review Notes box lives in this same form (not a
          // separate one) precisely so its current text is what gets snapshotted into this
          // classification event's history row -- also persisted onto the live review_notes
          // field so the box and the latest history entry never disagree.
          String notesAtClassifyTime = req.getParameter(PARAM_REVIEW_NOTES);
          OrgScope.recordReview(dataSession, matchItemSelectedRow, user, classification, notesAtClassifyTime);
          OrgScope.setReviewNotes(dataSession, matchItemSelectedRow, notesAtClassifyTime, user);
          if (patientCompare != null) {
            updatePassStatus(matchItemSelected, patientCompare);
          }
          transaction.commit();
          if (req.getParameter(PARAM_MATCH_ITEM_ID_NEXT) != null
              && !req.getParameter(PARAM_MATCH_ITEM_ID_NEXT).equals("")) {
            matchItemSelectedRow = OrgScope.loadMatchItem(dataSession,
                Integer.parseInt(req.getParameter(PARAM_MATCH_ITEM_ID_NEXT)), user);
            if (matchItemSelectedRow != null) {
              matchItemSelected = Island.toRuntimeMatchItem(matchItemSelectedRow);
            }
          }
        } else if (matchItemSelectedRow != null && action.equals(ACTION_SAVE_NOTES)) {
          Transaction transaction = dataSession.beginTransaction();
          if (!OrgScope.setReviewNotes(dataSession, matchItemSelectedRow, req.getParameter(PARAM_REVIEW_NOTES), user)) {
            message = "Cannot edit notes on a test case you do not own.";
          }
          transaction.commit();
        } else if (matchItemSelectedRow != null && action.equals(ACTION_TOGGLE_NEEDS_REVIEW)) {
          Transaction transaction = dataSession.beginTransaction();
          if (!OrgScope.setNeedsReview(dataSession, matchItemSelectedRow, !matchItemSelectedRow.isNeedsReview(),
              user)) {
            message = "Cannot edit the needs-review flag on a test case you do not own.";
          }
          transaction.commit();
        } else if (matchSetSelected != null && action.equals(ACTION_CREATE_NEW_VERSION)) {
          Transaction transaction = dataSession.beginTransaction();
          MatchSet copy = OrgScope.copyMatchSet(dataSession, matchSetSelected, user, req.getParameter(PARAM_VERSION_LABEL));
          transaction.commit();
          matchSetSelected = copy;
          session.setAttribute(ATTRIBUTE_MATCH_SET, matchSetSelected);
          message = "Created new version \"" + copy.getLabel()
              + (copy.getVersion() != null && !copy.getVersion().isEmpty() ? " (" + copy.getVersion() + ")" : "")
              + "\".";
        } else if (matchSetSelected != null && action.equals(ACTION_SET_LIFECYCLE_STATUS)) {
          Transaction transaction = dataSession.beginTransaction();
          if (!OrgScope.setLifecycleStatus(dataSession, matchSetSelected, req.getParameter(PARAM_LIFECYCLE_STATUS),
              user)) {
            message = "Cannot change the status of a Test Set you do not own.";
          }
          transaction.commit();
        } else if (matchSetSelected != null && (action.equals(ACTION_ARCHIVE) || action.equals(ACTION_RESTORE))) {
          Transaction transaction = dataSession.beginTransaction();
          if (!OrgScope.setArchived(dataSession, matchSetSelected, action.equals(ACTION_ARCHIVE), user)) {
            message = "Cannot archive/restore a Test Set you do not own.";
          }
          transaction.commit();
        } else if (action.equals(ACTION_SELECT) && matchSetSelected != null) {
          Query query = dataSession.createQuery("from MatchItem where matchSet = ? order by label");
          query.setParameter(0, matchSetSelected);
          List<org.immregistries.mismo.trainer.model.MatchItem> matchItemRowList = query.list();
          List<MatchItem> matchItemList = new ArrayList<MatchItem>();
          for (org.immregistries.mismo.trainer.model.MatchItem matchItemRow : matchItemRowList) {
            matchItemList.add(Island.toRuntimeMatchItem(matchItemRow));
          }
          session.setAttribute(ATTRIBUTE_MATCH_ITEM_LIST, matchItemList);
          session.setAttribute(ATTRIBUTE_SIGNATURE_MAP, new HashMap<String, List<MatchItem>>());
        } else if (action.equals(ACTION_DOWNLOAD) && matchSetSelected != null) {
          resp.setContentType("text/plain");
          resp.setHeader("Content-Disposition", "attachment; filename=" + matchSetSelected.getLabel() + ".txt;");
          Query query = dataSession.createQuery("from MatchItem where matchSet = ? order by label");
          query.setParameter(0, matchSetSelected);
          List<org.immregistries.mismo.trainer.model.MatchItem> matchItemList = query.list();
          for (org.immregistries.mismo.trainer.model.MatchItem matchItem : matchItemList) {
            out.println("TEST: " + matchItem.getLabel());
            out.println("EXPECT: " + matchItem.getExpectStatus());
            out.println("PATIENT A: " + matchItem.getPatientDataA());
            out.println("PATIENT B: " + matchItem.getPatientDataB());
          }
          return;
        } else if (action.equals(ACTION_COPY_TO_MY_ORGANIZATION) && matchSetSelected != null) {
          Transaction transaction = dataSession.beginTransaction();
          MatchSet copy = OrgScope.copyMatchSet(dataSession, matchSetSelected, user, null);
          transaction.commit();
          matchSetSelected = copy;
          session.setAttribute(ATTRIBUTE_MATCH_SET, matchSetSelected);
          message = "Copied \"" + copy.getLabel() + "\" to your organization.";
        }
      }

      HomeServlet.doHeader(out, req, user, message);
      out.println("    <div class=\"aira-container--wide aira-stack\">");
      out.println("    <h1 class=\"aira-page-title\">Test Set</h1>");

      List<MatchItem> matchItemList = (List<MatchItem>) session.getAttribute(ATTRIBUTE_MATCH_ITEM_LIST);

      String signatureSelected = req.getParameter(PARAM_SIGNATURE);
      if (signatureSelected != null) {
        Map<String, List<MatchItem>> signatureMap = (Map<String, List<MatchItem>>) session
            .getAttribute(TestSetServlet.ATTRIBUTE_SIGNATURE_MAP);
        matchItemList = signatureMap.get(signatureSelected);
      }
      String sublistName = req.getParameter(PARAM_SUBLIST_NAME);
      if (sublistName != null) {
        matchItemList = (List<MatchItem>) session.getAttribute(sublistName);
      }

      // The runtime org.immregistries.mismo.match.model.MatchItem class (mismo-match-1.1.jar,
      // frozen) has no provenance/review fields, so per-item provenance badges/filtering in the
      // list view below look the trainer-owned row up by id instead of carrying the data through
      // Island.toRuntimeMatchItem. Computed fresh every request -- not cached in a new session
      // attribute -- per v2-roadmap.md §10's guidance to prefer request-driven state here.
      Map<Integer, org.immregistries.mismo.trainer.model.MatchItem> matchItemRowById =
          new HashMap<Integer, org.immregistries.mismo.trainer.model.MatchItem>();
      if (matchSetSelected != null) {
        Query rowQuery = dataSession.createQuery("from MatchItem where matchSet = ?");
        rowQuery.setParameter(0, matchSetSelected);
        List<org.immregistries.mismo.trainer.model.MatchItem> rows = rowQuery.list();
        for (org.immregistries.mismo.trainer.model.MatchItem row : rows) {
          matchItemRowById.put(row.getMatchItemId(), row);
        }
      }
      String provenanceFilter = req.getParameter(PARAM_PROVENANCE_FILTER);
      if (provenanceFilter != null && !provenanceFilter.equals("") && matchItemList != null) {
        List<MatchItem> filteredByProvenance = new ArrayList<MatchItem>();
        for (MatchItem mi : matchItemList) {
          org.immregistries.mismo.trainer.model.MatchItem row = matchItemRowById.get(mi.getMatchItemId());
          if (row != null && provenanceFilter.equals(row.getProvenanceType())) {
            filteredByProvenance.add(mi);
          }
        }
        matchItemList = filteredByProvenance;
      }

      SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");

      if (matchSetSelected != null) {
        printLifecycleAndVersionPanel(out, dataSession, matchSetSelected, user);
      }

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
          if (signatureSelected != null) {
            link += "&" + PARAM_SIGNATURE + "=" + signatureSelected;
          }
          if (sublistName != null) {
            link += "&" + PARAM_SUBLIST_NAME + "=" + sublistName;
          }
          link += "&" + PARAM_MATCH_ITEM_ID + "=";
          out.println("<div class=\"navMenu\">");
          if (matchItemIdPrevious > 0) {
            out.println("<a class=\"navMenuLink\" href=\"" + link + matchItemIdPrevious + "\">Previous</a>");
          }
          if (signatureSelected == null) {
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
        Set<String> patientFieldSet = null;
        if (patientCompare != null && patientCompare.getConfiguration() != null) {
          patientFieldSet = patientCompare.getConfiguration().getPatientFieldSet();
        }
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Birth Date", Patient.BIRTH_DATE, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Name First", Patient.NAME_FIRST, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Name Middle", Patient.NAME_MIDDLE, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Name Last", Patient.NAME_LAST, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Name Suffix", Patient.NAME_SUFFIX, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Name Alias", Patient.NAME_ALIAS, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Guardian Name Last", Patient.GUARDIAN_NAME_LAST,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Guardian Name First", Patient.GUARDIAN_NAME_FIRST,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Mother Maiden Name", Patient.MOTHER_MAIDEN_NAME,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Address Street 1", Patient.ADDRESS_STREET1,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Address Street 2", Patient.ADDRESS_STREET2,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Address City", Patient.ADDRESS_CITY, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Address State", Patient.ADDRESS_STATE,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Address Zip", Patient.ADDRESS_ZIP, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "2nd Address Street 1", Patient.ADDRESS_2_STREET1,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "2nd Address Street 2", Patient.ADDRESS_2_STREET2,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "2nd Address City", Patient.ADDRESS_2_CITY,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "2nd Address State", Patient.ADDRESS_2_STATE,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "2nd Address Zip", Patient.ADDRESS_2_ZIP,
            patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Phone", Patient.PHONE, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Gender", Patient.GENDER, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "MRNs", Patient.MRNS, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Birth Type", Patient.BIRTH_TYPE, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Birth Order", Patient.BIRTH_ORDER, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Birth Status", Patient.BIRTH_STATUS, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Shot History", Patient.SHOT_HISTORY, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "SSN", Patient.SSN, patientFieldSet);
        MatchTreeRenderer.printMatchRow(out, matchItemSelected, "Medicaid #", Patient.MEDICAID, patientFieldSet);
        out.println("    </table>");
        String link = "MatchPatientServlet?" + PARAM_MATCH_ITEM_ID + "=" + matchItemSelected.getMatchItemId();
        out.println("    <p><a href=\"" + link + "\">Matching Diagnostics</a></p>");

        out.println("    <h3>Review Item</h3>");
        out.println("    <form action=\"TestSetServlet\" method=\"POST\"> ");
        out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
            + matchSetSelected.getMatchSetId() + "\"/>");
        out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_ITEM_ID + "\" value=\""
            + matchItemSelected.getMatchItemId() + "\"/>");
        if (signatureSelected != null) {
          out.println(
              "    <input type=\"hidden\" name=\"" + PARAM_SIGNATURE + "\" value=\"" + signatureSelected + "\"/>");
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
          out.println(
              "        <td class=\"" + style + "\">" + (matchItemSelected.isPass() ? "Pass" : "Fail") + "</td>");
          out.println("      </tr>");
        } else {
          out.println("      <tr>");
          out.println("        <th>Actual Result</th>");
          out.println("        <td>not tested</td>");
          out.println("      </tr>");
        }
        out.println("      <tr>");
        out.println("        <th>Last Updated By</th>");
        out.println("        <td>" + (matchItemSelected.getUser() == null ? "" : matchItemSelected.getUser().getName())
            + "</td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <th>Last Updated</th>");
        out.println("        <td>" + sdf.format(matchItemSelected.getUpdateDate()) + "</td>");
        out.println("      </tr>");
        out.println("      <tr>");
        out.println("        <th>Review Notes</th>");
        out.println("        <td><textarea name=\"" + PARAM_REVIEW_NOTES + "\" rows=\"3\" cols=\"50\">"
            + (matchItemSelectedRow.getReviewNotes() == null ? "" : escapeHtml(matchItemSelectedRow.getReviewNotes()))
            + "</textarea><br/><input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_SAVE_NOTES
            + "\"/> <span class=\"aira-muted\">(saves notes only -- classify buttons below save notes"
            + " together with the classification)</span></td>");
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

        printReviewDetailsPanel(out, dataSession, matchSetSelected, matchItemSelectedRow, matchItemSelected);

      } else if (matchSetSelected != null && matchItemList != null) {

        Map<String, List<MatchItem>> signatureMap = (Map<String, List<MatchItem>>) session
            .getAttribute(ATTRIBUTE_SIGNATURE_MAP);

        Scorer scorer;
        if (patientCompare != null && patientCompare.getConfiguration() != null) {
          scorer = new Scorer(patientCompare.getConfiguration().getScoringWeights());
        } else {
          scorer = new Scorer();
        }

        out.println("<h2>" + matchSetSelected.getLabel() + "</h2>");

        printProvenanceFilterForm(out, matchSetSelected, provenanceFilter);

        if (matchItemList.size() > 0) {

          if (patientCompare != null) {

            out.println("   <table border=\"1\" cellspacing=\"0\">");
            out.println("      <tr>");
            out.println("        <th>#</th>");
            out.println("        <th>Status</th>");
            out.println("        <th>Test Case</th>");
            out.println("        <th>Expected</th>");
            out.println("        <th>Actual</th>");
            out.println("        <th>Signature</th>");
            out.println("        <th>Provenance</th>");
            out.println("      </tr>");
            int pos = 0;
            for (MatchItem matchItem : matchItemList) {
              pos++;
              String link = "TestSetServlet?" + PARAM_MATCH_SET_ID + "=" + matchSetSelected.getMatchSetId() + "&"
                  + PARAM_MATCH_ITEM_ID + "=" + matchItem.getMatchItemId();
              String style = "";
              patientCompare.setMatchItem(matchItem);
              String signature = patientCompare.getSignature();
              if (matchItem.isExpectedStatusSet() && !matchItem.isTested()) {
                updatePassStatus(matchItem, patientCompare);
                List<MatchItem> signatureList = signatureMap.get(signature);
                if (signatureList == null) {
                  signatureList = new ArrayList<MatchItem>();
                  signatureMap.put(signature, signatureList);
                }
                signatureList.add(matchItem);
              }
              String provenance = provenanceOf(matchItemRowById, matchItem.getMatchItemId());
              if (matchItem.isTested()) {
                scorer.registerMatch(matchItem);
                style = matchItem.isPass() ? "pass" : "fail";
                out.println("      <tr>");
                out.println("        <td class=\"" + style + "\">" + pos + "</td>");
                out.println(
                    "        <td class=\"" + style + "\">" + (matchItem.isPass() ? "Passed" : "Fail") + "</td>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchItem.getLabel()
                    + "</a></td>");
                out.println("        <td class=\"" + style + "\">" + matchItem.getExpectStatus() + "</td>");
                out.println("        <td class=\"" + style + "\">" + matchItem.getActualStatus() + "</td>");
                out.println("        <td class=\"" + style + "\">" + signature + "</td>");
                out.println("        <td class=\"" + style + "\">" + provenance + "</td>");
                out.println("      </tr>");
              } else {
                out.println("      <tr>");
                out.println("        <td class=\"" + style + "\">" + pos + "</td>");
                out.println("        <td class=\"" + style + "\">not tested</td>");
                out.println("        <td class=\"" + style + "\"><a href=\"" + link + "\">" + matchItem.getLabel()
                    + "</a></td>");
                out.println("        <td class=\"" + style + "\">" + matchItem.getExpectStatus() + "</td>");
                out.println("        <td class=\"" + style + "\">&nbsp;</td>");
                out.println("        <td class=\"" + style + "\">" + signature + "</td>");
                out.println("        <td class=\"" + style + "\">" + provenance + "</td>");
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
            out.println("        <th>Provenance</th>");
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
              out.println("        <td>" + provenanceOf(matchItemRowById, matchItem.getMatchItemId()) + "</td>");
              out.println("      </tr>");
            }
          }
          out.println("    </table><br/>");

        }
        if (OrgScope.canEditCases(matchSetSelected, user)) {
          out.println("<h3>Load Data</h3>");
          out.println("    <form action=\"TestSetUploadServlet\" enctype=\"multipart/form-data\" method=\"POST\"> ");
          out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
              + matchSetSelected.getMatchSetId() + "\"/>");
          out.println("    <table>");
          out.println("      <tr>");
          out.println("        <td>Data Source</td>");
          out.println(
              "        <td><input type=\"text\" size=\"20\" name=\"" + PARAM_DATA_SOURCE + "\" value=\"\"/></td>");
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
        } else if (OrgScope.isEditable(matchSetSelected, user)) {
          out.println("<p><em>This Test Set is Approved -- change its status above to load more data.</em></p>");
        }

      }

      boolean showArchived = "true".equals(req.getParameter(PARAM_SHOW_ARCHIVED));
      List<MatchSet> matchSetList = OrgScope.listMatchSets(dataSession, user, showArchived);
      {
        out.println("<h3>All Match Sets</h3>");
        out.println("    <form action=\"TestSetServlet\" method=\"GET\">");
        out.println("      <label><input type=\"checkbox\" name=\"" + PARAM_SHOW_ARCHIVED + "\" value=\"true\""
            + (showArchived ? " checked=\"true\"" : "") + " onchange=\"this.form.submit()\"/> Show archived</label>");
        out.println("    </form>");
      }
      if (matchSetList.size() > 0) {
        out.println("   <table border=\"1\" cellspacing=\"0\">");
        out.println("      <tr>");
        out.println("        <th>Label</th>");
        out.println("        <th>Owner</th>");
        out.println("        <th>Status</th>");
        out.println("        <th>Version</th>");
        out.println("        <th>Last Updated</th>");
        out.println("        <th>Action</th>");
        out.println("      </tr>");
        for (MatchSet matchSet : matchSetList) {
          boolean editable = OrgScope.isEditable(matchSet, user);
          out.println("      <tr>");
          out.println("        <td>" + escapeHtml(matchSet.getLabel())
              + (matchSet.getArchivedAt() != null ? " (archived)" : "") + "</td>");
          out.println("        <td>" + (editable ? "You"
              : escapeHtml(matchSet.getOrganization().getName()) + " (template)") + "</td>");
          out.println("        <td>" + matchSet.getLifecycleStatus() + "</td>");
          out.println(
              "        <td>" + (matchSet.getVersion() == null ? "" : escapeHtml(matchSet.getVersion())) + "</td>");
          out.println("        <td>" + sdf.format(matchSet.getUpdatedAt()) + "</td>");
          out.println("        <td>");
          out.println("          <form action=\"TestSetServlet\" method=\"POST\"> ");
          out.println("    <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
              + matchSet.getMatchSetId() + "\"/>");
          out.println("            <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_SELECT
              + "\"/>");
          out.println("            <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_DOWNLOAD
              + "\"/>");
          if (!editable) {
            out.println("            <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\""
                + ACTION_COPY_TO_MY_ORGANIZATION + "\"/>");
          } else {
            out.println("            <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\""
                + (matchSet.getArchivedAt() != null ? ACTION_RESTORE : ACTION_ARCHIVE) + "\"/>");
          }
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

      out.println("    </div>");

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

  /** Looks up a runtime match item's persisted provenance, or "" if the row can't be found. */
  private static String provenanceOf(Map<Integer, org.immregistries.mismo.trainer.model.MatchItem> matchItemRowById,
      int matchItemId) {
    org.immregistries.mismo.trainer.model.MatchItem row = matchItemRowById.get(matchItemId);
    return row == null ? "" : row.getProvenanceType();
  }

  /** Renders the provenance filter dropdown shown above a Test Set's case list. */
  private void printProvenanceFilterForm(PrintWriter out, MatchSet matchSetSelected, String provenanceFilter) {
    String[] provenanceValues = {org.immregistries.mismo.trainer.model.MatchItem.PROVENANCE_MANUAL,
        org.immregistries.mismo.trainer.model.MatchItem.PROVENANCE_IMPORTED,
        org.immregistries.mismo.trainer.model.MatchItem.PROVENANCE_GENERATED,
        org.immregistries.mismo.trainer.model.MatchItem.PROVENANCE_SIGNATURE_GENERATED,
        org.immregistries.mismo.trainer.model.MatchItem.PROVENANCE_COPIED};
    out.println("    <form action=\"TestSetServlet\" method=\"GET\">");
    out.println("      <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
        + matchSetSelected.getMatchSetId() + "\"/>");
    out.println("      <label>Filter by Provenance: <select name=\"" + PARAM_PROVENANCE_FILTER
        + "\" onchange=\"this.form.submit()\">");
    out.println("        <option value=\"\"" + (provenanceFilter == null || provenanceFilter.equals("")
        ? " selected=\"true\"" : "") + ">All</option>");
    for (String provenance : provenanceValues) {
      out.println("        <option value=\"" + provenance + "\"" + (provenance.equals(provenanceFilter)
          ? " selected=\"true\"" : "") + ">" + provenance + "</option>");
    }
    out.println("      </select></label>");
    out.println("    </form>");
  }

  /**
   * Renders the lifecycle badge, status transition control, archive/restore control, "Create
   * New Version" prompt, and version-family list for the currently selected Test Set
   * (v2-roadmap.md §10).
   */
  private void printLifecycleAndVersionPanel(PrintWriter out, Session dataSession, MatchSet matchSetSelected,
      User user) {
    boolean editable = OrgScope.isEditable(matchSetSelected, user);
    out.println("    <section class=\"aira-panel\">");
    out.println("      <p>");
    out.println("        <strong>Status:</strong> " + matchSetSelected.getLifecycleStatus());
    if (matchSetSelected.getVersion() != null && !matchSetSelected.getVersion().equals("")) {
      out.println("        &middot; <strong>Version:</strong> " + escapeHtml(matchSetSelected.getVersion()));
    }
    if (matchSetSelected.getArchivedAt() != null) {
      out.println("        &middot; <em>Archived</em>");
    }
    out.println("      </p>");
    if (editable) {
      out.println("      <form action=\"TestSetServlet\" method=\"POST\">");
      out.println("        <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
          + matchSetSelected.getMatchSetId() + "\"/>");
      out.println("        <select name=\"" + PARAM_LIFECYCLE_STATUS + "\">");
      for (String status : new String[] {MatchSet.LIFECYCLE_DRAFT, MatchSet.LIFECYCLE_REVIEWED,
          MatchSet.LIFECYCLE_APPROVED}) {
        out.println("          <option value=\"" + status + "\""
            + (status.equals(matchSetSelected.getLifecycleStatus()) ? " selected=\"true\"" : "") + ">" + status
            + "</option>");
      }
      out.println("        </select>");
      out.println(
          "        <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_SET_LIFECYCLE_STATUS
              + "\"/>");
      out.println(
          "        <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\""
              + (matchSetSelected.getArchivedAt() != null ? ACTION_RESTORE : ACTION_ARCHIVE) + "\"/>");
      out.println("      </form>");
      out.println("      <form action=\"TestSetServlet\" method=\"POST\">");
      out.println("        <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
          + matchSetSelected.getMatchSetId() + "\"/>");
      out.println("        <label>New version label: <input type=\"text\" size=\"12\" placeholder=\"e.g. v1.2\""
          + " name=\"" + PARAM_VERSION_LABEL + "\" value=\"\"/></label>");
      out.println(
          "        <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_CREATE_NEW_VERSION
              + "\"/>");
      out.println("      </form>");
    }
    List<MatchSet> versionFamily = OrgScope.listVersionFamily(dataSession, matchSetSelected.getRootMatchSetId(), user);
    if (versionFamily.size() > 1) {
      out.println("      <h3>Versions of this Test Set</h3>");
      out.println("      <ul>");
      for (MatchSet familyMember : versionFamily) {
        String familyLink = "TestSetServlet?" + PARAM_MATCH_SET_ID + "=" + familyMember.getMatchSetId();
        boolean current = familyMember.getMatchSetId() == matchSetSelected.getMatchSetId();
        out.println("        <li>" + (current ? "<strong>" : "") + "<a href=\"" + familyLink + "\">"
            + escapeHtml(familyMember.getLabel())
            + (familyMember.getVersion() != null && !familyMember.getVersion().equals("")
                ? " (" + escapeHtml(familyMember.getVersion()) + ")" : "")
            + "</a> -- " + familyMember.getLifecycleStatus() + (current ? "</strong>" : "") + "</li>");
      }
      out.println("      </ul>");
    }
    out.println("    </section>");
  }

  /**
   * Renders the Test Case review page's provenance/original-expectation summary, the
   * independently-editable notes and needs-review controls, and the full review-history panel
   * (v2-roadmap.md §10).
   */
  private void printReviewDetailsPanel(PrintWriter out, Session dataSession, MatchSet matchSetSelected,
      org.immregistries.mismo.trainer.model.MatchItem matchItemSelectedRow, MatchItem matchItemSelected) {
    out.println("    <h3>Review Details</h3>");
    out.println("    <table border=\"1\" cellspacing=\"0\">");
    out.println("      <tr><th>Provenance</th><td>" + matchItemSelectedRow.getProvenanceType() + "</td></tr>");
    out.println("      <tr><th>Original Expected</th><td>"
        + (matchItemSelectedRow.getOriginalExpectStatus() == null ? ""
            : matchItemSelectedRow.getOriginalExpectStatus())
        + "</td></tr>");
    out.println(
        "      <tr><th>Reviewed</th><td>" + (matchItemSelectedRow.isReviewed() ? "Yes" : "No") + "</td></tr>");
    if (matchItemSelectedRow.getCopiedFromMatchItemId() != null) {
      out.println("      <tr><th>Copied From</th><td>match item #" + matchItemSelectedRow.getCopiedFromMatchItemId()
          + "</td></tr>");
    }
    out.println("    </table>");

    out.println("    <form action=\"TestSetServlet\" method=\"POST\">");
    out.println("      <input type=\"hidden\" name=\"" + PARAM_MATCH_SET_ID + "\" value=\""
        + matchSetSelected.getMatchSetId() + "\"/>");
    out.println("      <input type=\"hidden\" name=\"" + PARAM_MATCH_ITEM_ID + "\" value=\""
        + matchItemSelected.getMatchItemId() + "\"/>");
    out.println("      <input type=\"submit\" name=\"" + PARAM_ACTION + "\" value=\"" + ACTION_TOGGLE_NEEDS_REVIEW
        + "\"/>");
    out.println("      <span>Needs further review: " + (matchItemSelectedRow.isNeedsReview() ? "Yes" : "No")
        + "</span>");
    out.println("    </form>");

    List<MatchItemReview> reviewHistory = OrgScope.listMatchItemReviews(dataSession, matchItemSelectedRow);
    out.println("    <h3>Review History</h3>");
    if (reviewHistory.isEmpty()) {
      out.println("    <p>No review history yet.</p>");
    } else {
      out.println("    <table border=\"1\" cellspacing=\"0\">");
      out.println("      <tr><th>Reviewer</th><th>Classification</th><th>Notes</th><th>Reviewed At</th></tr>");
      SimpleDateFormat reviewSdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
      for (MatchItemReview review : reviewHistory) {
        String reviewerLabel = "";
        if (review.getReviewerUser() != null) {
          reviewerLabel = review.getReviewerUser().getDisplayName() != null ? review.getReviewerUser().getDisplayName()
              : review.getReviewerUser().getEmail();
        }
        out.println("      <tr>");
        out.println("        <td>" + escapeHtml(reviewerLabel) + "</td>");
        out.println("        <td>" + escapeHtml(review.getClassification()) + "</td>");
        out.println("        <td>" + (review.getNotes() == null ? "" : escapeHtml(review.getNotes())) + "</td>");
        out.println("        <td>" + reviewSdf.format(review.getReviewedAt()) + "</td>");
        out.println("      </tr>");
      }
      out.println("    </table>");
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
